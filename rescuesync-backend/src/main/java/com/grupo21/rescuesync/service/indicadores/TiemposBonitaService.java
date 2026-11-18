package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.client.BonitaActividadArchivada;
import com.grupo21.rescuesync.client.BonitaCasoInfo;
import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.client.FechasBonita;
import com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef;
import com.grupo21.rescuesync.dto.indicadores.EstadoFuenteBonita;
import com.grupo21.rescuesync.dto.indicadores.TiempoEtapaResponse;
import com.grupo21.rescuesync.dto.indicadores.TiemposEtapasResponse;
import com.grupo21.rescuesync.model.EtapaEmergencia;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiempos por etapa de la emergencia, calculados con la API REST de Bonita (contrato, sección 3.4).
 *
 * <p>Contrato: nunca lanza excepción por Bonita caído o con error; en ese caso devuelve
 * {@code bonita.disponible = false} y las etapas con 0 muestras (ver {@link #sinDatos}).
 *
 * <h2>Cálculo</h2>
 * Por cada caso:
 * <ol>
 *   <li>{@code archivedCase?f=sourceObjectId=} → si existe, el caso terminó: cuenta en
 *       {@code casosFinalizados} y aporta {@code end_date - start} al promedio total (si las dos fechas
 *       son válidas; si no, cuenta como finalizado pero no aporta al promedio).</li>
 *   <li>Si no está archivado, {@code case/{id}} confirma que existe y está en curso.</li>
 *   <li>{@code archivedActivity?f=caseId=} (todas las páginas) → duración de cada tarea completada.</li>
 * </ol>
 *
 * <p><b>Duración de una tarea.</b> Bonita archiva un registro por cada cambio de estado de una
 * actividad, todos con el mismo {@code sourceObjectId}. Se agrupan por {@code sourceObjectId} y:
 * <ul>
 *   <li>solo cuentan las actividades con al menos un registro en estado {@code completed}
 *       (las abortadas, canceladas, salteadas o todavía activas se ignoran);</li>
 *   <li><b>inicio</b> = el mínimo {@code reached_state_date} entre los registros de la actividad que
 *       <i>no</i> son {@code completed} (el primer estado archivado, normalmente
 *       {@code initializing}/{@code ready}: el momento en que la tarea quedó disponible); si un registro
 *       no tiene {@code reached_state_date} válido se usa su {@code archivedDate};</li>
 *   <li><b>fin</b> = el máximo {@code reached_state_date} de los registros {@code completed}
 *       (con la misma alternativa de {@code archivedDate});</li>
 *   <li>si falta alguna de las dos fechas, o fin &lt; inicio, la tarea se descarta. En particular, una
 *       actividad con un único registro {@code completed} se descarta: no hay forma de saber cuándo
 *       empezó, y contarla como 0 horas falsearía los promedios.</li>
 * </ul>
 * La duración de una etapa en un caso es la suma de sus tareas (un mismo nombre puede ejecutarse varias
 * veces, por ejemplo en un ciclo). Las tareas que no están en ninguna {@link EtapaEmergencia} se ignoran.
 *
 * <p><b>No verificado contra un Runtime 10.4 real</b> (solo contra la referencia de la REST API):
 * que {@code archivedActivity} devuelva varios registros por actividad (si devolviera solo el
 * {@code completed}, todas las tareas se descartarían y las etapas quedarían con 0 muestras; en ese caso
 * habría que pasar a {@code archivedFlowNode}); que el filtro {@code sourceObjectId} de {@code archivedCase} y el
 * {@code caseId} de {@code archivedActivity} funcionen así; los nombres exactos de los estados en
 * minúscula; y la zona horaria de las fechas (ver {@link FechasBonita}).
 *
 * <h2>Errores</h2>
 * <ul>
 *   <li>Un caso que Bonita no conoce (404 en {@code case/{id}} y sin archivo) se descarta y se sigue
 *       con el resto. Sigue contando en {@code casosAnalizados} porque se consultó.</li>
 *   <li>Cualquier otro error (conexión, timeout, 5xx, login) corta el cálculo entero y se devuelve
 *       {@link #sinDatos} con {@code disponible = false}: un dato parcial podría confundir más que
 *       ayudar, y si Bonita está caído no tiene sentido seguir intentando caso por caso.</li>
 * </ul>
 *
 * <h2>Caché</h2>
 * Por caso, en memoria. Los casos en curso (o no encontrados) duran {@code bonita.indicadores.cache-ttl}
 * (default 5 minutos); los archivados, que ya no cambian, {@code bonita.indicadores.cache-ttl-archivados}
 * (default 12 horas). Los errores no se cachean. {@code consultadoEn} es el momento del dato más viejo
 * usado en la respuesta.
 */
@Slf4j
@Service
public class TiemposBonitaService {

    static final String MENSAJE_NO_DISPONIBLE =
            "No se pudo consultar Bonita. Los tiempos por etapa no están disponibles por el momento.";

    private static final String ESTADO_COMPLETADO = "completed";
    private static final double MILIS_POR_HORA = 3_600_000d;

    private final BonitaClient bonitaClient;
    private final Duration ttlEnCurso;
    private final Duration ttlArchivados;
    private final Clock clock;

    private final Map<Long, EntradaCache> cache = new ConcurrentHashMap<>();

    @Autowired
    public TiemposBonitaService(BonitaClient bonitaClient,
                                @Value("${bonita.indicadores.cache-ttl:5m}") Duration ttlEnCurso,
                                @Value("${bonita.indicadores.cache-ttl-archivados:12h}") Duration ttlArchivados) {
        this(bonitaClient, ttlEnCurso, ttlArchivados, Clock.systemUTC());
    }

    /** Constructor con reloj explícito, para tests. */
    public TiemposBonitaService(BonitaClient bonitaClient, Duration ttlEnCurso, Duration ttlArchivados, Clock clock) {
        this.bonitaClient = bonitaClient;
        this.ttlEnCurso = ttlEnCurso;
        this.ttlArchivados = ttlArchivados;
        this.clock = clock;
    }

    public TiemposEtapasResponse tiemposEtapas(List<CasoBonitaRef> casos) {
        if (casos.isEmpty()) {
            return armarRespuesta(List.of(), 0, clock.instant());
        }
        Instant ahora = clock.instant();
        purgarVencidas(ahora);

        List<DatosCaso> datos = new ArrayList<>();
        Instant masViejo = ahora;
        try {
            for (CasoBonitaRef caso : casos) {
                if (caso.bonitaCaseId() == null) {
                    continue;
                }
                EntradaCache entrada = obtener(caso.bonitaCaseId(), ahora);
                if (entrada.obtenidoEn().isBefore(masViejo)) {
                    masViejo = entrada.obtenidoEn();
                }
                if (entrada.datos().encontrado()) {
                    datos.add(entrada.datos());
                }
            }
        } catch (RuntimeException ex) {
            // BonitaIntegrationException, RestClientException (incluye timeouts) o cualquier otra falla:
            // el dashboard sigue funcionando sin los datos de Bonita.
            log.warn("No se pudieron calcular los tiempos por etapa con Bonita: {}", ex.getMessage(), ex);
            return sinDatos(EstadoFuenteBonita.noDisponible(MENSAJE_NO_DISPONIBLE), casos.size());
        }
        return armarRespuesta(datos, casos.size(), masViejo);
    }

    /** Respuesta con todas las etapas en 0 muestras. */
    public static TiemposEtapasResponse sinDatos(EstadoFuenteBonita estado, long casosAnalizados) {
        List<TiempoEtapaResponse> etapas = Arrays.stream(EtapaEmergencia.values())
                .map(etapa -> new TiempoEtapaResponse(etapa, etapa.getNombre(), etapa.getTareas(),
                        0, null, null, null, null))
                .toList();
        return new TiemposEtapasResponse(estado, casosAnalizados, 0, null, etapas);
    }

    // ---------------------------------------------------------------------------------------------
    // Caché
    // ---------------------------------------------------------------------------------------------

    private EntradaCache obtener(Long caseId, Instant ahora) {
        EntradaCache actual = cache.get(caseId);
        if (actual != null && actual.venceEn().isAfter(ahora)) {
            return actual;
        }
        DatosCaso datos = consultarCaso(String.valueOf(caseId));
        Duration ttl = datos.finalizado() ? ttlArchivados : ttlEnCurso;
        EntradaCache nueva = new EntradaCache(datos, ahora, ahora.plus(ttl));
        cache.put(caseId, nueva);
        return nueva;
    }

    private void purgarVencidas(Instant ahora) {
        cache.values().removeIf(entrada -> !entrada.venceEn().isAfter(ahora));
    }

    /** Vacía la caché (para tests o para forzar una nueva consulta). */
    void limpiarCache() {
        cache.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Consulta y cálculo por caso
    // ---------------------------------------------------------------------------------------------

    private DatosCaso consultarCaso(String caseId) {
        Optional<BonitaCasoInfo> archivado = bonitaClient.buscarCasoArchivado(caseId);
        if (archivado.isEmpty() && bonitaClient.buscarCaso(caseId).isEmpty()) {
            log.warn("El caso {} de Bonita no existe (ni activo ni archivado); se descarta de los tiempos", caseId);
            return DatosCaso.NO_ENCONTRADO;
        }
        Map<EtapaEmergencia, Double> horas = horasPorEtapa(bonitaClient.listarActividadesArchivadas(caseId));
        Double duracionTotal = archivado.flatMap(caso -> horasEntre(
                FechasBonita.parsear(caso.start()), FechasBonita.parsear(caso.end_date()))).orElse(null);
        return new DatosCaso(true, archivado.isPresent(), duracionTotal, horas);
    }

    /**
     * Horas por etapa en un caso, a partir de sus actividades archivadas (ver la regla en el Javadoc de la
     * clase). Solo incluye etapas con al menos una tarea completada con fechas válidas.
     */
    static Map<EtapaEmergencia, Double> horasPorEtapa(List<BonitaActividadArchivada> registros) {
        Map<String, List<BonitaActividadArchivada>> porActividad = new LinkedHashMap<>();
        for (BonitaActividadArchivada registro : registros == null ? List.<BonitaActividadArchivada>of() : registros) {
            String clave = registro.sourceObjectId() != null ? registro.sourceObjectId() : "id:" + registro.id();
            porActividad.computeIfAbsent(clave, k -> new ArrayList<>()).add(registro);
        }

        Map<EtapaEmergencia, Double> horas = new EnumMap<>(EtapaEmergencia.class);
        for (List<BonitaActividadArchivada> actividad : porActividad.values()) {
            Optional<EtapaEmergencia> etapa = actividad.stream()
                    .map(BonitaActividadArchivada::name)
                    .filter(nombre -> nombre != null)
                    .findFirst()
                    .flatMap(EtapaEmergencia::deTarea);
            if (etapa.isEmpty()) {
                continue;
            }
            Optional<Instant> inicio = actividad.stream()
                    .filter(r -> !ESTADO_COMPLETADO.equalsIgnoreCase(r.state()))
                    .map(TiemposBonitaService::fechaDeRegistro)
                    .flatMap(Optional::stream)
                    .min(Instant::compareTo);
            Optional<Instant> fin = actividad.stream()
                    .filter(r -> ESTADO_COMPLETADO.equalsIgnoreCase(r.state()))
                    .map(TiemposBonitaService::fechaDeRegistro)
                    .flatMap(Optional::stream)
                    .max(Instant::compareTo);
            horasEntre(inicio, fin).ifPresent(h -> horas.merge(etapa.get(), h, Double::sum));
        }
        return horas;
    }

    private static Optional<Instant> fechaDeRegistro(BonitaActividadArchivada registro) {
        return FechasBonita.parsear(registro.reached_state_date())
                .or(() -> FechasBonita.parsear(registro.archivedDate()));
    }

    private static Optional<Double> horasEntre(Optional<Instant> inicio, Optional<Instant> fin) {
        if (inicio.isEmpty() || fin.isEmpty() || fin.get().isBefore(inicio.get())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(inicio.get(), fin.get()).toMillis() / MILIS_POR_HORA);
    }

    // ---------------------------------------------------------------------------------------------
    // Agregación
    // ---------------------------------------------------------------------------------------------

    private static TiemposEtapasResponse armarRespuesta(List<DatosCaso> datos, long casosAnalizados,
                                                        Instant consultadoEn) {
        List<TiempoEtapaResponse> etapas = new ArrayList<>();
        for (EtapaEmergencia etapa : EtapaEmergencia.values()) {
            List<Double> muestras = datos.stream()
                    .map(caso -> caso.horasPorEtapa().get(etapa))
                    .filter(h -> h != null)
                    .sorted()
                    .toList();
            etapas.add(new TiempoEtapaResponse(etapa, etapa.getNombre(), etapa.getTareas(), muestras.size(),
                    redondear(promedio(muestras)), redondear(mediana(muestras)),
                    muestras.isEmpty() ? null : redondear(muestras.get(0)),
                    muestras.isEmpty() ? null : redondear(muestras.get(muestras.size() - 1))));
        }
        long finalizados = datos.stream().filter(DatosCaso::finalizado).count();
        List<Double> duracionesTotales = datos.stream()
                .filter(DatosCaso::finalizado)
                .map(DatosCaso::duracionTotalHoras)
                .filter(h -> h != null)
                .toList();
        return new TiemposEtapasResponse(EstadoFuenteBonita.ok(consultadoEn), casosAnalizados, finalizados,
                redondear(promedio(duracionesTotales)), etapas);
    }

    private static Double promedio(List<Double> valores) {
        return valores.isEmpty() ? null
                : valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    /** Mediana de una lista ya ordenada; con cantidad par, promedio de los dos centrales. */
    private static Double mediana(List<Double> ordenados) {
        int n = ordenados.size();
        if (n == 0) {
            return null;
        }
        return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
    }

    /** Redondeo a 1 decimal, mitad hacia arriba (sección 2 del contrato). */
    private static Double redondear(Double valor) {
        return valor == null ? null : BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Resultado ya calculado de un caso (es lo que se cachea).
     *
     * @param encontrado         false si Bonita no conoce el caso
     * @param finalizado         true si el caso está archivado
     * @param duracionTotalHoras end_date - start del caso archivado; null si no terminó o faltan fechas
     * @param horasPorEtapa      solo etapas con al menos una tarea completada
     */
    record DatosCaso(boolean encontrado, boolean finalizado, Double duracionTotalHoras,
                     Map<EtapaEmergencia, Double> horasPorEtapa) {

        static final DatosCaso NO_ENCONTRADO = new DatosCaso(false, false, null, Collections.emptyMap());
    }

    private record EntradaCache(DatosCaso datos, Instant obtenidoEn, Instant venceEn) {
    }
}

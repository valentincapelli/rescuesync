package com.grupo21.rescuesync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.model.ClasificacionOferta;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.NivelCompetencia;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.model.ValidacionOferta;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Trae a RescueSync el resultado de la tarea "Evaluar ofertas" de Bonita (E3-10) y adapta cada oferta
 * a un lote principal o de apoyo (eslabón 4 del enunciado).
 *
 * <p>El conector de Bonita consulta al Sistema Nacional y deja en la variable {@code ofertasEvaluadasJson}
 * los lotes con sus ofertas, cada una con un bloque {@code validacion} (respuesta de
 * {@code POST /api/certificaciones/validar}). Este servicio lee esa variable, guarda el perfil en la
 * oferta, la clasifica y la pasa de PENDIENTE a EVALUADA con {@link HistorialEstadoService}.
 *
 * <p>Se ejecuta al abrir la pantalla de selección del municipio. Mientras Bonita no haya evaluado
 * (variable vacía o caso sin llegar a esa tarea) las ofertas siguen PENDIENTE. Es idempotente: solo
 * toca ofertas PENDIENTE, bloqueadas con {@code FOR UPDATE}.
 */
@Slf4j
@Service
public class ValidacionOfertasService {

    static final String VARIABLE = "ofertasEvaluadasJson";
    private static final int MAX_OBSERVACIONES = 500;
    private static final List<EstadoEmergencia> ESTADOS_EVALUABLES =
            List.of(EstadoEmergencia.COBERTURA_EVALUADA, EstadoEmergencia.COBERTURA_PARCIAL);

    private final BonitaClient bonitaClient;
    private final EmergenciaRepository emergenciaRepository;
    private final OfertaRepository ofertaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialEstadoService historialEstadoService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaccion;

    public ValidacionOfertasService(BonitaClient bonitaClient,
                                    EmergenciaRepository emergenciaRepository,
                                    OfertaRepository ofertaRepository,
                                    UsuarioRepository usuarioRepository,
                                    HistorialEstadoService historialEstadoService,
                                    ObjectMapper objectMapper,
                                    PlatformTransactionManager transactionManager) {
        this.bonitaClient = bonitaClient;
        this.emergenciaRepository = emergenciaRepository;
        this.ofertaRepository = ofertaRepository;
        this.usuarioRepository = usuarioRepository;
        this.historialEstadoService = historialEstadoService;
        this.objectMapper = objectMapper;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /** Sincroniza el municipio del usuario. Si no tiene municipio no hace nada: el listado informa el error. */
    public void sincronizarMunicipioDelUsuario(String email) {
        usuarioRepository.findByEmail(email)
                .filter(usuario -> usuario.getMunicipio() != null)
                .ifPresent(usuario -> sincronizarMunicipio(usuario.getMunicipio().getId()));
    }

    /**
     * Sincroniza las emergencias del municipio que tienen ofertas sin evaluar. Un fallo de Bonita en una
     * emergencia no impide mostrar las demás: se registra y esas ofertas quedan PENDIENTE.
     */
    public void sincronizarMunicipio(Long municipioId) {
        List<Long> emergencias = emergenciaRepository.buscarIdsConOfertasEnEstado(
                municipioId, ESTADOS_EVALUABLES, EstadoOferta.PENDIENTE);
        for (Long emergenciaId : emergencias) {
            try {
                int evaluadas = sincronizarEmergencia(emergenciaId);
                if (evaluadas > 0) {
                    log.info("Emergencia {}: {} ofertas evaluadas desde Bonita", emergenciaId, evaluadas);
                }
            } catch (RuntimeException ex) {
                log.warn("Emergencia {}: no se pudo leer la evaluación de ofertas de Bonita: {}",
                        emergenciaId, ex.getMessage());
            }
        }
    }

    /** Evalúa las ofertas PENDIENTE de una emergencia con el resultado de Bonita. Devuelve cuántas evaluó. */
    public int sincronizarEmergencia(Long emergenciaId) {
        Long caseId = emergenciaRepository.findById(emergenciaId)
                .filter(ValidacionOfertasService::evaluable)
                .map(Emergencia::getBonitaCaseId)
                .orElse(null);
        if (caseId == null) {
            return 0;
        }
        // Se lee Bonita antes de bloquear: la llamada HTTP no retiene filas de ofertas.
        Optional<JsonNode> lotes = leerEvaluacion(caseId);
        if (lotes.isEmpty()) {
            return 0;
        }
        Integer evaluadas = transaccion.execute(status -> {
            Emergencia emergencia = emergenciaRepository.findById(emergenciaId)
                    .filter(ValidacionOfertasService::evaluable)
                    .orElse(null);
            if (emergencia == null) {
                return 0;
            }
            // Solo las que siguen PENDIENTE: dos pedidos simultáneos no evalúan dos veces la misma oferta.
            List<Oferta> pendientes = ofertaRepository.buscarConBloqueoPorEmergenciaYEstado(
                    emergenciaId, EstadoOferta.PENDIENTE);
            return pendientes.isEmpty() ? 0 : aplicar(emergencia, pendientes, lotes.get());
        });
        return evaluadas == null ? 0 : evaluadas;
    }

    private static boolean evaluable(Emergencia emergencia) {
        return emergencia.getBonitaCaseId() != null && ESTADOS_EVALUABLES.contains(emergencia.getEstado());
    }

    private Optional<JsonNode> leerEvaluacion(Long caseId) {
        Optional<String> valor = bonitaClient.leerVariable(caseId.toString(), VARIABLE, false);
        if (valor.isEmpty() || valor.get().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode lotes = objectMapper.readTree(valor.get());
            // La variable arranca en "[]": todavía no pasó por "Evaluar ofertas".
            return lotes != null && lotes.isArray() && !lotes.isEmpty() ? Optional.of(lotes) : Optional.empty();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("El caso " + caseId + " tiene un " + VARIABLE + " inválido", ex);
        }
    }

    private int aplicar(Emergencia emergencia, List<Oferta> pendientes, JsonNode lotes) {
        Map<Long, Oferta> porId = pendientes.stream().collect(Collectors.toMap(Oferta::getId, Function.identity()));
        Instant ahora = Instant.now();
        int evaluadas = 0;
        for (JsonNode lote : lotes) {
            for (JsonNode nodo : lote.path("ofertas")) {
                Oferta oferta = porId.get(nodo.path("id").asLong());
                JsonNode perfil = nodo.path("validacion");
                if (oferta == null || !perfil.isObject()) {
                    continue;
                }
                if (oferta.getLote().getId() != lote.path("id").asLong()
                        || !oferta.getLote().getTipoRecurso().name().equals(perfil.path("tipoRecurso").asText())) {
                    log.warn("Emergencia {}: la evaluación de la oferta {} no corresponde a su lote; se ignora",
                            emergencia.getId(), oferta.getId());
                    continue;
                }
                oferta.setValidacion(clasificar(emergencia.getNivelGravedad(), oferta.getLote().getTipoRecurso(),
                        perfil, ahora, oferta.getLote().getUnidadMedida()));
                historialEstadoService.cambiarEstado(oferta, EstadoOferta.EVALUADA);
                evaluadas++;
            }
        }
        return evaluadas;
    }

    /**
     * Regla documentada por el Sistema Nacional: quien llama compara el riesgo máximo certificado con la
     * gravedad de la emergencia. Si hay varias capacidades para el tipo de recurso se usa la de mayor
     * riesgo y, a igual riesgo, la de mayor competencia.
     */
    public static ValidacionOferta clasificar(NivelGravedad gravedad, TipoRecurso tipoRecurso, JsonNode perfil,
                                       Instant evaluadaEn) {
        return clasificar(gravedad, tipoRecurso, perfil, evaluadaEn, null);
    }

    /** Igual que la anterior; con la unidad del lote también toma el stock informado por el Sistema Nacional. */
    public static ValidacionOferta clasificar(NivelGravedad gravedad, TipoRecurso tipoRecurso, JsonNode perfil,
                                       Instant evaluadaEn, String unidadLote) {
        String observaciones = recortar(perfil.path("observaciones").asText(null));
        if (!"HABILITADA".equals(perfil.path("estadoHabilitacion").asText())) {
            return sinHabilitacion(observaciones, evaluadaEn);
        }
        Optional<Capacidad> mejor = StreamSupport
                .stream(perfil.path("capacidadesHabilitadas").spliterator(), false)
                .filter(c -> tipoRecurso.name().equals(c.path("tipoRecurso").asText()))
                .map(ValidacionOfertasService::capacidad)
                .flatMap(Optional::stream)
                .max(Comparator.comparing(Capacidad::riesgo)
                        .thenComparing(Capacidad::competencia, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (mejor.isEmpty()) {
            return sinHabilitacion(observaciones, evaluadaEn);
        }
        Capacidad capacidad = mejor.get();
        ClasificacionOferta clasificacion = capacidad.riesgo().compareTo(gravedad) >= 0
                ? ClasificacionOferta.PRINCIPAL
                : ClasificacionOferta.APOYO;
        return new ValidacionOferta(clasificacion, capacidad.competencia(), capacidad.riesgo(),
                capacidad.vence(), observaciones, evaluadaEn, stockDisponible(perfil, unidadLote));
    }

    /**
     * Stock disponible de la ONG en la unidad del lote, sumando las entradas de {@code disponibilidad} con esa
     * unidad (comparadas sin distinguir mayúsculas ni espacios, igual que el conector de Bonita). Si la ONG no
     * tiene recursos con esa unidad, es 0: el compromiso fallaría. Null si el Sistema Nacional no informó el
     * dato, para no bloquear con versiones que todavía no lo envían.
     */
    static Integer stockDisponible(JsonNode perfil, String unidadLote) {
        JsonNode disponibilidad = perfil.path("disponibilidad");
        if (unidadLote == null || !disponibilidad.isArray()) {
            return null;
        }
        String unidad = normalizarUnidad(unidadLote);
        int total = 0;
        for (JsonNode entrada : disponibilidad) {
            if (unidad.equals(normalizarUnidad(entrada.path("unidadMedida").asText("")))) {
                total += entrada.path("cantidadDisponible").asInt(0);
            }
        }
        return total;
    }

    private static String normalizarUnidad(String valor) {
        return valor.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static ValidacionOferta sinHabilitacion(String observaciones, Instant evaluadaEn) {
        return new ValidacionOferta(ClasificacionOferta.SIN_HABILITACION, null, null, null, observaciones, evaluadaEn);
    }

    private static Optional<Capacidad> capacidad(JsonNode nodo) {
        NivelGravedad riesgo = enumeracion(NivelGravedad.class, nodo.path("nivelRiesgoMax").asText());
        if (riesgo == null) {
            return Optional.empty();
        }
        NivelCompetencia competencia = enumeracion(NivelCompetencia.class, nodo.path("nivelCompetencia").asText());
        LocalDate vence = null;
        if (nodo.path("fechaVencimiento").isTextual()) {
            try {
                vence = LocalDate.parse(nodo.path("fechaVencimiento").asText());
            } catch (DateTimeParseException ex) {
                vence = null;
            }
        }
        return Optional.of(new Capacidad(riesgo, competencia, vence));
    }

    private static <E extends Enum<E>> E enumeracion(Class<E> tipo, String valor) {
        try {
            return valor == null || valor.isBlank() ? null : Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String recortar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.length() <= MAX_OBSERVACIONES ? texto : texto.substring(0, MAX_OBSERVACIONES);
    }

    private record Capacidad(NivelGravedad riesgo, NivelCompetencia competencia, LocalDate vence) {}
}

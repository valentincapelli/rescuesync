package com.grupo21.rescuesync.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.config.BonitaProperties;
import com.grupo21.rescuesync.config.ConvocatoriaProperties;
import com.grupo21.rescuesync.dto.CrearEmergenciaRequest;
import com.grupo21.rescuesync.dto.EmergenciaResponse;
import com.grupo21.rescuesync.dto.LoteOfertasConsolidadasResponse;
import com.grupo21.rescuesync.dto.OfertaConsolidadaResponse;
import com.grupo21.rescuesync.dto.OngResumenResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.DecisionCobertura;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Municipio;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergenciaService {

    private final EmergenciaRepository emergenciaRepository;
    private final LoteRepository loteRepository;
    private final OfertaRepository ofertaRepository;
    private final BonitaClient bonitaClient;
    private final BonitaProperties bonitaProperties;
    private final UsuarioRepository usuarioRepository;
    private final ConvocatoriaProperties convocatoriaProperties;
    private final HistorialEstadoService historialEstadoService;

        @Transactional
    public EmergenciaResponse crear(
            CrearEmergenciaRequest request,
            String emailUsuario
        ) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Usuario no encontrado"
                        )
                );

        Municipio municipio = usuario.getMunicipio();

        if (municipio == null) {
                throw new BusinessException(
                        "El usuario no está asociado a un municipio"
                );
        }

        Emergencia emergencia = new Emergencia();

        emergencia.setTipoDesastre(request.tipoDesastre());
        emergencia.setNivelGravedad(request.nivelGravedad());
        emergencia.setZonaAfectada(request.zonaAfectada());
        emergencia.setDescripcion(request.descripcion());
        emergencia.setMunicipio(municipio);
        emergencia.setEstado(EstadoEmergencia.REGISTRADA);

        Emergencia guardada = emergenciaRepository.save(emergencia);
        historialEstadoService.registrarAlta(guardada);

        iniciarProcesoBonita(guardada);

        return toResponse(guardada);
    }

    /**
     * Instancia el proceso RescueSync en Bonita para esta emergencia (E2-11/E2-12).
     * Es "best effort": la emergencia ya quedó guardada en Postgres antes de esto,
     * así que si Bonita no está disponible o falla la instanciación, no perdemos
     * el alta — queda con bonitaCaseId en null y el problema se loguea, en vez de
     * romper la respuesta al usuario por un problema del lado de Bonita.
     */
    private void iniciarProcesoBonita(Emergencia emergencia) {
        try {
            String idProceso = bonitaClient.buscarIdProceso(bonitaProperties.processName())
                    .orElseThrow(() -> new BonitaIntegrationException(
                            "No hay un proceso habilitado llamado '" + bonitaProperties.processName()
                                    + "' en Bonita", null));

            // Entradas del contrato de instanciación definido en Bonita Studio.
            // Bonita las utiliza para inicializar las variables de proceso.
            Map<String, Object> entradasContrato = Map.of(
                    "emergenciaIdInput", emergencia.getId(),
                    "plazoConvocatoriaInput", convocatoriaProperties.plazoMs()
            );

            String caseId = bonitaClient.instanciarProceso(idProceso, entradasContrato);

            emergencia.setBonitaCaseId(Long.valueOf(caseId));
            emergenciaRepository.save(emergencia);

            bonitaClient.completarTarea(caseId, "Registrar emergencia");

            log.info("Emergencia {}: instancia de Bonita creada, caseId={}", emergencia.getId(), caseId);
        } catch (BonitaIntegrationException ex) {
            log.warn("Emergencia {}: no se pudo instanciar el proceso en Bonita ({})",
                    emergencia.getId(), ex.getMessage());
        } catch (NumberFormatException | IllegalStateException ex) {
            log.warn("Emergencia {}: Bonita devolvió un caseId no numérico ({})",
                    emergencia.getId(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public EmergenciaResponse obtener(Long id) {
        Emergencia emergencia = emergenciaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emergencia", id));

        return toResponse(emergencia);
    }

    @Transactional(readOnly = true)
    public java.util.List<EmergenciaResponse> listar() {
        return emergenciaRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoteOfertasConsolidadasResponse> ofertasConsolidadas(Long emergenciaId) {
        if (!emergenciaRepository.existsById(emergenciaId)) {
            throw new ResourceNotFoundException("Emergencia", emergenciaId);
        }

        List<Lote> lotes = loteRepository.findByEmergenciaIdOrderByIdAsc(emergenciaId);
        Map<Long, List<Oferta>> ofertasPorLote = ofertaRepository.findConOngByEmergenciaId(emergenciaId)
                .stream()
                .collect(Collectors.groupingBy(oferta -> oferta.getLote().getId()));

        return lotes.stream().map(lote -> {
            List<Oferta> ofertas = ofertasPorLote.getOrDefault(lote.getId(), List.of());
            List<OfertaConsolidadaResponse> respuestas = ofertas.stream()
                    .map(oferta -> new OfertaConsolidadaResponse(
                            oferta.getId(),
                            new OngResumenResponse(oferta.getOng().getId(), oferta.getOng().getNombre(),
                                    oferta.getOng().getCuit()),
                            oferta.getCantidadOfrecida(),
                            oferta.getObservaciones(),
                            oferta.getEstado()))
                    .toList();

            return new LoteOfertasConsolidadasResponse(
                    lote.getId(), lote.getTipoRecurso(), lote.getDescripcion(),
                    lote.getCantidadRequerida(), lote.getUnidadMedida(), lote.getEstado(),
                    ofertas.stream().mapToLong(Oferta::getCantidadOfrecida).sum(), respuestas);
        }).toList();
    }

    /**
     * "Publicar convocatoria": cierra el desglose y abre la ventana de ofertas.
     * Pasa la emergencia a CONVOCATORIA_PUBLICADA y todos sus lotes a PUBLICADO en una
     * sola transacción. Exige al menos un lote y no se puede publicar dos veces.
     */
    @Transactional
    public EmergenciaResponse publicarConvocatoria(Long id) {
    Emergencia emergencia = emergenciaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Emergencia", id));

    LoteService.validarDesgloseAbierto(emergencia);

    java.util.List<Lote> lotes = loteRepository.findByEmergenciaId(id);

    if (lotes.isEmpty()) {
            throw new BusinessException(
                    "La emergencia " + id + " no tiene lotes: cargá al menos uno antes de publicar la convocatoria"
            );
    }

    lotes.forEach(lote -> historialEstadoService.cambiarEstado(lote, EstadoLote.PUBLICADO));

    historialEstadoService.cambiarEstado(emergencia, EstadoEmergencia.CONVOCATORIA_PUBLICADA);

    emergencia.setFechaCierreConvocatoria(
            LocalDateTime.now().plus(
                    convocatoriaProperties.plazoMs(),
                    ChronoUnit.MILLIS
            )
    );

    sincronizarPublicacionConBonita(emergencia);

    return toResponse(emergencia);
    }

    /**
     * "Evaluar cobertura de lotes": el Centro Coordinador decide qué camino toma el proceso.
     * Compara lo ofrecido contra lo requerido de cada lote (mismo criterio que las ofertas
     * consolidadas) y:
     * <ul>
     *   <li>COMPLETA: exige todos los lotes cubiertos; los marca CUBIERTO y la emergencia pasa a
     *       COBERTURA_EVALUADA.</li>
     *   <li>PARCIAL: exige al menos un lote sin cubrir; marca cada lote CUBIERTO o
     *       PARCIALMENTE_CUBIERTO y la emergencia pasa a COBERTURA_PARCIAL.</li>
     *   <li>SIN_COBERTURA: reabre la convocatoria con un nuevo plazo; no cambia estados.</li>
     * </ul>
     * Solo aplica con la emergencia en CONVOCATORIA_PUBLICADA. Después completa la tarea en Bonita
     * con la decisión.
     */
    @Transactional
    public EmergenciaResponse decidirCobertura(Long id, DecisionCobertura decision) {
        Emergencia emergencia = emergenciaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emergencia", id));

        if (emergencia.getEstado() != EstadoEmergencia.CONVOCATORIA_PUBLICADA) {
            throw new BusinessException(
                    "La cobertura de la emergencia " + id + " solo se evalúa con la convocatoria publicada "
                            + "(estado actual: " + emergencia.getEstado() + ")");
        }

        List<Lote> lotes = loteRepository.findByEmergenciaIdOrderByIdAsc(id);
        if (lotes.isEmpty()) {
            throw new BusinessException("La emergencia " + id + " no tiene lotes para evaluar");
        }

        Map<Long, Long> ofrecidoPorLote = ofertaRepository.findConOngByEmergenciaId(id).stream()
                .collect(Collectors.groupingBy(oferta -> oferta.getLote().getId(),
                        Collectors.summingLong(Oferta::getCantidadOfrecida)));
        Predicate<Lote> cubierto = lote ->
                ofrecidoPorLote.getOrDefault(lote.getId(), 0L) >= lote.getCantidadRequerida();
        boolean todosCubiertos = lotes.stream().allMatch(cubierto);

        switch (decision) {
            case COMPLETA -> {
                if (!todosCubiertos) {
                    throw new BusinessException(
                            "No se puede decidir cobertura completa: hay lotes que no alcanzan la cantidad requerida");
                }
                lotes.forEach(lote -> historialEstadoService.cambiarEstado(lote, EstadoLote.CUBIERTO));
                historialEstadoService.cambiarEstado(emergencia, EstadoEmergencia.COBERTURA_EVALUADA);
            }
            case PARCIAL -> {
                if (todosCubiertos) {
                    throw new BusinessException(
                            "No se puede decidir cobertura parcial: todos los lotes están cubiertos");
                }
                lotes.forEach(lote -> historialEstadoService.cambiarEstado(lote,
                        cubierto.test(lote) ? EstadoLote.CUBIERTO : EstadoLote.PARCIALMENTE_CUBIERTO));
                historialEstadoService.cambiarEstado(emergencia, EstadoEmergencia.COBERTURA_PARCIAL);
            }
            case SIN_COBERTURA -> emergencia.setFechaCierreConvocatoria(
                    LocalDateTime.now().plus(convocatoriaProperties.plazoMs(), ChronoUnit.MILLIS));
        }

        sincronizarCoberturaConBonita(emergencia, decision);

        return toResponse(emergencia);
    }

    private EmergenciaResponse toResponse(Emergencia emergencia) {
        return new EmergenciaResponse(
                emergencia.getId(),
                emergencia.getTipoDesastre(),
                emergencia.getNivelGravedad(),
                emergencia.getZonaAfectada(),
                emergencia.getDescripcion(),
                emergencia.getMunicipio().getNombre(),
                emergencia.getEstado(),
                emergencia.getBonitaCaseId(),
                emergencia.getCreatedAt()
        );
    }

    private void sincronizarCoberturaConBonita(Emergencia emergencia, DecisionCobertura decision) {
        if (emergencia.getBonitaCaseId() == null) {
            log.warn(
                    "Emergencia {}: no tiene bonitaCaseId; no se sincroniza la cobertura con Bonita",
                    emergencia.getId()
            );
            return;
        }

        String caseId = emergencia.getBonitaCaseId().toString();

        try {
            bonitaClient.completarTarea(
                    caseId,
                    "Evaluar cobertura de lotes",
                    Map.of("decisionCoberturaInput", decision.name())
            );

            log.info(
                    "Emergencia {}: cobertura {} sincronizada con Bonita, caseId={}",
                    emergencia.getId(),
                    decision,
                    caseId
            );

        } catch (RuntimeException ex) {
            // A diferencia de la publicación, acá no se traga el error: la decisión cambia estados
            // locales y, si Bonita no la recibe, el caso queda desfasado. Al propagar la excepción
            // se revierte la transacción y el cliente recibe un 502.
            log.error(
                    "Emergencia {}: no se pudo sincronizar la cobertura con Bonita, caseId={} ({})",
                    emergencia.getId(),
                    caseId,
                    ex.getMessage()
            );
            throw new BonitaIntegrationException(
                    "No se pudo registrar la decisión de cobertura en Bonita (caso " + caseId + "): "
                            + ex.getMessage(),
                    ex
            );
        }
    }

    private void sincronizarPublicacionConBonita(Emergencia emergencia) {
        if (emergencia.getBonitaCaseId() == null) {
            log.warn(
                    "Emergencia {}: no tiene bonitaCaseId; no se sincroniza la publicación con Bonita",
                    emergencia.getId()
            );
            return;
        }

        String caseId = emergencia.getBonitaCaseId().toString();

        try {
            bonitaClient.completarTarea(
                    caseId,
                    "Revisar y desglosar emergencia"
            );

            bonitaClient.completarTarea(
                    caseId,
                    "Publicar convocatoria"
            );

            log.info(
                    "Emergencia {}: publicación sincronizada con Bonita, caseId={}",
                    emergencia.getId(),
                    caseId
            );

        } catch (RuntimeException ex) {
            log.warn(
                    "Emergencia {}: no se pudo sincronizar la publicación con Bonita, caseId={} ({})",
                    emergencia.getId(),
                    caseId,
                    ex.getMessage()
            );
        }
    }
}

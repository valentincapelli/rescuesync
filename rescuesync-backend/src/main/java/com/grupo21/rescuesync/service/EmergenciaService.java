package com.grupo21.rescuesync.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.config.BonitaProperties;
import com.grupo21.rescuesync.config.ConvocatoriaProperties;
import com.grupo21.rescuesync.dto.CrearEmergenciaRequest;
import com.grupo21.rescuesync.dto.EmergenciaResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Municipio;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergenciaService {

    private final EmergenciaRepository emergenciaRepository;
    private final LoteRepository loteRepository;
    private final BonitaClient bonitaClient;
    private final BonitaProperties bonitaProperties;
    private final UsuarioRepository usuarioRepository;
    private final ConvocatoriaProperties convocatoriaProperties;

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

    lotes.forEach(lote -> lote.setEstado(EstadoLote.PUBLICADO));

    emergencia.setEstado(EstadoEmergencia.CONVOCATORIA_PUBLICADA);

    emergencia.setFechaCierreConvocatoria(
            LocalDateTime.now().plus(
                    convocatoriaProperties.plazoMs(),
                    ChronoUnit.MILLIS
            )
    );

    sincronizarPublicacionConBonita(emergencia);

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

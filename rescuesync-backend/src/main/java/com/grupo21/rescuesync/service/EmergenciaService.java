package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.CrearEmergenciaRequest;
import com.grupo21.rescuesync.dto.EmergenciaResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmergenciaService {

    private final EmergenciaRepository emergenciaRepository;
    private final LoteRepository loteRepository;

    public EmergenciaResponse crear(CrearEmergenciaRequest request) {
        Emergencia emergencia = new Emergencia();

        emergencia.setTipoDesastre(request.tipoDesastre());
        emergencia.setNivelGravedad(request.nivelGravedad());
        emergencia.setZonaAfectada(request.zonaAfectada());
        emergencia.setDescripcion(request.descripcion());
        emergencia.setMunicipio(request.municipio());
        emergencia.setEstado(EstadoEmergencia.REGISTRADA);

        Emergencia guardada = emergenciaRepository.save(emergencia);

        return toResponse(guardada);
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

        java.util.List<Lote> lotes = loteRepository.findByEmergenciaIdOrderByIdAsc(id);
        if (lotes.isEmpty()) {
            throw new BusinessException(
                    "La emergencia " + id + " no tiene lotes: cargá al menos uno antes de publicar la convocatoria"
            );
        }

        lotes.forEach(lote -> lote.setEstado(EstadoLote.PUBLICADO));
        emergencia.setEstado(EstadoEmergencia.CONVOCATORIA_PUBLICADA);

        return toResponse(emergencia);
    }

    private EmergenciaResponse toResponse(Emergencia emergencia) {
        return new EmergenciaResponse(
                emergencia.getId(),
                emergencia.getTipoDesastre(),
                emergencia.getNivelGravedad(),
                emergencia.getZonaAfectada(),
                emergencia.getDescripcion(),
                emergencia.getMunicipio(),
                emergencia.getEstado(),
                emergencia.getBonitaCaseId(),
                emergencia.getCreatedAt()
        );
    }
}

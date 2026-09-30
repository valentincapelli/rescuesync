package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.CrearLoteRequest;
import com.grupo21.rescuesync.dto.LoteResponse;
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

import java.util.List;

/**
 * Desglose de una emergencia en lotes de necesidades (tarea "Revisar y desglosar emergencia"
 * del Centro Coordinador Regional). Mientras la convocatoria no se publique, los lotes quedan
 * en {@link EstadoLote#BORRADOR} y se pueden crear, editar y borrar.
 */
@Service
@RequiredArgsConstructor
public class LoteService {

    private final LoteRepository loteRepository;
    private final EmergenciaRepository emergenciaRepository;

    @Transactional
    public LoteResponse crear(Long emergenciaId, CrearLoteRequest request) {
        Emergencia emergencia = buscarEmergencia(emergenciaId);
        validarDesgloseAbierto(emergencia);

        Lote lote = new Lote();
        lote.setEmergencia(emergencia);
        aplicarDatos(lote, request);
        lote.setEstado(EstadoLote.BORRADOR);

        // Cargar el primer lote implica que el CCR tomó la emergencia para revisarla.
        if (emergencia.getEstado() == EstadoEmergencia.REGISTRADA) {
            emergencia.setEstado(EstadoEmergencia.EN_REVISION);
        }

        Lote guardado = loteRepository.save(lote);
        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<LoteResponse> listarPorEmergencia(Long emergenciaId) {
        if (!emergenciaRepository.existsById(emergenciaId)) {
            throw new ResourceNotFoundException("Emergencia", emergenciaId);
        }

        return loteRepository.findByEmergenciaIdOrderByIdAsc(emergenciaId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LoteResponse editar(Long emergenciaId, Long loteId, CrearLoteRequest request) {
        Lote lote = buscarLoteDeEmergencia(emergenciaId, loteId);
        validarDesgloseAbierto(lote.getEmergencia());

        aplicarDatos(lote, request);
        return toResponse(lote);
    }

    @Transactional
    public void eliminar(Long emergenciaId, Long loteId) {
        Lote lote = buscarLoteDeEmergencia(emergenciaId, loteId);
        Emergencia emergencia = lote.getEmergencia();
        validarDesgloseAbierto(emergencia);

        // orphanRemoval en Emergencia.lotes se encarga del DELETE.
        emergencia.getLotes().remove(lote);
    }

    private Emergencia buscarEmergencia(Long emergenciaId) {
        return emergenciaRepository.findById(emergenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergencia", emergenciaId));
    }

    private Lote buscarLoteDeEmergencia(Long emergenciaId, Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote", loteId));
        if (!lote.getEmergencia().getId().equals(emergenciaId)) {
            // No revelamos que el lote existe en otra emergencia.
            throw new ResourceNotFoundException("Lote", loteId);
        }
        return lote;
    }

    /** Solo se puede desglosar mientras la convocatoria no esté publicada. */
    static void validarDesgloseAbierto(Emergencia emergencia) {
        EstadoEmergencia estado = emergencia.getEstado();
        if (estado != EstadoEmergencia.REGISTRADA && estado != EstadoEmergencia.EN_REVISION) {
            throw new BusinessException(
                    "La emergencia " + emergencia.getId() + " está en estado " + estado
                            + ": ya no se pueden modificar sus lotes"
            );
        }
    }

    private static void aplicarDatos(Lote lote, CrearLoteRequest request) {
        lote.setTipoRecurso(request.tipoRecurso());
        lote.setDescripcion(request.descripcion().trim());
        lote.setCantidadRequerida(request.cantidadRequerida());
        lote.setUnidadMedida(request.unidadMedida().trim());
    }

    LoteResponse toResponse(Lote lote) {
        return new LoteResponse(
                lote.getId(),
                lote.getEmergencia().getId(),
                lote.getTipoRecurso(),
                lote.getDescripcion(),
                lote.getCantidadRequerida(),
                lote.getUnidadMedida(),
                lote.getEstado()
        );
    }
}

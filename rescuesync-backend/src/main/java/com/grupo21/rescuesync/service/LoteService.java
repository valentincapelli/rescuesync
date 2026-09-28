package com.grupo21.rescuesync.service;
import com.grupo21.rescuesync.dto.CrearLoteRequest;
import com.grupo21.rescuesync.dto.LoteResponse;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoteService {

    private final LoteRepository loteRepository;
    private final EmergenciaRepository emergenciaRepository;

    public LoteResponse crear(Long emergenciaId, CrearLoteRequest request) {

        Emergencia emergencia = emergenciaRepository.findById(emergenciaId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Emergencia con id " + emergenciaId + " no encontrada"
                        )
                );

        Lote lote = new Lote();

        lote.setEmergencia(emergencia);
        lote.setTipoRecurso(request.tipoRecurso());
        lote.setDescripcion(request.descripcion());
        lote.setCantidadRequerida(request.cantidadRequerida());
        lote.setUnidadMedida(request.unidadMedida());
        lote.setEstado(EstadoLote.PUBLICADO);

        Lote guardado = loteRepository.save(lote);

        return toResponse(guardado);
    }

    private LoteResponse toResponse(Lote lote) {
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
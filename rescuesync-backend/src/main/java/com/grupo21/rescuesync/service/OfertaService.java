package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.OfertaResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final LoteRepository loteRepository;

    public OfertaResponse crear(Long loteId, CrearOfertaRequest request) {

        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lote con id " + loteId + " no encontrado"
                        )
                );
        
        Oferta ofertaExistente = ofertaRepository.findByOngNombreIgnoreCaseAndLoteId(request.ongNombre(), loteId)
                .stream()
                .findFirst()
                .orElse(null);
        if (ofertaExistente != null) {
            throw new BusinessException("Ya existe una oferta de esta ONG para este lote");
        }
        Oferta oferta = new Oferta();

        oferta.setLote(lote);
        oferta.setOngNombre(request.ongNombre());
        oferta.setCantidadOfrecida(request.cantidadOfrecida());
        oferta.setObservaciones(request.observaciones());
        oferta.setEstado(EstadoOferta.PENDIENTE);

        Oferta guardada = ofertaRepository.save(oferta);

        return toResponse(guardada);
    }

    private OfertaResponse toResponse(Oferta oferta) {
        return new OfertaResponse(
                oferta.getId(),
                oferta.getLote().getId(),
                oferta.getOngNombre(),
                oferta.getCantidadOfrecida(),
                oferta.getObservaciones(),
                oferta.getEstado()
        );
    }
}
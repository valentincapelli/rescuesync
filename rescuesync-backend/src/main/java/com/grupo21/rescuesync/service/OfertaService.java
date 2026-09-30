package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.ofertas.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.EditarOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.OfertaResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final LoteRepository loteRepository;

    @Transactional
    public OfertaResponse crear(Long loteId, CrearOfertaRequest request) {

        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lote con id " + loteId + " no encontrado"
                        )
                );
        validarLotePublicado(lote);

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

    @Transactional
    public OfertaResponse editar(Long ofertaId, EditarOfertaRequest request) {

        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Oferta con id " + ofertaId + " no encontrada"
                        )
                );

        if (!oferta.getOngNombre().equalsIgnoreCase(request.ongNombre().trim())) {
                throw new BusinessException(
                        "La ONG indicada no tiene permiso para editar esta oferta"
                );
        }

        validarLotePublicado(oferta.getLote());

        oferta.setCantidadOfrecida(request.cantidadOfrecida());
        oferta.setObservaciones(request.observaciones());

        Oferta actualizada = ofertaRepository.save(oferta);

        return toResponse(actualizada);
        }

    /** Solo se oferta sobre lotes de una convocatoria publicada (no en BORRADOR ni cerrados). */
    private void validarLotePublicado(Lote lote) {
        if (lote.getEstado() != EstadoLote.PUBLICADO) {
            throw new BusinessException(
                    "El lote " + lote.getId() + " está en estado " + lote.getEstado()
                            + ": no admite ofertas"
            );
        }
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
package com.grupo21.sistemanacional.service;

import com.grupo21.sistemanacional.dto.compromisos.CrearCompromisoRequest;
import com.grupo21.sistemanacional.dto.compromisos.CrearCompromisoResponse;
import com.grupo21.sistemanacional.exception.ResourceNotFoundException;
import com.grupo21.sistemanacional.model.Compromiso;
import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.repository.CompromisoRepository;
import com.grupo21.sistemanacional.repository.RecursoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompromisoService {

    private final RecursoRepository recursoRepository;
    private final CompromisoRepository compromisoRepository;

    @Transactional
    public CrearCompromisoResponse comprometer(
            CrearCompromisoRequest request
    ) {
        // Primera comprobación de idempotencia.
        var existente = compromisoRepository
            .findByRecursoIdAndOfertaRef(
                    request.recursoId(),
                    request.ofertaRef()
            );

        if (existente.isPresent()) {
            return toResponse(existente.get());
        }

        // Bloqueo pesimista del recurso.
        Recurso recurso = recursoRepository
                .findByIdForUpdate(request.recursoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el recurso " + request.recursoId()
                ));

        // Segunda comprobación: otra transacción pudo crear el
        // compromiso mientras esperábamos el bloqueo.
        existente = compromisoRepository
                .findByRecursoIdAndOfertaRef(
                        request.recursoId(),
                        request.ofertaRef()
                );

        if (existente.isPresent()) {
            return toResponse(existente.get());
        }

        // Valida stock disponible e incrementa cantidadComprometida.
        recurso.comprometer(request.cantidad());

        Compromiso compromiso = new Compromiso(
                recurso,
                request.emergenciaRef(),
                request.ofertaRef(),
                request.cantidad()
        );

        compromisoRepository.save(compromiso);

        return toResponse(compromiso);
    }

    private CrearCompromisoResponse toResponse(Compromiso compromiso) {
        return new CrearCompromisoResponse(
                compromiso.getId(),
                compromiso.getRecurso().getId(),
                compromiso.getEmergenciaRef(),
                compromiso.getOfertaRef(),
                compromiso.getCantidad(),
                compromiso.getEstado(),
                compromiso.getFechaLiberacion()
        );
    }
}
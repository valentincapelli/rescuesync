package com.grupo21.sistemanacional.service;

import com.grupo21.sistemanacional.dto.compromisos.CompromisoLiberadoResponse;
import com.grupo21.sistemanacional.dto.compromisos.LiberacionResponse;
import com.grupo21.sistemanacional.dto.compromisos.LiberarCompromisosRequest;
import com.grupo21.sistemanacional.exception.BusinessException;
import com.grupo21.sistemanacional.exception.ResourceNotFoundException;
import com.grupo21.sistemanacional.model.Compromiso;
import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.repository.CompromisoRepository;
import com.grupo21.sistemanacional.repository.RecursoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Liberación de recursos al cerrar una emergencia (o una oferta). Todo ocurre en una transacción:
 * si un compromiso no se puede liberar, no se libera ninguno.
 */
@Service
@RequiredArgsConstructor
public class LiberacionService {

    private final CompromisoRepository compromisoRepository;
    private final RecursoRepository recursoRepository;

    @Transactional
    public LiberacionResponse liberar(LiberarCompromisosRequest request) {
        Long emergenciaRef = request.emergenciaRef();
        Long ofertaRef = request.ofertaRef();

        // 1) Qué recursos están involucrados (solo ids: todavía no se cargan los compromisos).
        List<Long> recursoIds = ofertaRef == null
                ? compromisoRepository.findRecursoIdsByEmergenciaRef(emergenciaRef)
                : compromisoRepository.findRecursoIdsByEmergenciaRefAndOfertaRef(emergenciaRef, ofertaRef);
        if (recursoIds.isEmpty()) {
            throw new ResourceNotFoundException(ofertaRef == null
                    ? "No hay compromisos para la emergencia " + emergenciaRef
                    : "No hay compromisos para la oferta " + ofertaRef + " de la emergencia " + emergenciaRef);
        }

        // 2) Se bloquean los recursos (ordenados por id, igual que al comprometer: sin deadlocks).
        Map<Long, Recurso> recursos = recursoRepository.findAllByIdForUpdate(recursoIds).stream()
                .collect(Collectors.toMap(Recurso::getId, Function.identity()));

        // 3) Recién ahora se leen los compromisos: ya reflejan cualquier liberación concurrente que haya terminado.
        List<Compromiso> compromisos = ofertaRef == null
                ? compromisoRepository.findByEmergenciaRef(emergenciaRef)
                : compromisoRepository.findByEmergenciaRefAndOfertaRef(emergenciaRef, ofertaRef);

        Instant ahora = Instant.now();
        int liberados = 0;
        int yaLiberados = 0;
        for (Compromiso compromiso : compromisos) {
            if (compromiso.isLiberado()) {
                yaLiberados++;
                continue;
            }
            Recurso recurso = recursos.get(compromiso.getRecurso().getId());
            if (recurso == null) {
                // Apareció un compromiso nuevo entre el paso 1 y el 3: su recurso no está bloqueado.
                throw new BusinessException("Los compromisos cambiaron durante la liberación; reintentá la operación");
            }
            recurso.liberar(compromiso.getCantidad());
            compromiso.marcarLiberado(ahora);
            liberados++;
        }

        List<CompromisoLiberadoResponse> detalle = compromisos.stream()
                .map(CompromisoLiberadoResponse::from)
                .toList();
        return new LiberacionResponse(emergenciaRef, ofertaRef, liberados, yaLiberados, detalle);
    }
}

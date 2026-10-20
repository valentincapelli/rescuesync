package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.compromisos.LiberacionResponse;
import com.grupo21.sistemanacional.dto.compromisos.LiberarCompromisosRequest;
import com.grupo21.sistemanacional.service.LiberacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Liberación")
@RestController
@RequestMapping("/api/compromisos")
@RequiredArgsConstructor
public class LiberacionController {

    private final LiberacionService liberacionService;

    @Operation(summary = "Liberar los recursos comprometidos de una oferta o de toda una emergencia",
            description = "Lo invoca Bonita (SERVICIO) al cerrar el proyecto. Con ofertaRef libera esa oferta "
                    + "(todas las ONGs de un consorcio); sin ofertaRef, todos los compromisos de la emergencia. "
                    + "Es idempotente: reintentar no devuelve stock dos veces. 404 si no hay compromisos.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICIO')")
    @PostMapping("/liberar")
    public LiberacionResponse liberar(@Valid @RequestBody LiberarCompromisosRequest request) {
        return liberacionService.liberar(request);
    }
}

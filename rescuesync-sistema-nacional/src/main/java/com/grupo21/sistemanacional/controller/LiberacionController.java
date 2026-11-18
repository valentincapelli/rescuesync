package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.compromisos.LiberacionResponse;
import com.grupo21.sistemanacional.dto.compromisos.LiberarCompromisosRequest;
import com.grupo21.sistemanacional.service.LiberacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @ApiResponse(responseCode = "200", description = "Compromisos liberados. `liberados` cuenta los que se liberaron ahora y "
            + "`yaLiberados` los que ya lo estaban (un reintento responde 200 con `liberados: 0`).",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = LiberacionResponse.class)))
    @ApiResponse(responseCode = "404", description = "No hay compromisos para esa emergencia u oferta.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @ApiResponse(responseCode = "409", description = "Algún compromiso no se pudo liberar o cambió durante la operación: "
            + "es todo o nada, no se libera ninguno. Se puede reintentar.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICIO')")
    @PostMapping("/liberar")
    public LiberacionResponse liberar(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "`emergenciaRef` es obligatorio; `ofertaRef` es opcional.",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "Una oferta", value = "{\"emergenciaRef\": 12, \"ofertaRef\": 34}"),
                                    @ExampleObject(name = "Toda la emergencia (cierre)", value = "{\"emergenciaRef\": 12}")
                            }))
            @RequestBody LiberarCompromisosRequest request) {
        return liberacionService.liberar(request);
    }
}

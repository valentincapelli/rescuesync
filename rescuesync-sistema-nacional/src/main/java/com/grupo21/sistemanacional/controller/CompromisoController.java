package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.compromisos.CrearCompromisoRequest;
import com.grupo21.sistemanacional.dto.compromisos.CrearCompromisoResponse;
import com.grupo21.sistemanacional.service.CompromisoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Compromisos")
@RestController
@RequestMapping("/api/compromisos")
@RequiredArgsConstructor
public class CompromisoController {

    private final CompromisoService compromisoService;

    @Operation(summary = "Comprometer recursos con control de concurrencia",
            description = "Reserva `cantidad` unidades de un recurso de una ONG para una oferta de RescueSync. "
                    + "En una sola transacción bloquea la fila del recurso (`SELECT ... FOR UPDATE`) y suma la cantidad a "
                    + "lo comprometido, así dos pedidos simultáneos sobre las últimas unidades no se pisan: prospera uno y "
                    + "el otro recibe 409 con lo disponible.\n\n"
                    + "**Idempotente por `recursoId` + `ofertaRef`:** si ya existe ese compromiso se devuelve tal cual "
                    + "(también con 201) sin reservar stock otra vez, aunque el reintento traiga otra `cantidad` o "
                    + "`emergenciaRef`. `emergenciaRef` y `ofertaRef` son los ids de RescueSync y deben ser los mismos "
                    + "que se envían después a `POST /api/compromisos/liberar`.")
    @ApiResponse(responseCode = "201", description = "Compromiso creado (o el ya existente, si es un reintento).",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CrearCompromisoResponse.class),
                    examples = @ExampleObject(value = "{\"id\": 1, \"recursoId\": 5, \"emergenciaRef\": 12, "
                            + "\"ofertaRef\": 34, \"cantidad\": 2, \"estado\": \"COMPROMETIDO\", \"fechaLiberacion\": null}")))
    @ApiResponse(responseCode = "404", description = "No existe el recurso indicado.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @ApiResponse(responseCode = "409", description = "Stock insuficiente: el mensaje informa cuántas unidades se pidieron "
            + "y cuántas hay disponibles. No se crea el compromiso.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @PreAuthorize("hasRole('SERVICIO')")
    @PostMapping
    public ResponseEntity<CrearCompromisoResponse> comprometer(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"recursoId\": 5, \"emergenciaRef\": 12, "
                                    + "\"ofertaRef\": 34, \"cantidad\": 2}")))
            @RequestBody CrearCompromisoRequest request
    ) {
        CrearCompromisoResponse response =
                compromisoService.comprometer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}

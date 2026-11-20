package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.certificaciones.ValidacionCertificacionResponse;
import com.grupo21.sistemanacional.dto.certificaciones.ValidarCertificacionRequest;
import com.grupo21.sistemanacional.service.CertificacionService;
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

@Tag(name = "Certificaciones")
@RestController
@RequestMapping("/api/certificaciones")
@RequiredArgsConstructor
public class ValidacionCertificacionController {

    private final CertificacionService certificacionService;

    @Operation(summary = "Validar las capacidades certificadas de una ONG (ADMIN o SERVICIO)",
            description = "Consulta por CUIT (11 dígitos, sin guiones) y tipo de recurso. Devuelve competencia y riesgo "
                    + "máximo de la certificación vigente si la ONG está habilitada; en otro caso, capacidades vacías y "
                    + "una observación (no es un rechazo: responde 200). Quien llama compara `nivelRiesgoMax` con la "
                    + "gravedad de la emergencia para decidir si la ONG va a un lote principal o de apoyo. "
                    + "No adjudica ofertas ni compromete recursos.")
    @ApiResponse(responseCode = "200", description = "Capacidades habilitadas (lista vacía si no hay certificación vigente, "
            + "la ONG no está habilitada o el CUIT no está registrado).",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ValidacionCertificacionResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "cuit": "30700000001",
                              "tipoRecurso": "ALIMENTOS",
                              "estadoHabilitacion": "HABILITADA",
                              "capacidadesHabilitadas": [
                                {
                                  "tipoRecurso": "ALIMENTOS",
                                  "nivelCompetencia": "INTERMEDIO",
                                  "nivelRiesgoMax": "MEDIO",
                                  "fechaVencimiento": null
                                }
                              ],
                              "observaciones": "Se informan el nivel de competencia y el riesgo máximo certificados para el tipo de recurso solicitado"
                            }""")))
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICIO')")
    @PostMapping("/validar")
    public ValidacionCertificacionResponse validar(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"cuit\": \"30700000001\", \"tipoRecurso\": \"ALIMENTOS\"}")))
            @RequestBody ValidarCertificacionRequest request) {
        return certificacionService.validar(request);
    }
}

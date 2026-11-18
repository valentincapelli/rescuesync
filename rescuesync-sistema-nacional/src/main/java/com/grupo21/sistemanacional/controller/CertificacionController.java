package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.certificaciones.ActualizarCertificacionRequest;
import com.grupo21.sistemanacional.dto.certificaciones.CertificacionResponse;
import com.grupo21.sistemanacional.dto.certificaciones.CrearCertificacionRequest;
import com.grupo21.sistemanacional.service.CertificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Certificaciones")
@RestController
@RequestMapping("/api/ongs/{ongId}/certificaciones")
@RequiredArgsConstructor
public class CertificacionController {

    private final CertificacionService certificacionService;

    @Operation(summary = "Listar las certificaciones de una ONG",
            description = "ADMIN y SERVICIO ven cualquiera; una ONG solo las propias. "
                    + "Incluye las vencidas, marcadas con vigente=false.")
    @GetMapping
    public List<CertificacionResponse> listar(@PathVariable Long ongId) {
        return certificacionService.listar(ongId);
    }

    @Operation(summary = "Certificar a una ONG para un tipo de recurso (solo ADMIN)",
            description = "Una ONG tiene a lo sumo una certificación por tipo de recurso. "
                    + "`nivelCompetencia`: BASICO, INTERMEDIO o AVANZADO. `nivelRiesgoMax`: BAJO, MEDIO, ALTO o CRITICO. "
                    + "`fechaVencimiento` es opcional (sin ella no vence) y no puede ser una fecha pasada.")
    @ApiResponse(responseCode = "201", description = "Certificación creada.")
    @ApiResponse(responseCode = "409", description = "La ONG ya tiene una certificación para ese tipo de recurso.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificacionResponse crear(@PathVariable Long ongId,
                                       @Valid
                                       @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                               content = @Content(mediaType = "application/json",
                                                       examples = @ExampleObject(value = "{\"tipoRecurso\": \"MEDICAMENTOS\", "
                                                               + "\"nivelCompetencia\": \"AVANZADO\", \"nivelRiesgoMax\": \"ALTO\", "
                                                               + "\"fechaVencimiento\": \"2027-12-31\"}")))
                                       @RequestBody CrearCertificacionRequest request) {
        return certificacionService.crear(ongId, request);
    }

    @Operation(summary = "Actualizar nivel, riesgo máximo o vencimiento de una certificación (solo ADMIN)",
            description = "El tipo de recurso no cambia: para otro tipo hay que crear otra certificación.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{certificacionId}")
    public CertificacionResponse actualizar(@PathVariable Long ongId,
                                            @PathVariable Long certificacionId,
                                            @Valid
                                            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                    content = @Content(mediaType = "application/json",
                                                            examples = @ExampleObject(value = "{\"nivelCompetencia\": \"AVANZADO\", "
                                                                    + "\"nivelRiesgoMax\": \"CRITICO\"}")))
                                            @RequestBody ActualizarCertificacionRequest request) {
        return certificacionService.actualizar(ongId, certificacionId, request);
    }

    @Operation(summary = "Eliminar una certificación (solo ADMIN)")
    @ApiResponse(responseCode = "204", description = "Certificación eliminada.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{certificacionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long ongId, @PathVariable Long certificacionId) {
        certificacionService.eliminar(ongId, certificacionId);
    }
}

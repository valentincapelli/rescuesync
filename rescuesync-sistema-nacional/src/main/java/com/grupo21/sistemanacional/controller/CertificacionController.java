package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.certificaciones.ActualizarCertificacionRequest;
import com.grupo21.sistemanacional.dto.certificaciones.CertificacionResponse;
import com.grupo21.sistemanacional.dto.certificaciones.CrearCertificacionRequest;
import com.grupo21.sistemanacional.service.CertificacionService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Certificar a una ONG para un tipo de recurso (solo ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificacionResponse crear(@PathVariable Long ongId,
                                       @Valid @RequestBody CrearCertificacionRequest request) {
        return certificacionService.crear(ongId, request);
    }

    @Operation(summary = "Actualizar nivel, riesgo máximo o vencimiento de una certificación (solo ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{certificacionId}")
    public CertificacionResponse actualizar(@PathVariable Long ongId,
                                            @PathVariable Long certificacionId,
                                            @Valid @RequestBody ActualizarCertificacionRequest request) {
        return certificacionService.actualizar(ongId, certificacionId, request);
    }

    @Operation(summary = "Eliminar una certificación (solo ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{certificacionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long ongId, @PathVariable Long certificacionId) {
        certificacionService.eliminar(ongId, certificacionId);
    }
}

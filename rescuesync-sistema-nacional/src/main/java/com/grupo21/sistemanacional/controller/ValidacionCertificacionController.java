package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.certificaciones.ValidacionCertificacionResponse;
import com.grupo21.sistemanacional.dto.certificaciones.ValidarCertificacionRequest;
import com.grupo21.sistemanacional.service.CertificacionService;
import io.swagger.v3.oas.annotations.Operation;
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
            description = "Consulta por CUIT y tipo de recurso. Devuelve competencia y riesgo máximo de la "
                    + "certificación vigente si la ONG está habilitada; en otro caso, capacidades vacías y una "
                    + "observación. No adjudica ofertas ni compromete recursos.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICIO')")
    @PostMapping("/validar")
    public ValidacionCertificacionResponse validar(@Valid @RequestBody ValidarCertificacionRequest request) {
        return certificacionService.validar(request);
    }
}

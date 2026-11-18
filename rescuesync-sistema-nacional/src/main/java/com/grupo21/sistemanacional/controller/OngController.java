package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.ongs.OngResponse;
import com.grupo21.sistemanacional.dto.ongs.RegistroOngRequest;
import com.grupo21.sistemanacional.service.OngService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "ONGs")
@RestController
@RequestMapping("/api/ongs")
@RequiredArgsConstructor
public class OngController {

    private final OngService ongService;

    @Operation(summary = "Registrar una ONG (público)",
            description = "Onboarding: crea la ONG y su usuario representante (rol ONG) en un solo paso. "
                    + "La ONG queda HABILITADA. El CUIT son 11 dígitos sin guiones. Después puede iniciar sesión "
                    + "con `POST /api/auth/login`.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "ONG y usuario creados.")
    @ApiResponse(responseCode = "409", description = "Ya existe una ONG con ese CUIT o el nombre de usuario está en uso.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public OngResponse registrar(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"cuit\": \"30711111111\", \"razonSocial\": \"Ayuda Sur\", "
                                    + "\"username\": \"ayuda.sur\", \"password\": \"secreto123\"}")))
            @RequestBody RegistroOngRequest request) {
        return ongService.registrar(request);
    }

    @Operation(summary = "Listar las ONGs habilitadas",
            description = "Solo las ONGs en estado HABILITADA, que son las únicas que pueden comprometer recursos.")
    @GetMapping
    public List<OngResponse> listarHabilitadas() {
        return ongService.listarHabilitadas();
    }

    @Operation(summary = "Obtener una ONG",
            description = "ADMIN y SERVICIO ven cualquiera; una ONG solo la propia.")
    @GetMapping("/{id}")
    public OngResponse obtener(@PathVariable Long id) {
        return ongService.obtener(id);
    }
}

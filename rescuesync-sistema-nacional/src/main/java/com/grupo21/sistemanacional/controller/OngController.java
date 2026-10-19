package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.ongs.OngResponse;
import com.grupo21.sistemanacional.dto.ongs.RegistroOngRequest;
import com.grupo21.sistemanacional.service.OngService;
import io.swagger.v3.oas.annotations.Operation;
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
            description = "Crea la ONG y su usuario representante en un solo paso. La ONG queda HABILITADA.")
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public OngResponse registrar(@Valid @RequestBody RegistroOngRequest request) {
        return ongService.registrar(request);
    }

    @Operation(summary = "Listar las ONGs habilitadas")
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

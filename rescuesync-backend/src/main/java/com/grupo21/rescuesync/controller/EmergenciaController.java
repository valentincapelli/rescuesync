package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.CrearEmergenciaRequest;
import com.grupo21.rescuesync.dto.EmergenciaResponse;
import com.grupo21.rescuesync.service.EmergenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "Emergencias")
@RestController
@RequestMapping("/api/emergencias")
@RequiredArgsConstructor
public class EmergenciaController {

    private final EmergenciaService emergenciaService;

    @Operation(summary = "Registrar una nueva emergencia")
    @PostMapping
    public ResponseEntity<EmergenciaResponse> crear(@Valid @RequestBody CrearEmergenciaRequest request) {
        EmergenciaResponse response = emergenciaService.crear(request);

        return ResponseEntity
                .created(URI.create("/api/emergencias/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Listar emergencias registradas")
    @GetMapping
    public List<EmergenciaResponse> listar() {
        return emergenciaService.listar();
    }

    @Operation(summary = "Obtener una emergencia por id")
    @GetMapping("/{id}")
    public EmergenciaResponse obtener(@PathVariable Long id) {
        return emergenciaService.obtener(id);
    }
}

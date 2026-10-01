package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.CrearEmergenciaRequest;
import com.grupo21.rescuesync.dto.EmergenciaResponse;
import com.grupo21.rescuesync.service.EmergenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping(value = "/api/emergencias", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class EmergenciaController {

    private final EmergenciaService emergenciaService;

    @Operation(summary = "Registrar una nueva emergencia")
    @PreAuthorize("hasAuthority('REGISTRAR_EMERGENCIA')")
    @ApiResponse(responseCode = "201", description = "Emergencia registrada")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergenciaResponse> crear(@Valid @RequestBody CrearEmergenciaRequest request) {
        EmergenciaResponse response = emergenciaService.crear(request);

        return ResponseEntity
                .created(URI.create("/api/emergencias/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Listar emergencias registradas")
    @PreAuthorize("hasAuthority('CONSULTAR_EMERGENCIAS')")
    @GetMapping
    public List<EmergenciaResponse> listar() {
        return emergenciaService.listar();
    }

    @Operation(summary = "Publicar la convocatoria de la emergencia",
            description = "Pasa la emergencia a CONVOCATORIA_PUBLICADA y todos sus lotes a PUBLICADO. "
                    + "409 si no tiene lotes o si ya estaba publicada.")
    @PreAuthorize("hasAuthority('PUBLICAR_EMERGENCIA')")
    @ApiResponse(responseCode = "200", description = "Convocatoria publicada")
    @ApiResponse(responseCode = "409", description = "Sin lotes o convocatoria ya publicada")
    @PostMapping("/{id}/convocatoria")
    public EmergenciaResponse publicarConvocatoria(@PathVariable Long id) {
        return emergenciaService.publicarConvocatoria(id);
    }

    @Operation(summary = "Obtener una emergencia por id")
    @PreAuthorize("hasAuthority('CONSULTAR_EMERGENCIAS')")
    @GetMapping("/{id}")
    public EmergenciaResponse obtener(@PathVariable Long id) {
        return emergenciaService.obtener(id);
    }
}

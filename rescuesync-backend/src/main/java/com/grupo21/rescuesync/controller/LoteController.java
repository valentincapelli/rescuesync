package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.CrearLoteRequest;
import com.grupo21.rescuesync.dto.LoteResponse;
import com.grupo21.rescuesync.service.LoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Lotes")
@RestController
@RequestMapping("/api/emergencias/{emergenciaId}/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final LoteService loteService;

    @Operation(summary = "Crear un lote (queda en BORRADOR hasta publicar la convocatoria)")
    @PreAuthorize("hasAuthority('CREAR_LOTE')")
    @PostMapping
    public ResponseEntity<LoteResponse> crear(
            @PathVariable Long emergenciaId,
            @Valid @RequestBody CrearLoteRequest request
    ) {
        LoteResponse response = loteService.crear(emergenciaId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "Listar los lotes de una emergencia")
    @PreAuthorize("hasAuthority('CONSULTAR_LOTES')")
    @GetMapping
    public List<LoteResponse> listar(@PathVariable Long emergenciaId) {
        return loteService.listarPorEmergencia(emergenciaId);
    }

    @Operation(summary = "Editar un lote mientras la convocatoria no esté publicada")
    @PreAuthorize("hasAuthority('EDITAR_LOTE')")
    @PutMapping("/{loteId}")
    public LoteResponse editar(
            @PathVariable Long emergenciaId,
            @PathVariable Long loteId,
            @Valid @RequestBody CrearLoteRequest request
    ) {
        return loteService.editar(emergenciaId, loteId, request);
    }

    @Operation(summary = "Borrar un lote mientras la convocatoria no esté publicada")
    @PreAuthorize("hasAuthority('BORRAR_LOTE')")
    @DeleteMapping("/{loteId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long emergenciaId,
            @PathVariable Long loteId
    ) {
        loteService.eliminar(emergenciaId, loteId);
        return ResponseEntity.noContent().build();
    }
}

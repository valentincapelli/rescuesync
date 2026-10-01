package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.ofertas.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.EditarOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.OfertaResponse;
import com.grupo21.rescuesync.service.OfertaService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotes/{loteId}/ofertas")
@RequiredArgsConstructor
public class OfertaController {

    private final OfertaService ofertaService;

    @PreAuthorize("hasAuthority('CREAR_OFERTA')")
    @PostMapping
    public ResponseEntity<OfertaResponse> crear(
            @PathVariable Long loteId,
            @Valid @RequestBody CrearOfertaRequest request
    ) {
        OfertaResponse response = ofertaService.crear(loteId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasAuthority('EDITAR_OFERTA')")
    @PutMapping("/{ofertaId}")
    public ResponseEntity<OfertaResponse> editar(
            @PathVariable Long ofertaId,
            @Valid @RequestBody EditarOfertaRequest request
    ) {
        OfertaResponse response = ofertaService.editar(ofertaId, request);

        return ResponseEntity.ok(response);
    }
}
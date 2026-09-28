package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.OfertaResponse;
import com.grupo21.rescuesync.service.OfertaService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotes/{loteId}/ofertas")
@RequiredArgsConstructor
public class OfertaController {

    private final OfertaService ofertaService;

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
}
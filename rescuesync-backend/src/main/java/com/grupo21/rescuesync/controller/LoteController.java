package com.grupo21.rescuesync.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grupo21.rescuesync.dto.CrearLoteRequest;
import com.grupo21.rescuesync.dto.LoteResponse;
import com.grupo21.rescuesync.service.LoteService;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/emergencias/{emergenciaId}/lotes")
@RequiredArgsConstructor
public class LoteController {
    private final LoteService loteService;

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
}
package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.compromisos.CrearCompromisoRequest;
import com.grupo21.sistemanacional.dto.compromisos.CrearCompromisoResponse;
import com.grupo21.sistemanacional.service.CompromisoService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compromisos")
@RequiredArgsConstructor
public class CompromisoController {

    private final CompromisoService compromisoService;

    @Operation(summary = "Comprometer recursos con control de concurrencia")
    @PreAuthorize("hasRole('SERVICIO')")
    @PostMapping
    public ResponseEntity<CrearCompromisoResponse> comprometer(
            @Valid @RequestBody CrearCompromisoRequest request
    ) {
        CrearCompromisoResponse response =
                compromisoService.comprometer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
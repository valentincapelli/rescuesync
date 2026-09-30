package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.dto.BonitaEstadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bonita")
@RestController
@RequestMapping("/api/bonita")
@RequiredArgsConstructor
public class BonitaController {

    private final BonitaClient bonitaClient;

    @Operation(summary = "Verifica que el backend puede autenticarse contra Bonita y encontrar el proceso configurado")
    @GetMapping("/estado")
    public BonitaEstadoResponse estado() {
        return bonitaClient.verificarConexion();
    }
}

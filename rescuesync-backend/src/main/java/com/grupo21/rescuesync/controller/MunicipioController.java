package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.MunicipioResponse;
import com.grupo21.rescuesync.service.MunicipioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Municipios")
@RestController
@RequestMapping(value = "/api/municipios", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MunicipioController {

    private final MunicipioService municipioService;

    @Operation(summary = "Listar municipios",
            description = "Id y nombre, ordenados por nombre. Para listas de selección, por ejemplo el filtro "
                    + "por municipio del dashboard de indicadores.")
    @PreAuthorize("hasAnyAuthority('CONSULTAR_INDICADORES', 'CONSULTAR_EMERGENCIAS')")
    @GetMapping
    public List<MunicipioResponse> listar() {
        return municipioService.listar();
    }
}

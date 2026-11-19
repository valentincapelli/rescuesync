package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.ofertas.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.EditarOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.OfertaResponse;
import com.grupo21.rescuesync.dto.ofertas.OfertasEvaluadasEmergenciaResponse;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasRequest;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasResponse;
import com.grupo21.rescuesync.service.OfertaService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OfertaController {

    private final OfertaService ofertaService;

    @PreAuthorize("hasAuthority('CREAR_OFERTA')")
    @PostMapping("/lotes/{loteId}/ofertas")
    public ResponseEntity<OfertaResponse> crear(
            @PathVariable Long loteId,
            @Valid @RequestBody CrearOfertaRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        OfertaResponse response =
                ofertaService.crear(loteId, request, userDetails.getUsername());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasAuthority('EDITAR_OFERTA')")
    @PutMapping("/lotes/{loteId}/ofertas/{ofertaId}")
    public ResponseEntity<OfertaResponse> editar(
            @PathVariable Long ofertaId,
            @Valid @RequestBody EditarOfertaRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        OfertaResponse response = ofertaService.editar(ofertaId, request, userDetails.getUsername());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CONSULTAR_OFERTAS_VALIDADAS_MUNICIPIO')")
    @GetMapping("/ofertas/evaluadas")
    public ResponseEntity<List<OfertasEvaluadasEmergenciaResponse>> obtenerOfertasEvaluadas(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<OfertasEvaluadasEmergenciaResponse> ofertas =
                ofertaService.obtenerOfertasEvaluadasDelMunicipio(
                        userDetails.getUsername()
                );

        return ResponseEntity.ok(ofertas);
    }

    @PreAuthorize("hasAuthority('SELECCIONAR_OFERTAS')")
    @PutMapping("/ofertas/seleccion")
    public ResponseEntity<SeleccionarOfertasResponse> seleccionarOfertas(
            @Valid @RequestBody SeleccionarOfertasRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        SeleccionarOfertasResponse response =
                ofertaService.seleccionarOfertas(
                        userDetails.getUsername(),
                        request
                );

        return ResponseEntity.ok(response);
    }
}
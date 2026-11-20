package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.ofertas.CrearOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.EditarOfertaRequest;
import com.grupo21.rescuesync.dto.ofertas.OfertaResponse;
import com.grupo21.rescuesync.dto.ofertas.OfertaPropiaResponse;
import com.grupo21.rescuesync.dto.ofertas.OfertasEvaluadasEmergenciaResponse;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasRequest;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasResponse;
import com.grupo21.rescuesync.dto.ofertas.VersionOfertaResponse;
import com.grupo21.rescuesync.service.OfertaService;
import com.grupo21.rescuesync.service.ValidacionOfertasService;

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
    private final ValidacionOfertasService validacionOfertasService;

    @PreAuthorize("hasAuthority('EDITAR_OFERTA')")
    @GetMapping("/ofertas/mias")
    public List<OfertaPropiaResponse> listarPropias(@AuthenticationPrincipal UserDetails usuario) {
        return ofertaService.listarPropias(usuario.getUsername());
    }

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
            @PathVariable Long loteId,
            @PathVariable Long ofertaId,
            @Valid @RequestBody EditarOfertaRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        OfertaResponse response = ofertaService.editar(loteId, ofertaId, request, userDetails.getUsername());

        return ResponseEntity.ok(response);
    }

    /** Historial de versiones: alta y cada edición con fecha, autor y valores anteriores. */
    @PreAuthorize("hasAuthority('CONSULTAR_OFERTAS')")
    @GetMapping("/lotes/{loteId}/ofertas/{ofertaId}/versiones")
    public List<VersionOfertaResponse> listarVersiones(
            @PathVariable Long loteId,
            @PathVariable Long ofertaId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ofertaService.listarVersiones(loteId, ofertaId, userDetails.getUsername());
    }

    @PreAuthorize("hasAuthority('CONSULTAR_OFERTAS_VALIDADAS_MUNICIPIO')")
    @GetMapping("/ofertas/evaluadas")
    public ResponseEntity<List<OfertasEvaluadasEmergenciaResponse>> obtenerOfertasEvaluadas(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // Trae de Bonita el resultado de "Evaluar ofertas" antes de listar (ofertas PENDIENTE -> EVALUADA).
        validacionOfertasService.sincronizarMunicipioDelUsuario(userDetails.getUsername());
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

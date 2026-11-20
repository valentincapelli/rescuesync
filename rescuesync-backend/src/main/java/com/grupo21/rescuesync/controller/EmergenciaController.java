package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.CrearEmergenciaRequest;
import com.grupo21.rescuesync.dto.CoberturaResponse;
import com.grupo21.rescuesync.dto.ResolverCoberturaRequest;
import com.grupo21.rescuesync.model.OperacionCobertura;
import com.grupo21.rescuesync.service.CoberturaService;
import com.grupo21.rescuesync.dto.DecidirCoberturaRequest;
import com.grupo21.rescuesync.dto.EmergenciaResponse;
import com.grupo21.rescuesync.dto.LoteOfertasConsolidadasResponse;
import com.grupo21.rescuesync.service.EmergenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final CoberturaService coberturaService;

    @Operation(summary = "Registrar una nueva emergencia")
    @PreAuthorize("hasAuthority('REGISTRAR_EMERGENCIA')")
    @ApiResponse(responseCode = "201", description = "Emergencia registrada")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergenciaResponse> crear(
        @Valid @RequestBody CrearEmergenciaRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        EmergenciaResponse response = emergenciaService.crear(request, userDetails.getUsername());

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
    @PreAuthorize("hasAuthority('PUBLICAR_CONVOCATORIA')")
    @ApiResponse(responseCode = "200", description = "Convocatoria publicada")
    @ApiResponse(responseCode = "409", description = "Sin lotes o convocatoria ya publicada")
    @PostMapping("/{id}/convocatoria")
    public ResponseEntity<EmergenciaResponse> publicarConvocatoria(@PathVariable Long id) {
        if (coberturaService.consultar(id).republicacionPendiente()) {
            return respuestaCobertura(coberturaService.ejecutar(id, OperacionCobertura.REPUBLICAR, null, null));
        }
        return ResponseEntity.ok(emergenciaService.publicarConvocatoria(id));
    }

    @Operation(summary = "Registrar la decisión de cobertura de lotes",
            description = "COMPLETA exige todos los lotes cubiertos (emergencia a COBERTURA_EVALUADA); "
                    + "PARCIAL exige al menos uno sin cubrir (COBERTURA_PARCIAL); SIN_COBERTURA reabre la "
                    + "convocatoria mediante una nueva publicación. Requiere que venza el plazo. "
                    + "202 indica que la operación sigue pendiente de confirmación en Bonita.")
    @PreAuthorize("hasAuthority('EVALUAR_COBERTURA')")
    @ApiResponse(responseCode = "200", description = "Decisión registrada")
    @ApiResponse(responseCode = "409", description = "Estado inválido o decisión inconsistente con las ofertas")
    @PostMapping(value = "/{id}/cobertura", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergenciaResponse> decidirCobertura(
            @PathVariable Long id,
            @Valid @RequestBody DecidirCoberturaRequest request
    ) {
        return respuestaCobertura(coberturaService.ejecutar(id, OperacionCobertura.EVALUAR, request.decision(), null));
    }

    @PreAuthorize("hasAuthority('EVALUAR_COBERTURA')")
    @GetMapping("/{id}/cobertura")
    public CoberturaResponse consultarCobertura(@PathVariable Long id) {
        return coberturaService.consultar(id);
    }

    @PreAuthorize("hasAuthority('EVALUAR_COBERTURA')")
    @PostMapping("/{id}/cobertura/resolucion")
    public ResponseEntity<EmergenciaResponse> resolverCobertura(@PathVariable Long id,
            @Valid @RequestBody ResolverCoberturaRequest request) {
        return respuestaCobertura(coberturaService.ejecutar(id, OperacionCobertura.RESOLVER, null, request.observacion()));
    }

    @PreAuthorize("hasAuthority('EVALUAR_COBERTURA')")
    @PostMapping("/{id}/cobertura/sincronizar")
    public CoberturaResponse sincronizarCobertura(@PathVariable Long id) {
        return coberturaService.sincronizar(id);
    }

    private ResponseEntity<EmergenciaResponse> respuestaCobertura(CoberturaResponse resultado) {
        return ResponseEntity.status(resultado.sincronizacionPendiente() ? 202 : 200).body(resultado.emergencia());
    }

    @Operation(summary = "Obtener una emergencia por id")
    @PreAuthorize("hasAuthority('CONSULTAR_EMERGENCIAS')")
    @GetMapping("/{id}")
    public EmergenciaResponse obtener(@PathVariable Long id) {
        return emergenciaService.obtener(id);
    }

    @Operation(summary = "Obtener las ofertas consolidadas por lote de una emergencia")
    @ApiResponse(responseCode = "200", description = "Lotes con sus ofertas y total ofrecido")
    @ApiResponse(responseCode = "404", description = "Emergencia inexistente")
    @PreAuthorize("hasAnyAuthority('CONSULTAR_EMERGENCIAS', 'CONSULTAR_OFERTAS_CONSOLIDADAS')")
    @GetMapping("/{emergenciaId}/ofertas-consolidadas")
    public List<LoteOfertasConsolidadasResponse> ofertasConsolidadas(@PathVariable Long emergenciaId) {
        return emergenciaService.ofertasConsolidadas(emergenciaId);
    }
}

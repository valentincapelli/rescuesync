package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.despliegues.DespliegueResponse;
import com.grupo21.rescuesync.dto.despliegues.SeleccionOfertaEstadoResponse;
import com.grupo21.rescuesync.dto.despliegues.RegistrarSeguimientoRequest;
import com.grupo21.rescuesync.model.TipoNovedadDespliegue;
import com.grupo21.rescuesync.service.despliegues.DespliegueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DespliegueController {
    private final DespliegueService servicio;

    @GetMapping("/emergencias/{id}/selecciones")
    @PreAuthorize("hasAuthority('MONITOREAR_DESPLIEGUE')")
    public List<SeleccionOfertaEstadoResponse> selecciones(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return servicio.listarSelecciones(id, user.getUsername());
    }

    @GetMapping("/emergencias/{id}/despliegues")
    @PreAuthorize("hasAuthority('MONITOREAR_DESPLIEGUE')")
    public List<DespliegueResponse> listar(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return servicio.listar(id, user.getUsername(), false);
    }

    @PostMapping("/emergencias/{id}/despliegues/sincronizar")
    @PreAuthorize("hasAuthority('MONITOREAR_DESPLIEGUE')")
    public List<DespliegueResponse> actualizar(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return servicio.listar(id, user.getUsername(), true);
    }

    @GetMapping("/actividades/mias")
    @PreAuthorize("hasAuthority('FINALIZAR_ACTIVIDAD')")
    public List<DespliegueResponse> propias(@AuthenticationPrincipal UserDetails user) {
        return servicio.propias(user.getUsername(), false);
    }

    @PostMapping("/actividades/mias/sincronizar")
    @PreAuthorize("hasAuthority('FINALIZAR_ACTIVIDAD')")
    public List<DespliegueResponse> actualizarPropias(@AuthenticationPrincipal UserDetails user) {
        return servicio.propias(user.getUsername(), true);
    }

    @PostMapping("/ofertas/{id}/seguimientos")
    @PreAuthorize("hasAuthority('MONITOREAR_DESPLIEGUE')")
    public ResponseEntity<DespliegueResponse> novedad(@PathVariable Long id,
            @Valid @RequestBody RegistrarSeguimientoRequest body, @AuthenticationPrincipal UserDetails user) {
        return respuesta(servicio.registrar(id, user.getUsername(), TipoNovedadDespliegue.NOVEDAD, body.observacion()));
    }

    @PostMapping("/ofertas/{id}/monitoreo/completar")
    @PreAuthorize("hasAuthority('MONITOREAR_DESPLIEGUE')")
    public ResponseEntity<DespliegueResponse> monitorear(@PathVariable Long id,
            @Valid @RequestBody RegistrarSeguimientoRequest body, @AuthenticationPrincipal UserDetails user) {
        return respuesta(servicio.registrar(id, user.getUsername(), TipoNovedadDespliegue.MONITOREO_COMPLETADO, body.observacion()));
    }

    @PostMapping("/ofertas/{id}/finalizacion")
    @PreAuthorize("hasAuthority('FINALIZAR_ACTIVIDAD')")
    public ResponseEntity<DespliegueResponse> finalizar(@PathVariable Long id,
            @Valid @RequestBody RegistrarSeguimientoRequest body, @AuthenticationPrincipal UserDetails user) {
        return respuesta(servicio.registrar(id, user.getUsername(), TipoNovedadDespliegue.FINALIZACION, body.observacion()));
    }

    private ResponseEntity<DespliegueResponse> respuesta(DespliegueResponse resultado) {
        return ResponseEntity.status(resultado.pendienteSincronizacion() ? 202 : 200).body(resultado);
    }
}

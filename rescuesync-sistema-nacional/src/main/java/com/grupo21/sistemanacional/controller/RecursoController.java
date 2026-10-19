package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.recursos.ActualizarRecursoRequest;
import com.grupo21.sistemanacional.dto.recursos.CrearRecursoRequest;
import com.grupo21.sistemanacional.dto.recursos.RecursoResponse;
import com.grupo21.sistemanacional.model.TipoRecurso;
import com.grupo21.sistemanacional.repository.DisponibilidadView;
import com.grupo21.sistemanacional.service.RecursoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Recursos")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecursoController {

    private final RecursoService recursoService;

    @Operation(summary = "Listar el inventario de una ONG",
            description = "ADMIN y SERVICIO ven cualquiera; una ONG solo el propio.")
    @GetMapping("/ongs/{ongId}/recursos")
    public List<RecursoResponse> listar(@PathVariable Long ongId) {
        return recursoService.listar(ongId);
    }

    @Operation(summary = "Agregar un recurso al inventario (solo el representante de esa ONG)",
            description = "Una fila por tipo de recurso y unidad. La unidad se guarda en minúsculas y sin espacios de más.")
    @PreAuthorize("hasRole('ONG')")
    @PostMapping("/ongs/{ongId}/recursos")
    @ResponseStatus(HttpStatus.CREATED)
    public RecursoResponse crear(@PathVariable Long ongId, @Valid @RequestBody CrearRecursoRequest request) {
        return recursoService.crear(ongId, request);
    }

    @Operation(summary = "Cambiar el stock total de un recurso (solo el representante de esa ONG)",
            description = "No puede quedar por debajo de lo ya comprometido.")
    @PreAuthorize("hasRole('ONG')")
    @PutMapping("/ongs/{ongId}/recursos/{recursoId}")
    public RecursoResponse actualizar(@PathVariable Long ongId, @PathVariable Long recursoId,
                                      @Valid @RequestBody ActualizarRecursoRequest request) {
        return recursoService.actualizar(ongId, recursoId, request);
    }

    @Operation(summary = "Eliminar un recurso (solo el representante de esa ONG)",
            description = "No se puede si tiene unidades comprometidas o historial de compromisos.")
    @PreAuthorize("hasRole('ONG')")
    @DeleteMapping("/ongs/{ongId}/recursos/{recursoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long ongId, @PathVariable Long recursoId) {
        recursoService.eliminar(ongId, recursoId);
    }

    @Operation(summary = "Disponibilidad global de recursos",
            description = "Suma de lo disponible (total - comprometido) de las ONGs habilitadas, por tipo y unidad.")
    @GetMapping("/recursos/disponibilidad")
    public List<DisponibilidadView> disponibilidadGlobal(@RequestParam(required = false) TipoRecurso tipoRecurso) {
        return recursoService.disponibilidadGlobal(tipoRecurso);
    }
}

package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.indicadores.EstadoProyectosResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.FormatoReporte;
import com.grupo21.rescuesync.dto.indicadores.ReporteIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.ResumenIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministrosMovilizadosResponse;
import com.grupo21.rescuesync.dto.indicadores.TiemposEtapasResponse;
import com.grupo21.rescuesync.exception.FuncionalidadPendienteException;
import com.grupo21.rescuesync.exception.ParametroInvalidoException;
import com.grupo21.rescuesync.service.indicadores.IndicadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Dashboard de indicadores para el personal directivo (E4). Contrato en docs/e4-indicadores-contrato.md.
 * Todos los endpoints aceptan los filtros de {@link FiltroIndicadores} como query params.
 */
@Tag(name = "Indicadores")
@RestController
@RequestMapping("/api/indicadores")
@PreAuthorize("hasAuthority('CONSULTAR_INDICADORES')")
@ApiResponse(responseCode = "400", description = "Filtro inválido (fecha mal formada, enum inexistente, desde > hasta)")
@ApiResponse(responseCode = "401", description = "Sin sesión")
@ApiResponse(responseCode = "403", description = "El rol no tiene CONSULTAR_INDICADORES")
@RequiredArgsConstructor
public class IndicadorController {

    private final IndicadorService indicadorService;

    @Operation(summary = "KPIs principales (datos propios + Bonita)")
    @GetMapping(value = "/resumen", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResumenIndicadoresResponse resumen(@ParameterObject FiltroIndicadores filtro) {
        return indicadorService.resumen(filtro);
    }

    @Operation(summary = "Suministros movilizados por mes, tipo de recurso y unidad")
    @GetMapping(value = "/suministros", produces = MediaType.APPLICATION_JSON_VALUE)
    public SuministrosMovilizadosResponse suministros(@ParameterObject FiltroIndicadores filtro) {
        return indicadorService.suministros(filtro);
    }

    @Operation(summary = "Porcentaje de lotes resueltos mediante consorcios de ONGs")
    @GetMapping(value = "/consorcios", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResolucionConsorciosResponse consorcios(@ParameterObject FiltroIndicadores filtro) {
        return indicadorService.consorcios(filtro);
    }

    @Operation(summary = "Estado de los proyectos (emergencias) y su detalle")
    @GetMapping(value = "/proyectos", produces = MediaType.APPLICATION_JSON_VALUE)
    public EstadoProyectosResponse proyectos(@ParameterObject FiltroIndicadores filtro) {
        return indicadorService.proyectos(filtro);
    }

    @Operation(summary = "Tiempos de resolución por etapa de la emergencia (API de Bonita)",
            description = "Si Bonita no está disponible responde 200 con bonita.disponible = false.")
    @GetMapping(value = "/tiempos-etapas", produces = MediaType.APPLICATION_JSON_VALUE)
    public TiemposEtapasResponse tiemposEtapas(@ParameterObject FiltroIndicadores filtro) {
        return indicadorService.tiemposEtapas(filtro);
    }

    @Operation(summary = "Reporte completo exportable",
            description = "formato=json devuelve ReporteIndicadoresResponse. csv y xlsx se descargan como archivo "
                    + "(hoy responden 501 hasta la etapa de integración).")
    @ApiResponse(responseCode = "501", description = "Formato todavía no implementado")
    @GetMapping("/reporte")
    public ResponseEntity<ReporteIndicadoresResponse> reporte(
            @ParameterObject FiltroIndicadores filtro,
            @Parameter(description = "json | csv | xlsx") @RequestParam(defaultValue = "json") String formato
    ) {
        FormatoReporte formatoReporte = parsearFormato(formato);
        if (formatoReporte != FormatoReporte.JSON) {
            throw new FuncionalidadPendienteException("La exportación en " + formatoReporte + " todavía no está implementada");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(indicadorService.reporte(filtro));
    }

    private static FormatoReporte parsearFormato(String formato) {
        try {
            return FormatoReporte.valueOf(formato.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ParametroInvalidoException("Formato '" + formato + "' no soportado: usar json, csv o xlsx");
        }
    }
}

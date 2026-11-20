package com.grupo21.rescuesync;

import com.grupo21.rescuesync.dto.indicadores.CantidadPorEstadoResponse;
import com.grupo21.rescuesync.dto.indicadores.EstadoFuenteBonita;
import com.grupo21.rescuesync.dto.indicadores.EstadoProyectosResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ProyectoResumenResponse;
import com.grupo21.rescuesync.dto.indicadores.ReporteIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.ResumenIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministroMensualResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministrosMovilizadosResponse;
import com.grupo21.rescuesync.dto.indicadores.TotalSuministroResponse;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoDesastre;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.service.indicadores.ExportadorReporte;
import com.grupo21.rescuesync.service.indicadores.TiemposBonitaService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Formato de las exportaciones CSV y XLSX (sin Spring). */
class ExportadorReporteTests {

    private static final Instant REGISTRO = Instant.parse("2026-10-05T15:00:00Z"); // 12:00 en Argentina

    private final ExportadorReporte exportador = new ExportadorReporte();

    @Test
    void csvUsaPuntoYComaComaDecimalYEscapaTextos() {
        String csv = new String(exportador.csv(reporte()), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("% finalizados;66,7\r\n");
        assertThat(csv).contains("Datos de Bonita disponibles;No\r\n");
        assertThat(csv).contains("Motivo (Bonita);Bonita caído\r\n");
        assertThat(csv).contains("2026-10;ALIMENTOS;kg;55;3\r\n");
        // Texto con ; y comillas: entre comillas, con las comillas duplicadas.
        assertThat(csv).contains(";\"Zona \"\"norte\"\"; ribera\";");
        // Texto que empieza con = : se neutraliza para que Excel no lo tome como fórmula
        // (y, como tiene comillas, además va entrecomillado).
        assertThat(csv).contains(";\"'=HYPERLINK(\"\"x\"\")\";");
        // Fecha en hora de Argentina y celda vacía para null.
        assertThat(csv).contains(";2026-10-05 12:00;;");
    }

    @Test
    void xlsxEscribeNumerosComoNumerosYUnaHojaPorSeccion() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportador.xlsx(reporte())))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(9);

            Sheet resumen = libro.getSheet("Resumen");
            assertThat(resumen.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Indicador");
            assertThat(resumen.getRow(4).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(resumen.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(66.7);

            Sheet proyectos = libro.getSheet("Proyectos");
            assertThat(proyectos.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(7);
            assertThat(proyectos.getRow(1).getCell(6).getLocalDateTimeCellValue().toString()).isEqualTo("2026-10-05T12:00");
            assertThat(proyectos.getRow(2).getCell(4).getStringCellValue()).isEqualTo("'=HYPERLINK(\"x\")");
        }
    }

    @Test
    void nombreArchivoUsaElRangoDelFiltro() {
        FiltroIndicadores filtro = new FiltroIndicadores(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"),
                null, null, null);
        assertThat(ExportadorReporte.nombreArchivo(filtro, "csv")).isEqualTo("indicadores_2026-01-01_2026-06-30.csv");
    }

    private static ReporteIndicadoresResponse reporte() {
        FiltroIndicadores filtro = new FiltroIndicadores(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-30"),
                null, TipoDesastre.INUNDACION, null);
        EstadoFuenteBonita bonita = new EstadoFuenteBonita(false, "Bonita caído", REGISTRO);
        ResumenIndicadoresResponse resumen = new ResumenIndicadoresResponse(REGISTRO, filtro, 3, 1, 2, 66.7,
                2, 50.0, null, bonita);
        SuministrosMovilizadosResponse suministros = new SuministrosMovilizadosResponse(
                List.of(new SuministroMensualResponse("2026-10", TipoRecurso.ALIMENTOS, "kg", 55, 3)),
                List.of(new TotalSuministroResponse(TipoRecurso.ALIMENTOS, "kg", 55, 3)));
        ResolucionConsorciosResponse consorcios = new ResolucionConsorciosResponse(2, 1, 1, 50.0, 2.0, List.of());
        EstadoProyectosResponse proyectos = new EstadoProyectosResponse(3, 2, 1, 66.7,
                List.of(new CantidadPorEstadoResponse(EstadoEmergencia.CERRADA, 2)),
                List.of(
                        new ProyectoResumenResponse(7L, TipoDesastre.INUNDACION, NivelGravedad.ALTO, "La Plata",
                                "Zona \"norte\"; ribera", EstadoEmergencia.CERRADA, REGISTRO, null, 2, 66.7, null),
                        new ProyectoResumenResponse(8L, TipoDesastre.INUNDACION, NivelGravedad.BAJO, "Berisso",
                                "=HYPERLINK(\"x\")", EstadoEmergencia.REGISTRADA, REGISTRO, null, 0, null, 15L)));
        return new ReporteIndicadoresResponse(resumen, suministros, consorcios, proyectos,
                TiemposBonitaService.sinDatos(bonita, 0));
    }
}

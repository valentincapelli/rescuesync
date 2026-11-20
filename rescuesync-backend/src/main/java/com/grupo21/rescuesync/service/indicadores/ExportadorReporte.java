package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ReporteIndicadoresResponse;
import com.grupo21.rescuesync.service.indicadores.ReporteTabular.Seccion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Exporta el reporte de indicadores a CSV y XLSX a partir de {@link ReporteTabular}.
 *
 * <p><b>CSV:</b> pensado para abrirse con Excel en configuración regional de Argentina. Usa
 * separador {@code ;}, coma decimal, UTF-8 con BOM y fin de línea CRLF. Cada sección empieza con una
 * línea {@code # Título}, sigue el encabezado y las filas, y termina con una línea vacía. Las fechas
 * van como {@code yyyy-MM-dd HH:mm} en hora de Argentina. Para datos procesables por programa,
 * usar {@code formato=json}.
 *
 * <p><b>XLSX:</b> una hoja por sección, con encabezado en negrita y fila fija, números como celdas
 * numéricas y fechas como celdas de fecha.
 *
 * <p>Los textos que empiezan con {@code = + - @} se prefijan con un apóstrofo, para que Excel no los
 * interprete como fórmulas (por ejemplo, una zona afectada cargada por un usuario).
 */
@Component
public class ExportadorReporte {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String BOM = "﻿";
    private static final char SEPARADOR = ';';
    private static final String FIN_LINEA = "\r\n";
    private static final int ANCHO_MAXIMO_COLUMNA = 60;

    // ------------------------------------------------------------------ CSV

    public byte[] csv(ReporteIndicadoresResponse reporte) {
        DecimalFormat decimal = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        StringBuilder sb = new StringBuilder(BOM);
        for (Seccion seccion : ReporteTabular.desde(reporte)) {
            sb.append(campoCsv("# " + seccion.titulo())).append(FIN_LINEA);
            lineaCsv(sb, List.copyOf(seccion.encabezados()), decimal);
            for (List<Object> fila : seccion.filas()) {
                lineaCsv(sb, fila, decimal);
            }
            sb.append(FIN_LINEA);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void lineaCsv(StringBuilder sb, List<?> celdas, DecimalFormat decimal) {
        for (int i = 0; i < celdas.size(); i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(campoCsv(textoCsv(celdas.get(i), decimal)));
        }
        sb.append(FIN_LINEA);
    }

    private static String textoCsv(Object valor, DecimalFormat decimal) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Double || valor instanceof Float) {
            return decimal.format(((Number) valor).doubleValue());
        }
        if (valor instanceof Number) {
            return valor.toString();
        }
        if (valor instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        if (valor instanceof Instant instant) {
            return FECHA.format(enArgentina(instant));
        }
        if (valor instanceof Enum<?> e) {
            return e.name();
        }
        return neutralizarFormula(valor.toString());
    }

    /** Entrecomilla si hace falta (separador, comillas, saltos de línea o espacios en los bordes). */
    private static String campoCsv(String texto) {
        boolean requiereComillas = texto.indexOf(SEPARADOR) >= 0 || texto.indexOf('"') >= 0
                || texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0
                || (!texto.isEmpty() && (Character.isWhitespace(texto.charAt(0))
                        || Character.isWhitespace(texto.charAt(texto.length() - 1))));
        return requiereComillas ? '"' + texto.replace("\"", "\"\"") + '"' : texto;
    }

    // ------------------------------------------------------------------ XLSX

    public byte[] xlsx(ReporteIndicadoresResponse reporte) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            for (Seccion seccion : ReporteTabular.desde(reporte)) {
                escribirHoja(libro, estilos, seccion);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el XLSX del reporte", ex);
        }
    }

    private static void escribirHoja(Workbook libro, Estilos estilos, Seccion seccion) {
        Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(seccion.titulo()));
        int columnas = seccion.encabezados().size();
        int[] anchos = new int[columnas];

        Row encabezado = hoja.createRow(0);
        for (int c = 0; c < columnas; c++) {
            Cell celda = encabezado.createCell(c);
            celda.setCellValue(seccion.encabezados().get(c));
            celda.setCellStyle(estilos.encabezado);
            anchos[c] = seccion.encabezados().get(c).length();
        }

        int r = 1;
        for (List<Object> fila : seccion.filas()) {
            Row row = hoja.createRow(r++);
            for (int c = 0; c < columnas; c++) {
                anchos[c] = Math.max(anchos[c], escribirCelda(row.createCell(c), fila.get(c), estilos));
            }
        }

        hoja.createFreezePane(0, 1);
        for (int c = 0; c < columnas; c++) {
            hoja.setColumnWidth(c, (Math.min(anchos[c], ANCHO_MAXIMO_COLUMNA) + 2) * 256);
        }
    }

    /** Escribe el valor y devuelve un ancho aproximado en caracteres. */
    private static int escribirCelda(Cell celda, Object valor, Estilos estilos) {
        if (valor == null) {
            return 0;
        }
        if (valor instanceof Double || valor instanceof Float) {
            celda.setCellValue(((Number) valor).doubleValue());
            celda.setCellStyle(estilos.decimal);
            return valor.toString().length();
        }
        if (valor instanceof Number n) {
            celda.setCellValue(n.doubleValue());
            return valor.toString().length();
        }
        if (valor instanceof Boolean b) {
            celda.setCellValue(b ? "Sí" : "No");
            return 2;
        }
        if (valor instanceof Instant instant) {
            celda.setCellValue(enArgentina(instant));
            celda.setCellStyle(estilos.fecha);
            return 16;
        }
        String texto = valor instanceof Enum<?> e ? e.name() : neutralizarFormula(valor.toString());
        celda.setCellValue(texto);
        return texto.length();
    }

    private static final class Estilos {
        final CellStyle encabezado;
        final CellStyle decimal;
        final CellStyle fecha;

        Estilos(Workbook libro) {
            CreationHelper helper = libro.getCreationHelper();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            encabezado = libro.createCellStyle();
            encabezado.setFont(negrita);
            decimal = libro.createCellStyle();
            decimal.setDataFormat(helper.createDataFormat().getFormat("0.0"));
            fecha = libro.createCellStyle();
            fecha.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        }
    }

    // ------------------------------------------------------------------ comunes

    /** Nombre de archivo para la descarga: indicadores_&lt;desde&gt;_&lt;hasta&gt;.&lt;ext&gt;. */
    public static String nombreArchivo(FiltroIndicadores filtro, String extension) {
        return "indicadores_" + filtro.desde() + "_" + filtro.hasta() + "." + extension;
    }

    private static LocalDateTime enArgentina(Instant instant) {
        return LocalDateTime.ofInstant(instant, FiltroIndicadores.ZONA);
    }

    private static String neutralizarFormula(String texto) {
        if (!texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0) {
            return "'" + texto;
        }
        return texto;
    }
}

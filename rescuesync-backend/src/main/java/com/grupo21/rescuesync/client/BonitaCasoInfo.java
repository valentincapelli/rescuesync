package com.grupo21.rescuesync.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Caso (instancia de proceso) de Bonita, activo ({@code GET /API/bpm/case/{id}}) o archivado
 * ({@code GET /API/bpm/archivedCase?f=sourceObjectId={id}}). Solo los campos que usan los indicadores.
 *
 * <p>Las fechas quedan como texto tal cual las manda Bonita (formato {@code yyyy-MM-dd HH:mm:ss.SSS},
 * o vacío si no aplica, como {@code end_date} de un caso activo). Se interpretan con
 * {@link FechasBonita}.
 *
 * @param id             id del registro. En un caso archivado es el id del archivo, no el del caso.
 * @param sourceObjectId solo en casos archivados: id que tenía el caso mientras estaba activo
 * @param state          estado del caso (por ejemplo, {@code started} o {@code completed})
 * @param start          inicio del caso
 * @param end_date       fin del caso; vacío o ausente mientras está activo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BonitaCasoInfo(
        String id,
        String sourceObjectId,
        String state,
        String start,
        String end_date
) {
}

package com.grupo21.rescuesync.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Registro de {@code GET /API/bpm/archivedActivity}. Una misma actividad puede tener varios
 * registros archivados (uno por cambio de estado) que comparten {@code sourceObjectId}.
 *
 * <p>Campos según la referencia de la REST API de Bonita (muestra de ArchivedActivity): {@code id},
 * {@code type}, {@code name}, {@code state}, {@code reached_state_date}, {@code sourceObjectId},
 * {@code archivedDate}, entre otros que se ignoran. Las fechas quedan como texto (ver {@link FechasBonita}).
 *
 * @param id                 id del registro archivado
 * @param sourceObjectId     id de la actividad mientras estaba activa (agrupa los registros)
 * @param name               nombre de la tarea en el diagrama
 * @param type               tipo de actividad (USER_TASK, AUTOMATIC_TASK, ...)
 * @param state              estado alcanzado en ese registro (ready, executing, completed, ...)
 * @param reached_state_date momento en que la actividad llegó a {@code state}
 * @param archivedDate       momento en que se archivó el registro
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BonitaActividadArchivada(
        String id,
        String sourceObjectId,
        String name,
        String type,
        String state,
        String reached_state_date,
        String archivedDate
) {
}

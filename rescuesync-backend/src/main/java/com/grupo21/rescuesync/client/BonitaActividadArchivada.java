package com.grupo21.rescuesync.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Registro de {@code GET /API/bpm/archivedFlowNode}. Una misma tarea tiene varios registros archivados
 * (uno por cambio de estado: {@code initializing}, {@code ready}, {@code completed}) que comparten
 * {@code sourceObjectId}. Verificado contra Bonita Runtime 10.4 el 18/11/2026.
 *
 * <p>Campos usados: {@code id}, {@code type}, {@code name}, {@code state}, {@code reached_state_date},
 * {@code sourceObjectId} y {@code archivedDate}; el resto se ignora. Las fechas quedan como texto (ver
 * {@link FechasBonita}).
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

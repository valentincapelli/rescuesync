package com.grupo21.rescuesync.model;

/**
 * Estado de una Oferta de ayuda cargada por una ONG para uno o varios lotes.
 *
 * PENDIENTE    -> Cargada por la ONG, ventana de convocatoria aún abierta.
 * EVALUADA     -> El Sistema Nacional devolvió perfil/habilitación de apoyo.
 * SELECCIONADA -> El Municipio la eligió para cubrir el lote.
 * RECHAZADA    -> No fue seleccionada por el Municipio.
 * COMPROMETIDA -> Recursos registrados como comprometidos en el Sistema Nacional.
 * FINALIZADA   -> La ONG informó la finalización de la actividad asignada.
 */
public enum EstadoOferta {
    PENDIENTE,
    EVALUADA,
    SELECCIONADA,
    RECHAZADA,
    COMPROMETIDA,
    FINALIZADA
}

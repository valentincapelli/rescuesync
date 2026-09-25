package com.grupo21.rescuesync.model;

/**
 * Estado de una Emergencia a lo largo del proceso RescueSync (ver modelo BPMN).
 *
 * REGISTRADA               -> Municipio registra la emergencia.
 * EN_REVISION               -> Centro Coordinador Regional la revisa y desglosa en lotes.
 * CONVOCATORIA_PUBLICADA    -> Lotes publicados, ventana de ofertas abierta.
 * COBERTURA_EVALUADA        -> Venció el timer, se evaluó la cobertura de lotes.
 * COBERTURA_PARCIAL         -> Se decidió continuar con cobertura parcial (lotes sin cubrir).
 * ADJUDICADA                -> Municipio seleccionó ofertas y notificó a las ONGs.
 * EN_EJECUCION               -> Recursos comprometidos, despliegue en curso (monitoreo CCR).
 * CERRADA                   -> Sistema Nacional liberó los recursos comprometidos.
 */
public enum EstadoEmergencia {
    REGISTRADA,
    EN_REVISION,
    CONVOCATORIA_PUBLICADA,
    COBERTURA_EVALUADA,
    COBERTURA_PARCIAL,
    ADJUDICADA,
    EN_EJECUCION,
    CERRADA
}

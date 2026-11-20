package com.grupo21.rescuesync.model;

/**
 * Decisión del Centro Coordinador en la tarea "Evaluar cobertura de lotes". Es el valor del
 * contrato {@code decisionCoberturaInput} de Bonita; cada una corresponde a una salida del
 * gateway "¿Lotes cubiertos?".
 *
 * COMPLETA      -> Todos los lotes alcanzan la cantidad requerida; sigue a "Evaluar ofertas".
 * PARCIAL       -> Hay lotes sin cubrir; sigue a "Resolver cobertura incompleta".
 * SIN_COBERTURA -> Se reabre la convocatoria; vuelve a "Publicar convocatoria".
 */
public enum DecisionCobertura {
    COMPLETA,
    PARCIAL,
    SIN_COBERTURA
}

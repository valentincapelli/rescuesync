package com.grupo21.rescuesync.model;

/**
 * Cómo se adapta una oferta a su lote según el perfil que devolvió el Sistema Nacional
 * (eslabón 4 del enunciado: lotes principales o de apoyo secundario, sin rechazo binario).
 *
 * PRINCIPAL        -> Certificación vigente con riesgo máximo igual o mayor a la gravedad de la emergencia.
 * APOYO            -> Certificación vigente con riesgo máximo menor a la gravedad: tareas de apoyo secundario.
 * SIN_HABILITACION -> Sin certificación vigente para el tipo de recurso o ONG no habilitada.
 *                     El municipio no la puede seleccionar (solo ve las ofertas validadas).
 */
public enum ClasificacionOferta {
    PRINCIPAL,
    APOYO,
    SIN_HABILITACION;

    public boolean esSeleccionable() {
        return this != SIN_HABILITACION;
    }
}

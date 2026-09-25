package com.grupo21.rescuesync.model;

/**
 * Estado de un Lote dentro de la convocatoria.
 *
 * PUBLICADO             -> Publicado en la convocatoria, recibiendo ofertas.
 * CUBIERTO              -> La cantidad requerida quedó cubierta por ofertas.
 * PARCIALMENTE_CUBIERTO -> Cobertura incompleta; se continúa igual (cobertura parcial).
 * REFORMULADO           -> El CCR lo redefinió tras una cobertura incompleta.
 * CERRADO               -> Oferta(s) seleccionadas y recursos comprometidos para este lote.
 */
public enum EstadoLote {
    PUBLICADO,
    CUBIERTO,
    PARCIALMENTE_CUBIERTO,
    REFORMULADO,
    CERRADO
}

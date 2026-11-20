package com.grupo21.rescuesync.dto.ofertas;

import com.grupo21.rescuesync.model.ClasificacionOferta;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.NivelCompetencia;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.ValidacionOferta;

import java.time.LocalDate;

/** Oferta evaluada con el perfil del Sistema Nacional, para la pantalla de selección del municipio. */
public record OfertaEvaluadaResponse(
        Long id,
        Long loteId,
        String ongNombre,
        Integer cantidadOfrecida,
        String observaciones,
        EstadoOferta estado,
        Validacion validacion
) {

    /** Null en datos incompletos que no tienen resultado de validación nacional. */
    public record Validacion(
            ClasificacionOferta clasificacion,
            NivelCompetencia nivelCompetencia,
            NivelGravedad nivelRiesgoMax,
            LocalDate certificacionVence,
            /** Motivo informativo; si lo ofrecido supera el stock nacional, explica por qué no se puede elegir. */
            String observaciones,
            /** Stock de la ONG en el Sistema Nacional al evaluar; null si no se informó. */
            Integer stockDisponible
    ) {}

    public static OfertaEvaluadaResponse de(Oferta oferta) {
        ValidacionOferta v = oferta.getValidacion();
        Validacion validacion = v == null || v.getClasificacion() == null ? null
                : new Validacion(v.getClasificacion(), v.getNivelCompetencia(), v.getNivelRiesgoMax(),
                        v.getCertificacionVence(), observaciones(oferta, v), v.getStockDisponible());
        return new OfertaEvaluadaResponse(oferta.getId(), oferta.getLote().getId(), oferta.getOng().getNombre(),
                oferta.getCantidadOfrecida(), oferta.getObservaciones(), oferta.getEstado(), validacion);
    }

    private static String observaciones(Oferta oferta, ValidacionOferta v) {
        if (v.stockInsuficiente(oferta.getCantidadOfrecida())) {
            return "Stock insuficiente en el Sistema Nacional: la ONG ofrece " + oferta.getCantidadOfrecida()
                    + " " + oferta.getLote().getUnidadMedida() + " pero tiene " + v.getStockDisponible()
                    + " disponibles.";
        }
        return v.getObservaciones();
    }

    /**
     * Se puede seleccionar si está validada (eslabón 5: solo ofertas validadas) y, cuando el Sistema Nacional
     * informó el stock, si lo ofrecido no lo supera: comprometerla fallaría y dejaría trabado el caso.
     */
    public boolean seleccionable() {
        return validacion != null && validacion.clasificacion() != null
                && validacion.clasificacion().esSeleccionable()
                && (validacion.stockDisponible() == null || cantidadOfrecida <= validacion.stockDisponible());
    }
}

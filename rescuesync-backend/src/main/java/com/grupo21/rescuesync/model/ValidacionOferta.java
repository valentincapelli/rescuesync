package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Resultado de la validación nacional de una oferta, tomado de la tarea "Evaluar ofertas" de Bonita
 * (variable {@code ofertasEvaluadasJson}). Se guarda en la oferta al pasar a {@link EstadoOferta#EVALUADA}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ValidacionOferta {

    @Enumerated(EnumType.STRING)
    @Column(name = "validacion_clasificacion", length = 20)
    private ClasificacionOferta clasificacion;

    /** Nivel de la capacidad usada para clasificar; null si no hay certificación vigente. */
    @Enumerated(EnumType.STRING)
    @Column(name = "validacion_nivel_competencia", length = 20)
    private NivelCompetencia nivelCompetencia;

    /** Riesgo máximo certificado. Usa la misma escala que la gravedad de la emergencia. */
    @Enumerated(EnumType.STRING)
    @Column(name = "validacion_nivel_riesgo_max", length = 20)
    private NivelGravedad nivelRiesgoMax;

    /** Vencimiento de la certificación; null si no vence o no hay certificación. */
    @Column(name = "validacion_certificacion_vence")
    private LocalDate certificacionVence;

    /** Observaciones del Sistema Nacional, por ejemplo el motivo de una capacidad vacía. */
    @Column(name = "validacion_observaciones", length = 500)
    private String observaciones;

    @Column(name = "validacion_evaluada_en")
    private Instant evaluadaEn;

    /**
     * Stock que la ONG tiene disponible en el Sistema Nacional, en la unidad del lote, al evaluar.
     * Null si el Sistema Nacional no lo informó (versiones anteriores) o la oferta no está habilitada.
     */
    @Column(name = "validacion_stock_disponible")
    private Integer stockDisponible;

    public ValidacionOferta(ClasificacionOferta clasificacion, NivelCompetencia nivelCompetencia,
                            NivelGravedad nivelRiesgoMax, LocalDate certificacionVence, String observaciones,
                            Instant evaluadaEn) {
        this(clasificacion, nivelCompetencia, nivelRiesgoMax, certificacionVence, observaciones, evaluadaEn, null);
    }

    /** True si se sabe que lo ofrecido supera el stock disponible: comprometerlo fallaría en el Sistema Nacional. */
    public boolean stockInsuficiente(int cantidadOfrecida) {
        return stockDisponible != null && cantidadOfrecida > stockDisponible;
    }
}

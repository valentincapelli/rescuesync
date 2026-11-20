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
}

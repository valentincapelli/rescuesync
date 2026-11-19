package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Seguimiento de la actividad adjudicada. El stock sigue siendo responsabilidad del Sistema Nacional. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "despliegues")
public class Despliegue extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false, unique = true)
    private Oferta oferta;
    @Column(nullable = false)
    private Long bonitaCaseId;
    @Column(nullable = false)
    private Instant comprometidoEn;
    private Instant monitoreadoEn;
    private Instant finalizadoEn;
    private Instant liberadoEn;
    private String tareaMonitoreoId;
    private String tareaFinalizacionId;
    @Column(nullable = false)
    private boolean monitoreoDisponible;
    @Column(nullable = false)
    private boolean finalizacionDisponible;
    private Instant sincronizadoEn;
}

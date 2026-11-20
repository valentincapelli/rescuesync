package com.grupo21.sistemanacional.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * Aval de una ONG para un tipo de recurso: con qué competencia lo ofrece y hasta qué riesgo puede actuar.
 * Es la base de la validación por niveles de competencia (no hay rechazo binario).
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "ong")
@Entity
@Table(name = "certificaciones",
        uniqueConstraints = @UniqueConstraint(name = "uk_certificaciones_ong_tipo",
                columnNames = {"ong_id", "tipo_recurso"}))
public class Certificacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ong_id", nullable = false)
    private Ong ong;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recurso", nullable = false, length = 30)
    private TipoRecurso tipoRecurso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_competencia", nullable = false, length = 20)
    private NivelCompetencia nivelCompetencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_riesgo_max", nullable = false, length = 20)
    private NivelGravedad nivelRiesgoMax;

    /** null = no vence. */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    public Certificacion(Ong ong, TipoRecurso tipoRecurso, NivelCompetencia nivelCompetencia,
                         NivelGravedad nivelRiesgoMax, LocalDate fechaVencimiento) {
        this.ong = ong;
        this.tipoRecurso = tipoRecurso;
        this.nivelCompetencia = nivelCompetencia;
        this.nivelRiesgoMax = nivelRiesgoMax;
        this.fechaVencimiento = fechaVencimiento;
    }

    public boolean isVigente(LocalDate hoy) {
        return fechaVencimiento == null || !fechaVencimiento.isBefore(hoy);
    }
}

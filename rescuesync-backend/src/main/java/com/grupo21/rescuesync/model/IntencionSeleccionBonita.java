package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Intención durable por oferta adjudicada; conserva el task ID para reconciliar envíos ambiguos. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "intenciones_seleccion_bonita", uniqueConstraints = {
        @UniqueConstraint(name = "uk_intencion_seleccion_oferta", columnNames = "oferta_id"),
        @UniqueConstraint(name = "uk_intencion_seleccion_task", columnNames = "bonita_task_id")
})
public class IntencionSeleccionBonita extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Oferta oferta;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoIntencionSeleccionBonita estado = EstadoIntencionSeleccionBonita.PENDIENTE;

    @Column(name = "bonita_case_id")
    private Long bonitaCaseId;

    @Column(name = "bonita_task_id", length = 100)
    private String bonitaTaskId;

    @Column(name = "continuar_seleccionando_input")
    private Boolean continuarSeleccionandoInput;

    @Column(name = "ultimo_intento_en")
    private Instant ultimoIntentoEn;

    @Column(name = "detalle_incidente", length = 500)
    private String detalleIncidente;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}

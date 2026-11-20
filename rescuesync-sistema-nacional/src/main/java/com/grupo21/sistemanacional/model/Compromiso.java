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
import lombok.ToString;
import org.hibernate.annotations.Check;

import java.time.Instant;

/**
 * Reserva de stock de un recurso para una oferta adjudicada de RescueSync.
 * emergenciaRef y ofertaRef son ids de la base de RescueSync: acá no se guardan emergencias ni ofertas.
 * <p>
 * Único por (recurso, oferta_ref): si Bonita reintenta el mismo compromiso, se devuelve el existente
 * en vez de duplicar (idempotencia).
 */
@Getter
@NoArgsConstructor
@ToString(exclude = "recurso")
@Entity
@Table(name = "compromisos",
        uniqueConstraints = @UniqueConstraint(name = "uk_compromisos_recurso_oferta",
                columnNames = {"recurso_id", "oferta_ref"}))
@Check(name = "ck_compromisos_cantidad", constraints = "cantidad > 0")
public class Compromiso extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

    @Column(name = "emergencia_ref", nullable = false)
    private Long emergenciaRef;

    @Column(name = "oferta_ref", nullable = false)
    private Long ofertaRef;

    @Column(nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCompromiso estado = EstadoCompromiso.COMPROMETIDO;

    @Column(name = "fecha_liberacion")
    private Instant fechaLiberacion;

    public Compromiso(Recurso recurso, Long emergenciaRef, Long ofertaRef, int cantidad) {
        this.recurso = recurso;
        this.emergenciaRef = emergenciaRef;
        this.ofertaRef = ofertaRef;
        this.cantidad = cantidad;
    }

    public boolean isLiberado() {
        return estado == EstadoCompromiso.LIBERADO;
    }

    /** Marca el compromiso como liberado. El stock lo devuelve el service con Recurso#liberar. */
    public void marcarLiberado(Instant cuando) {
        this.estado = EstadoCompromiso.LIBERADO;
        this.fechaLiberacion = cuando;
    }
}

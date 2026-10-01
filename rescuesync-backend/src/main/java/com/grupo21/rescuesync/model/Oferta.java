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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Oferta de ayuda cargada por una ONG para cubrir (total o parcialmente) un {@link Lote}
 * publicado en la convocatoria. Una ONG que quiera cubrir varios lotes carga una
 * Oferta por cada uno.
 */
@Getter
@Setter
@ToString(exclude = {"lote", "ong"})
@NoArgsConstructor
@Entity
@Table(
    name = "ofertas",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_oferta_ong_lote",
            columnNames = {"ong_id", "lote_id"}
        )
    }
)
public class Oferta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ong_id", nullable = false)
    private Ong ong;

    @Column(name = "cantidad_ofrecida", nullable = false)
    private Integer cantidadOfrecida;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoOferta estado = EstadoOferta.PENDIENTE;
}

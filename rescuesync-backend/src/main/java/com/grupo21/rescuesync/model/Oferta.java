package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Oferta de ayuda cargada por una ONG para cubrir (total o parcialmente) un {@link Lote}
 * publicado en la convocatoria. Una ONG que quiera cubrir varios lotes carga una
 * Oferta por cada uno.
 *
 * Nota: todavía no hay módulo de autenticación (E2-10), por eso la ONG se identifica
 * por nombre. Cuando exista el login de ONGs, {@code ongNombre} pasará a ser una
 * relación con la entidad Ong/Usuario correspondiente.
 */
@Getter
@Setter
@ToString(exclude = "lote")
@NoArgsConstructor
@Entity
@Table(name = "ofertas")
public class Oferta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Column(name = "ong_nombre", nullable = false, length = 150)
    private String ongNombre;

    @Column(name = "cantidad_ofrecida", nullable = false)
    private Integer cantidadOfrecida;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoOferta estado = EstadoOferta.PENDIENTE;
}

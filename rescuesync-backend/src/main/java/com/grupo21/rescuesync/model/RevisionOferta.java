package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Una edición de una {@link Oferta} durante la convocatoria: quién la hizo, cuándo ({@code createdAt})
 * y los valores antes y después. Las filas no se modifican ni se borran; son la trazabilidad de versiones
 * que pide el enunciado. La versión 1 es el alta de la oferta y no genera fila.
 */
@Getter
@Setter
@ToString(exclude = {"oferta", "autor"})
@NoArgsConstructor
@Entity
@Table(name = "revisiones_oferta", indexes = @Index(name = "idx_revision_oferta_oferta", columnList = "oferta_id"))
public class RevisionOferta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false, updatable = false)
    private Oferta oferta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Usuario autor;

    @Column(name = "cantidad_anterior", nullable = false, updatable = false)
    private Integer cantidadAnterior;

    @Column(name = "cantidad_nueva", nullable = false, updatable = false)
    private Integer cantidadNueva;

    @Column(name = "observaciones_anteriores", columnDefinition = "TEXT", updatable = false)
    private String observacionesAnteriores;

    @Column(name = "observaciones_nuevas", columnDefinition = "TEXT", updatable = false)
    private String observacionesNuevas;
}

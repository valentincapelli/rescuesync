package com.grupo21.rescuesync.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Lote de necesidades: porción cuantificada de recursos o personal en que el
 * Centro Coordinador Regional desglosa una {@link Emergencia}, publicado en la convocatoria
 * para que las ONGs carguen {@link Oferta}s.
 */
@Getter
@Setter
@ToString(exclude = {"emergencia", "ofertas"})
@NoArgsConstructor
@Entity
@Table(name = "lotes")
public class Lote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emergencia_id", nullable = false)
    private Emergencia emergencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recurso", nullable = false, length = 30)
    private TipoRecurso tipoRecurso;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "cantidad_requerida", nullable = false)
    private Integer cantidadRequerida;

    @Column(name = "unidad_medida", nullable = false, length = 30)
    private String unidadMedida;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoLote estado = EstadoLote.PUBLICADO;

    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<Oferta> ofertas = new ArrayList<>();

    public void addOferta(Oferta oferta) {
        ofertas.add(oferta);
        oferta.setLote(this);
    }
}

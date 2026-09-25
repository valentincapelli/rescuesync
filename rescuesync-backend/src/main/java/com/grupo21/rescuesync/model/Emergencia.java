package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Emergencia registrada por un Municipio. Es la raíz del proceso RescueSync:
 * a partir de ella el Centro Coordinador Regional desglosa {@link Lote}s de necesidades.
 */
@Getter
@Setter
@ToString(exclude = "lotes")
@NoArgsConstructor
@Entity
@Table(name = "emergencias")
public class Emergencia extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_desastre", nullable = false, length = 30)
    private TipoDesastre tipoDesastre;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_gravedad", nullable = false, length = 20)
    private NivelGravedad nivelGravedad;

    @Column(name = "zona_afectada", nullable = false, length = 200)
    private String zonaAfectada;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "municipio", nullable = false, length = 150)
    private String municipio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoEmergencia estado = EstadoEmergencia.REGISTRADA;

    /** Id de la instancia de proceso en Bonita una vez iniciada (E2-11/E2-12). Null hasta entonces. */
    @Column(name = "bonita_case_id")
    private Long bonitaCaseId;

    @OneToMany(mappedBy = "emergencia", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<Lote> lotes = new ArrayList<>();

    public void addLote(Lote lote) {
        lotes.add(lote);
        lote.setEmergencia(this);
    }
}

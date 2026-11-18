package com.grupo21.rescuesync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Un cambio de estado de una Emergencia, Lote u Oferta (E4: indicadores).
 *
 * <p>Permite responder "cuándo" pasó algo (por ejemplo, en qué mes se comprometió una oferta),
 * cosa que {@code updatedAt} no resuelve porque cambia con cualquier edición.
 *
 * <p>Sin FK a propósito: los lotes se pueden borrar durante el desglose y el historial
 * no debe impedirlo. {@code emergenciaId} se guarda siempre para filtrar sin joins.
 *
 * <p>Se escribe solamente a través de {@code HistorialEstadoService}. El alta de una entidad
 * queda registrada con {@code estadoAnterior = null}.
 */
@Getter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "historial_estados", indexes = {
        @Index(name = "ix_historial_entidad", columnList = "tipo_entidad, entidad_id"),
        @Index(name = "ix_historial_estado_fecha", columnList = "tipo_entidad, estado_nuevo, fecha_cambio"),
        @Index(name = "ix_historial_emergencia", columnList = "emergencia_id")
})
public class HistorialEstado extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidad", nullable = false, length = 20)
    private TipoEntidadHistorial tipoEntidad;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(name = "emergencia_id", nullable = false)
    private Long emergenciaId;

    /** Nombre del enum de estado anterior; {@code null} en el alta. */
    @Column(name = "estado_anterior", length = 30)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private String estadoNuevo;

    @Column(name = "fecha_cambio", nullable = false)
    private Instant fechaCambio;

    public HistorialEstado(TipoEntidadHistorial tipoEntidad, Long entidadId, Long emergenciaId,
                           Enum<?> estadoAnterior, Enum<?> estadoNuevo, Instant fechaCambio) {
        this.tipoEntidad = tipoEntidad;
        this.entidadId = entidadId;
        this.emergenciaId = emergenciaId;
        this.estadoAnterior = estadoAnterior == null ? null : estadoAnterior.name();
        this.estadoNuevo = estadoNuevo.name();
        this.fechaCambio = fechaCambio;
    }
}

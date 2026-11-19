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
import java.time.Instant;

/** Las acciones con tarea quedan pendientes hasta confirmar su ejecución en Bonita. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "novedades_despliegue")
public class NovedadDespliegue extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despliegue_id", nullable = false)
    private Despliegue despliegue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNovedadDespliegue tipo;
    @Column(nullable = false, length = 2000)
    private String observacion;
    @Column(unique = true)
    private String bonitaTaskId;
    private Instant confirmadaEn;
}

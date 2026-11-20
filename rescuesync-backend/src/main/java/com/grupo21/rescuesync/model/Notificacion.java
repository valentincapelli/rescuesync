package com.grupo21.rescuesync.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ong_id", nullable = false)
    private Ong ong;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    /** Nullable para conservar avisos históricos; los nuevos identifican su oferta sin interpretar el mensaje. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oferta_id", unique = true)
    private Oferta oferta;

    @Enumerated(EnumType.STRING)
    @Column(name = "paso_bonita", length = 20)
    private PasoNotificacionBonita pasoBonita;

    @Column(name = "bonita_case_id")
    private Long bonitaCaseId;

    @Column(name = "bonita_task_id", length = 100)
    private String bonitaTaskId;

    @Column(name = "bonita_ultimo_intento")
    private Instant bonitaUltimoIntento;

    @Column(name = "bonita_pendiente", nullable = false, columnDefinition = "boolean default false")
    private boolean bonitaPendiente;

    @Column(name = "notificada_en")
    private Instant notificadaEn;

    @Column(name = "recibida_en")
    private Instant recibidaEn;

    @OneToMany(
            mappedBy = "notificacion",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<NotificacionUsuario> destinatarios = new ArrayList<>();
}

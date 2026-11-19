package com.grupo21.rescuesync.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "notificaciones_usuarios",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notificacion_usuario",
                        columnNames = {"notificacion_id", "usuario_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificacionUsuario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notificacion_id", nullable = false)
    private Notificacion notificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private Instant fechaLectura;
}
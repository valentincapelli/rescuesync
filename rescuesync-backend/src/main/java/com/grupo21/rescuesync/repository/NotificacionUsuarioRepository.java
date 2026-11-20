package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.NotificacionUsuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionUsuarioRepository
        extends JpaRepository<NotificacionUsuario, Long> {

    List<NotificacionUsuario>
    findByUsuarioIdOrderByNotificacion_CreatedAtDesc(Long usuarioId);

    Optional<NotificacionUsuario>
    findByNotificacionIdAndUsuarioId(
            Long notificacionId,
            Long usuarioId
    );

    long countByUsuarioIdAndFechaLecturaIsNull(Long usuarioId);

    boolean existsByNotificacionIdAndFechaLecturaIsNotNull(Long notificacionId);
}

package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    Optional<Notificacion> findByOfertaId(Long ofertaId);

    List<Notificacion> findByOferta_Lote_Emergencia_IdAndBonitaPendienteTrue(Long emergenciaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notificacion n where n.id = :id")
    Optional<Notificacion> buscarConBloqueo(@Param("id") Long id);
}

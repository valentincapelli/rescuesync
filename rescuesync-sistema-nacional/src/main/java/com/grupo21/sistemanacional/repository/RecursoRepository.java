package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecursoRepository extends JpaRepository<Recurso, Long> {

    List<Recurso> findByOngIdOrderByTipoRecurso(Long ongId);

    Optional<Recurso> findByOngIdAndTipoRecursoAndUnidadMedida(Long ongId, TipoRecurso tipo, String unidadMedida);

    /**
     * Bloquea las filas (SELECT ... FOR UPDATE) hasta que termine la transacción.
     * Ordenado por id para que dos compromisos que tocan los mismos recursos los bloqueen
     * siempre en el mismo orden y no se produzca un deadlock (consorcios con varias ONGs).
     * Usar solo dentro de un método @Transactional.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recurso r where r.id in :ids order by r.id")
    List<Recurso> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** Igual que {@link #findAllByIdForUpdate} pero para un solo recurso. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recurso r where r.id = :id")
    Optional<Recurso> findByIdForUpdate(@Param("id") Long id);

    /** Disponibilidad global por tipo y unidad, solo de ONGs habilitadas. */
    @Query("""
            select new com.grupo21.sistemanacional.repository.DisponibilidadView(
                r.tipoRecurso, r.unidadMedida, sum(r.cantidadTotal - r.cantidadComprometida))
            from Recurso r
            where r.ong.estadoHabilitacion = com.grupo21.sistemanacional.model.EstadoHabilitacion.HABILITADA
            group by r.tipoRecurso, r.unidadMedida
            order by r.tipoRecurso, r.unidadMedida
            """)
    List<DisponibilidadView> disponibilidadGlobal();
}

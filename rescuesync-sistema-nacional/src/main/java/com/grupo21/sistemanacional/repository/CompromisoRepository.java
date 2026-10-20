package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.Compromiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompromisoRepository extends JpaRepository<Compromiso, Long> {

    /** Para la idempotencia: si ya existe, un reintento de Bonita devuelve este en vez de duplicar. */
    Optional<Compromiso> findByRecursoIdAndOfertaRef(Long recursoId, Long ofertaRef);

    /**
     * Para liberar: Bonita conoce emergencia y oferta, no el id interno del compromiso.
     * Puede devolver varios (oferta de un consorcio que compromete recursos de varias ONGs).
     */
    List<Compromiso> findByEmergenciaRefAndOfertaRef(Long emergenciaRef, Long ofertaRef);

    List<Compromiso> findByEmergenciaRef(Long emergenciaRef);

    /**
     * Ids de los recursos tocados por los compromisos de una emergencia, sin cargar los compromisos.
     * La liberación los usa para bloquear los recursos ANTES de leer los compromisos: así lee el estado
     * ya confirmado por cualquier liberación concurrente y no libera dos veces.
     */
    @Query("select distinct c.recurso.id from Compromiso c where c.emergenciaRef = :emergenciaRef")
    List<Long> findRecursoIdsByEmergenciaRef(@Param("emergenciaRef") Long emergenciaRef);

    @Query("""
            select distinct c.recurso.id from Compromiso c
            where c.emergenciaRef = :emergenciaRef and c.ofertaRef = :ofertaRef
            """)
    List<Long> findRecursoIdsByEmergenciaRefAndOfertaRef(@Param("emergenciaRef") Long emergenciaRef,
                                                         @Param("ofertaRef") Long ofertaRef);

    /** Para no borrar un recurso que tiene historial de compromisos. */
    boolean existsByRecursoId(Long recursoId);
}

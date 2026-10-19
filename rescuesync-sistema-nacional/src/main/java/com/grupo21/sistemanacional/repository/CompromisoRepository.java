package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.Compromiso;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** Para no borrar un recurso que tiene historial de compromisos. */
    boolean existsByRecursoId(Long recursoId);
}

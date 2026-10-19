package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.TipoRecurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CertificacionRepository extends JpaRepository<Certificacion, Long> {

    List<Certificacion> findByOngIdOrderByTipoRecurso(Long ongId);

    Optional<Certificacion> findByIdAndOngId(Long id, Long ongId);

    boolean existsByOngIdAndTipoRecurso(Long ongId, TipoRecurso tipoRecurso);

    /**
     * Certificación vigente de una ONG (por CUIT) para un tipo de recurso.
     * Base de la validación por niveles de competencia: si no hay, se devuelve capacidad vacía, no un rechazo.
     */
    @Query("""
            select c from Certificacion c
            where c.ong.cuit = :cuit
              and c.tipoRecurso = :tipo
              and (c.fechaVencimiento is null or c.fechaVencimiento >= :hoy)
            """)
    Optional<Certificacion> findVigente(@Param("cuit") String cuit,
                                        @Param("tipo") TipoRecurso tipo,
                                        @Param("hoy") LocalDate hoy);
}

package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.TipoDesastre;
import com.grupo21.rescuesync.model.TipoEntidadHistorial;
import com.grupo21.rescuesync.model.TipoRecurso;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consultas de solo lectura para los indicadores propios (E4, frente A).
 *
 * <p>Devuelven datos crudos filtrados; la agregación (por mes, por tipo, porcentajes) se hace en
 * {@code IndicadoresPropiosService}. Así no dependemos de funciones de fecha propias de cada
 * motor (H2 en tests, PostgreSQL en ejecución).
 *
 * <p>Los filtros opcionales van como {@code (:param is null or ...)}.
 */
@Repository
public class IndicadoresQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Primer cambio a {@code estadoNuevo} de cada entidad del tipo dado, solo si ese primer cambio
     * cae dentro de [inicio, fin). Devuelve entidadId → fecha.
     */
    public Map<Long, Instant> primerCambioEnPeriodo(TipoEntidadHistorial tipo, Enum<?> estadoNuevo,
                                                    Instant inicio, Instant fin) {
        List<Object[]> filas = em.createQuery(
                        "select h.entidadId, min(h.fechaCambio) from HistorialEstado h "
                                + "where h.tipoEntidad = :tipo and h.estadoNuevo = :estado "
                                + "group by h.entidadId "
                                + "having min(h.fechaCambio) >= :inicio and min(h.fechaCambio) < :fin",
                        Object[].class)
                .setParameter("tipo", tipo)
                .setParameter("estado", estadoNuevo.name())
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultList();
        return aMapa(filas);
    }

    /** Entidades del tipo dado con al menos un cambio a {@code estadoNuevo} dentro de [inicio, fin). */
    public List<Long> conCambioEnPeriodo(TipoEntidadHistorial tipo, Enum<?> estadoNuevo,
                                         Instant inicio, Instant fin) {
        return em.createQuery(
                        "select distinct h.entidadId from HistorialEstado h "
                                + "where h.tipoEntidad = :tipo and h.estadoNuevo = :estado "
                                + "and h.fechaCambio >= :inicio and h.fechaCambio < :fin",
                        Long.class)
                .setParameter("tipo", tipo)
                .setParameter("estado", estadoNuevo.name())
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultList();
    }

    /** Primer cambio a {@code estadoNuevo} (sin límite de fecha) de cada entidad indicada. */
    public Map<Long, Instant> primerCambio(TipoEntidadHistorial tipo, Enum<?> estadoNuevo,
                                           Collection<Long> entidadIds) {
        if (entidadIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> filas = em.createQuery(
                        "select h.entidadId, min(h.fechaCambio) from HistorialEstado h "
                                + "where h.tipoEntidad = :tipo and h.estadoNuevo = :estado "
                                + "and h.entidadId in :ids group by h.entidadId",
                        Object[].class)
                .setParameter("tipo", tipo)
                .setParameter("estado", estadoNuevo.name())
                .setParameter("ids", entidadIds)
                .getResultList();
        return aMapa(filas);
    }

    /** Ofertas por id con lote y emergencia cargados, filtradas por estado y por los filtros del dashboard. */
    public List<Oferta> ofertasConLote(Collection<Long> ofertaIds, Set<EstadoOferta> estados,
                                       Long municipioId, TipoDesastre tipoDesastre, TipoRecurso tipoRecurso) {
        if (ofertaIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select o from Oferta o join fetch o.lote l join fetch l.emergencia e "
                                + "where o.id in :ids and o.estado in :estados "
                                + "and (:municipioId is null or e.municipio.id = :municipioId) "
                                + "and (:tipoDesastre is null or e.tipoDesastre = :tipoDesastre) "
                                + "and (:tipoRecurso is null or l.tipoRecurso = :tipoRecurso)",
                        Oferta.class)
                .setParameter("ids", ofertaIds)
                .setParameter("estados", estados)
                .setParameter("municipioId", municipioId)
                .setParameter("tipoDesastre", tipoDesastre)
                .setParameter("tipoRecurso", tipoRecurso)
                .getResultList();
    }

    /** Lotes por id en los estados dados, filtrados por los filtros del dashboard. */
    public List<Lote> lotes(Collection<Long> loteIds, Set<EstadoLote> estados,
                            Long municipioId, TipoDesastre tipoDesastre, TipoRecurso tipoRecurso) {
        if (loteIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select l from Lote l join l.emergencia e "
                                + "where l.id in :ids and l.estado in :estados "
                                + "and (:municipioId is null or e.municipio.id = :municipioId) "
                                + "and (:tipoDesastre is null or e.tipoDesastre = :tipoDesastre) "
                                + "and (:tipoRecurso is null or l.tipoRecurso = :tipoRecurso)",
                        Lote.class)
                .setParameter("ids", loteIds)
                .setParameter("estados", estados)
                .setParameter("municipioId", municipioId)
                .setParameter("tipoDesastre", tipoDesastre)
                .setParameter("tipoRecurso", tipoRecurso)
                .getResultList();
    }

    /** Pares (loteId, ongId) de las ofertas en los estados dados de esos lotes. */
    public List<Object[]> ongsPorLote(Collection<Long> loteIds, Set<EstadoOferta> estados) {
        if (loteIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select o.lote.id, o.ong.id from Oferta o "
                                + "where o.lote.id in :ids and o.estado in :estados",
                        Object[].class)
                .setParameter("ids", loteIds)
                .setParameter("estados", estados)
                .getResultList();
    }

    /** Emergencias registradas en [inicio, fin) con municipio cargado. */
    public List<Emergencia> emergencias(Instant inicio, Instant fin, Long municipioId, TipoDesastre tipoDesastre) {
        return em.createQuery(
                        "select e from Emergencia e join fetch e.municipio m "
                                + "where e.createdAt >= :inicio and e.createdAt < :fin "
                                + "and (:municipioId is null or m.id = :municipioId) "
                                + "and (:tipoDesastre is null or e.tipoDesastre = :tipoDesastre) "
                                + "order by e.createdAt desc, e.id desc",
                        Emergencia.class)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .setParameter("municipioId", municipioId)
                .setParameter("tipoDesastre", tipoDesastre)
                .getResultList();
    }

    /** Filas (loteId, emergenciaId, cantidadRequerida) de los lotes de esas emergencias. */
    public List<Object[]> lotesDeEmergencias(Collection<Long> emergenciaIds) {
        if (emergenciaIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select l.id, l.emergencia.id, l.cantidadRequerida from Lote l "
                                + "where l.emergencia.id in :ids",
                        Object[].class)
                .setParameter("ids", emergenciaIds)
                .getResultList();
    }

    /** loteId → suma de cantidadOfrecida de sus ofertas en los estados dados. */
    public Map<Long, Long> cantidadOfrecidaPorLote(Collection<Long> loteIds, Set<EstadoOferta> estados) {
        if (loteIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> filas = em.createQuery(
                        "select o.lote.id, sum(o.cantidadOfrecida) from Oferta o "
                                + "where o.lote.id in :ids and o.estado in :estados group by o.lote.id",
                        Object[].class)
                .setParameter("ids", loteIds)
                .setParameter("estados", estados)
                .getResultList();
        Map<Long, Long> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            resultado.put((Long) fila[0], ((Number) fila[1]).longValue());
        }
        return resultado;
    }

    private static Map<Long, Instant> aMapa(List<Object[]> filas) {
        Map<Long, Instant> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            resultado.put((Long) fila[0], (Instant) fila[1]);
        }
        return resultado;
    }
}

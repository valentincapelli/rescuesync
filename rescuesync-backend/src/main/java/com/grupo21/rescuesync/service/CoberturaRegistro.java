package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.config.ConvocatoriaProperties;
import com.grupo21.rescuesync.dto.CoberturaResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.DecisionCobertura;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.OperacionCobertura;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Persistencia de las decisiones y del ID exacto a reconciliar; no realiza llamadas HTTP. */
@Service
@RequiredArgsConstructor
public class CoberturaRegistro {
    private final EmergenciaRepository emergencias;
    private final LoteRepository lotes;
    private final OfertaRepository ofertas;
    private final HistorialEstadoService historial;
    private final ConvocatoriaProperties convocatoria;
    private static final Duration ESPERA_REINTENTO = Duration.ofSeconds(30);

    public record Envio(Long emergenciaId, Long caseId, String taskId, OperacionCobertura operacion,
                        DecisionCobertura decision, Instant ultimoIntento) {}

    @Transactional(readOnly = true)
    public CoberturaResponse resumen(Long id) {
        var e = buscar(id);
        var cantidades = cantidades(id);
        var detalle = lotes.findByEmergenciaIdOrderByIdAsc(id).stream().map(l ->
                new CoberturaResponse.Lote(l.getId(), l.getTipoRecurso(), l.getDescripcion(),
                        l.getCantidadRequerida(), l.getUnidadMedida(), cantidades.getOrDefault(l.getId(), 0L))).toList();
        return new CoberturaResponse(EmergenciaService.toResponse(e), e.getFechaCierreConvocatoria(),
                plazoVencido(e), e.getDecisionCobertura(), e.isRepublicacionPendiente(),
                e.isResolucionCoberturaPendiente(), e.isCoberturaSincronizacionPendiente(),
                e.getResolucionCobertura(), detalle);
    }

    @Transactional(readOnly = true)
    public Envio pendiente(Long id) {
        var e = buscar(id);
        return e.isCoberturaSincronizacionPendiente() ? envio(e) : null;
    }

    @Transactional(readOnly = true)
    public Long validar(Long id, OperacionCobertura operacion, DecisionCobertura decision) {
        var e = buscar(id);
        validar(e, operacion, decision);
        if (e.getBonitaCaseId() == null) {
            throw new BusinessException("La emergencia no tiene un caso en Bonita para continuar el proceso");
        }
        return e.getBonitaCaseId();
    }

    @Transactional
    public Envio reservar(Long id, Long caseId, String taskId, OperacionCobertura operacion,
                         DecisionCobertura decision, String resolucion) {
        var e = bloquear(id);
        if (!Objects.equals(caseId, e.getBonitaCaseId())) throw new BusinessException("El caso de Bonita cambió");
        if (e.isCoberturaSincronizacionPendiente()) return null;
        validar(e, operacion, decision);
        e.setOperacionCobertura(operacion);
        e.setCoberturaTaskId(taskId);
        e.setCoberturaSincronizacionPendiente(true);
        e.setCoberturaUltimoIntento(Instant.now());
        if (operacion == OperacionCobertura.EVALUAR) e.setDecisionCobertura(decision);
        if (operacion == OperacionCobertura.RESOLVER) e.setResolucionCobertura(resolucion.trim());
        if (operacion == OperacionCobertura.REPUBLICAR) {
            e.setCierreConvocatoriaPrevisto(LocalDateTime.now().plusNanos(convocatoria.plazoMs() * 1_000_000));
        }
        emergencias.flush();
        return envio(e);
    }

    @Transactional
    public Envio renovar(Envio anterior) {
        var e = bloquear(anterior.emergenciaId());
        if (!corresponde(e, anterior) || !Objects.equals(e.getCoberturaUltimoIntento(), anterior.ultimoIntento())
                || e.getCoberturaUltimoIntento().plus(ESPERA_REINTENTO).isAfter(Instant.now())) return null;
        e.setCoberturaUltimoIntento(Instant.now());
        if (anterior.operacion() == OperacionCobertura.REPUBLICAR) {
            e.setCierreConvocatoriaPrevisto(LocalDateTime.now().plusNanos(convocatoria.plazoMs() * 1_000_000));
        }
        emergencias.flush();
        return envio(e);
    }

    @Transactional
    public void confirmar(Envio envio) {
        var e = bloquear(envio.emergenciaId());
        if (!corresponde(e, envio)) return;
        switch (envio.operacion()) {
            case EVALUAR -> {
                var cantidades = cantidades(e.getId());
                if (envio.decision() != DecisionCobertura.SIN_COBERTURA) {
                    for (var lote : lotes.findByEmergenciaIdOrderByIdAsc(e.getId())) {
                        historial.cambiarEstado(lote, cantidades.getOrDefault(lote.getId(), 0L)
                                >= lote.getCantidadRequerida() ? EstadoLote.CUBIERTO : EstadoLote.PARCIALMENTE_CUBIERTO);
                    }
                    historial.cambiarEstado(e, envio.decision() == DecisionCobertura.COMPLETA
                            ? EstadoEmergencia.COBERTURA_EVALUADA : EstadoEmergencia.COBERTURA_PARCIAL);
                }
            }
            case REPUBLICAR -> {
                e.setFechaCierreConvocatoria(e.getCierreConvocatoriaPrevisto());
                e.setDecisionCobertura(null);
                e.setResolucionCobertura(null);
            }
            case RESOLVER -> { /* Conserva COBERTURA_PARCIAL y la resolución registrada. */ }
        }
        e.setCoberturaSincronizacionPendiente(false);
    }

    private void validar(Emergencia e, OperacionCobertura operacion, DecisionCobertura decision) {
        if (e.isCoberturaSincronizacionPendiente()) {
            if (e.getOperacionCobertura() != operacion
                    || operacion == OperacionCobertura.EVALUAR && e.getDecisionCobertura() != decision) {
                throw new BusinessException("Hay una operación de cobertura pendiente de confirmación");
            }
            return;
        }
        switch (operacion) {
            case EVALUAR -> {
                if (e.getEstado() != EstadoEmergencia.CONVOCATORIA_PUBLICADA || e.isRepublicacionPendiente()) {
                    throw new BusinessException("La convocatoria no está disponible para evaluar cobertura");
                }
                if (!plazoVencido(e)) throw new BusinessException("La convocatoria todavía no venció");
                var necesidades = lotes.findByEmergenciaIdOrderByIdAsc(e.getId());
                if (necesidades.isEmpty()) throw new BusinessException("No hay lotes para evaluar");
                var cantidades = cantidades(e.getId());
                boolean completa = necesidades.stream().allMatch(l -> cantidades.getOrDefault(l.getId(), 0L) >= l.getCantidadRequerida());
                boolean hayOfertas = cantidades.values().stream().anyMatch(c -> c > 0);
                if (decision == null || decision == DecisionCobertura.COMPLETA && !completa
                        || decision != DecisionCobertura.COMPLETA && completa
                        || decision == DecisionCobertura.PARCIAL && !hayOfertas) {
                    throw new BusinessException("La decisión no coincide con la cobertura de los lotes");
                }
            }
            case REPUBLICAR -> {
                if (!e.isRepublicacionPendiente() || e.getEstado() != EstadoEmergencia.CONVOCATORIA_PUBLICADA) {
                    throw new BusinessException("La emergencia no está esperando una nueva convocatoria");
                }
            }
            case RESOLVER -> {
                if (!e.isResolucionCoberturaPendiente()) {
                    throw new BusinessException("La emergencia no tiene una cobertura parcial pendiente de resolución");
                }
            }
        }
    }

    private boolean corresponde(Emergencia e, Envio envio) {
        return e.isCoberturaSincronizacionPendiente() && Objects.equals(e.getBonitaCaseId(), envio.caseId())
                && Objects.equals(e.getCoberturaTaskId(), envio.taskId()) && e.getOperacionCobertura() == envio.operacion();
    }

    private boolean plazoVencido(Emergencia e) {
        return e.getFechaCierreConvocatoria() != null && !LocalDateTime.now().isBefore(e.getFechaCierreConvocatoria());
    }

    private Map<Long, Long> cantidades(Long id) {
        return ofertas.findConOngByEmergenciaId(id).stream().collect(Collectors.groupingBy(
                o -> o.getLote().getId(), Collectors.summingLong(Oferta::getCantidadOfrecida)));
    }

    private Emergencia buscar(Long id) {
        return emergencias.findById(id).orElseThrow(() -> new ResourceNotFoundException("Emergencia", id));
    }

    private Emergencia bloquear(Long id) {
        return emergencias.buscarConBloqueo(id).orElseThrow(() -> new ResourceNotFoundException("Emergencia", id));
    }

    private Envio envio(Emergencia e) {
        return new Envio(e.getId(), e.getBonitaCaseId(), e.getCoberturaTaskId(), e.getOperacionCobertura(),
                e.getDecisionCobertura(), e.getCoberturaUltimoIntento());
    }
}

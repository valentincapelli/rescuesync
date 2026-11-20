package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.dto.despliegues.SeleccionOfertaEstadoResponse;
import com.grupo21.rescuesync.model.EstadoIntencionSeleccionBonita;
import com.grupo21.rescuesync.model.IntencionSeleccionBonita;
import com.grupo21.rescuesync.repository.DespliegueRepository;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.IntencionSeleccionBonitaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Transacciones locales cortas; nunca llama a Bonita. */
@Service
@RequiredArgsConstructor
public class SeleccionBonitaRegistro {
    private final EmergenciaRepository emergencias;
    private final IntencionSeleccionBonitaRepository intenciones;
    private final DespliegueRepository despliegues;
    private final PlanificadorSeleccionBonita planificador;

    public record Envio(long ofertaId, long caseId, String taskId, boolean continuar, Instant ultimoIntento) {}

    @Transactional(readOnly = true)
    public boolean tieneIntenciones(Long emergenciaId) {
        return !cola(emergenciaId).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<Long> recuperables(int maximo) {
        return intenciones.buscarEmergenciasRecuperables(List.of(
                EstadoIntencionSeleccionBonita.PENDIENTE,
                EstadoIntencionSeleccionBonita.DESPACHANDO),
                org.springframework.data.domain.PageRequest.of(0, maximo));
    }

    @Transactional(readOnly = true)
    public List<SeleccionOfertaEstadoResponse> estados(Long emergenciaId) {
        var porOferta = despliegues.findByOferta_Lote_Emergencia_IdOrderById(emergenciaId).stream()
                .collect(Collectors.toMap(d -> d.getOferta().getId(), Function.identity()));
        return cola(emergenciaId).stream().map(i -> {
            var d = porOferta.get(i.getOferta().getId());
            return new SeleccionOfertaEstadoResponse(i.getOferta().getId(), i.getOferta().getOng().getNombre(),
                    i.getEstado(), i.getDetalleIncidente(), d == null ? null : d.getComprometidoEn(),
                    d == null ? null : d.getFinalizadoEn(), d == null ? null : d.getLiberadoEn());
        }).toList();
    }

    @Transactional(readOnly = true)
    public Long caso(Long emergenciaId) {
        var e = emergencias.findById(emergenciaId).orElseThrow();
        if (e.getBonitaCaseId() == null) throw new BusinessException("La emergencia no tiene caso en Bonita");
        return e.getBonitaCaseId();
    }

    @Transactional(readOnly = true)
    public Envio enCurso(Long emergenciaId) {
        return cola(emergenciaId).stream()
                .filter(i -> i.getEstado() == EstadoIntencionSeleccionBonita.DESPACHANDO)
                .findFirst().map(this::envio).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean puedeSeleccionar(Long emergenciaId) {
        return planificador.siguiente(cola(emergenciaId).stream()
                .map(i -> new PlanificadorSeleccionBonita.OfertaEnCola(i.getOferta().getId(), i.getEstado()))
                .toList()).isPresent();
    }

    @Transactional
    public Envio reservar(Long emergenciaId, long caseId, String taskId) {
        bloquearCaso(emergenciaId, caseId);
        List<IntencionSeleccionBonita> cola = cola(emergenciaId);
        var siguiente = planificador.siguiente(cola.stream()
                .map(i -> new PlanificadorSeleccionBonita.OfertaEnCola(i.getOferta().getId(), i.getEstado()))
                .toList()).orElse(null);
        if (siguiente == null) return null;
        var intencion = cola.stream().filter(i -> i.getOferta().getId().equals(siguiente.ofertaId()))
                .findFirst().orElseThrow();
        intencion.setBonitaCaseId(caseId);
        intencion.setBonitaTaskId(taskId);
        intencion.setContinuarSeleccionandoInput(siguiente.continuarSeleccionandoInput());
        intencion.setUltimoIntentoEn(Instant.now());
        intencion.setIntentos(1);
        intencion.setEstado(EstadoIntencionSeleccionBonita.DESPACHANDO);
        intenciones.saveAndFlush(intencion);
        return envio(intencion);
    }

    @Transactional
    public Envio renovar(Long emergenciaId, Envio anterior) {
        bloquearCaso(emergenciaId, anterior.caseId());
        var i = intenciones.findByOfertaId(anterior.ofertaId()).orElseThrow();
        if (i.getEstado() != EstadoIntencionSeleccionBonita.DESPACHANDO
                || !anterior.taskId().equals(i.getBonitaTaskId())
                || !anterior.ultimoIntento().equals(i.getUltimoIntentoEn())) return null;
        if (i.getIntentos() >= 3) {
            i.setEstado(EstadoIntencionSeleccionBonita.INCIDENTE);
            i.setDetalleIncidente("Se agotaron los intentos de ejecución de la tarea reservada");
            return null;
        }
        i.setUltimoIntentoEn(Instant.now());
        i.setIntentos(i.getIntentos() + 1);
        intenciones.saveAndFlush(i);
        return envio(i);
    }

    @Transactional
    public void confirmar(Long emergenciaId, Envio envio) {
        bloquearCaso(emergenciaId, envio.caseId());
        var i = intenciones.findByOfertaId(envio.ofertaId()).orElseThrow();
        if (i.getEstado() == EstadoIntencionSeleccionBonita.DESPACHANDO
                && envio.taskId().equals(i.getBonitaTaskId())) {
            i.setEstado(EstadoIntencionSeleccionBonita.EN_CICLO);
        }
    }

    @Transactional
    public void incidente(Long emergenciaId, Envio envio, String motivo) {
        bloquearCaso(emergenciaId, envio.caseId());
        var i = intenciones.findByOfertaId(envio.ofertaId()).orElseThrow();
        if (i.getEstado() == EstadoIntencionSeleccionBonita.DESPACHANDO
                && envio.taskId().equals(i.getBonitaTaskId())) {
            i.setEstado(EstadoIntencionSeleccionBonita.INCIDENTE);
            i.setDetalleIncidente(motivo);
        }
    }

    @Transactional
    public void incidentePendiente(Long emergenciaId, Long caseId, String motivo) {
        bloquearCaso(emergenciaId, caseId);
        var siguiente = cola(emergenciaId).stream()
                .filter(i -> i.getEstado() == EstadoIntencionSeleccionBonita.PENDIENTE)
                .findFirst();
        siguiente.ifPresent(i -> {
            i.setBonitaCaseId(caseId);
            i.setEstado(EstadoIntencionSeleccionBonita.INCIDENTE);
            i.setDetalleIncidente(motivo);
        });
    }

    @Transactional
    public void liberacionesConfirmadas(Long emergenciaId, Long caseId, java.util.Set<Long> liberadas) {
        bloquearCaso(emergenciaId, caseId);
        for (var i : cola(emergenciaId)) {
            long ofertaId = i.getOferta().getId();
            if (liberadas.contains(ofertaId) && i.getEstado() != EstadoIntencionSeleccionBonita.LIBERADA) {
                var d = despliegues.findByOfertaId(ofertaId).orElseThrow(() ->
                        new BusinessException("Falta el despliegue de una oferta liberada"));
                if (d.getLiberadoEn() == null || !d.getBonitaCaseId().equals(caseId)) {
                    throw new BusinessException("La liberación todavía no está confirmada");
                }
                i.setEstado(EstadoIntencionSeleccionBonita.LIBERADA);
            }
        }
    }

    private void bloquearCaso(Long emergenciaId, Long caseId) {
        var e = emergencias.buscarConBloqueo(emergenciaId).orElseThrow();
        if (!caseId.equals(e.getBonitaCaseId())) throw new BusinessException("El caso de Bonita cambió");
    }

    private List<IntencionSeleccionBonita> cola(Long emergenciaId) {
        return intenciones.findByOferta_Lote_Emergencia_IdOrderByOferta_IdAsc(emergenciaId);
    }

    private Envio envio(IntencionSeleccionBonita i) {
        return new Envio(i.getOferta().getId(), i.getBonitaCaseId(), i.getBonitaTaskId(),
                i.getContinuarSeleccionandoInput(), i.getUltimoIntentoEn());
    }
}

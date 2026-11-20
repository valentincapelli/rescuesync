package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Avanza únicamente las notificaciones de la oferta activa del ciclo de Bonita. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionBonitaCoordinador {
    private final NotificacionBonitaRegistro registro;
    private final SeleccionBonitaRegistro selecciones;
    private final BonitaClient bonita;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recepcionRegistrada(NotificacionService.RecepcionRegistrada evento) {
        try {
            sincronizar(evento.emergenciaId());
        } catch (RuntimeException ex) {
            log.warn("Recepción de emergencia {} pendiente de sincronización", evento.emergenciaId());
        }
    }

    public void sincronizar(Long emergenciaId) {
        Long caseId = selecciones.caso(emergenciaId);
        String caso = caseId.toString();
        boolean activo = bonita.buscarCaso(caso).isPresent();
        if (!activo && bonita.buscarCasoArchivado(caso).isEmpty()) return;
        if (!emergenciaId.toString().equals(bonita.leerVariable(caso, "emergenciaId", !activo).orElse(null))) {
            throw new BonitaIntegrationException("El caso no corresponde a la emergencia notificada", null);
        }
        var pendientes = registro.pendientes(emergenciaId, caseId);
        if (!pendientes.isEmpty()) {
            var archivadas = bonita.listarActividadesArchivadas(caso);
            for (var pendiente : pendientes) {
                if (archivadas.stream().anyMatch(t -> pendiente.taskId().equals(t.sourceObjectId())
                        && pendiente.paso().tarea().equals(t.name()) && "completed".equals(t.state()))) {
                    registro.confirmar(pendiente);
                }
            }
        }
        if (!activo) return;
        String seleccionada = bonita.leerVariable(caso, "ofertaSeleccionadaId", false).orElse(null);
        if (seleccionada == null || seleccionada.equals("0")) return;
        Long ofertaId;
        try {
            ofertaId = Long.valueOf(seleccionada);
        } catch (NumberFormatException ex) {
            throw new BonitaIntegrationException("Bonita no identifica una oferta seleccionada válida", ex);
        }
        // A lo sumo envío y recepción. Si el motor aún no creó la siguiente tarea, se retoma al actualizar.
        for (int paso = 0; paso < 2; paso++) {
            var estado = registro.consultar(emergenciaId, ofertaId, caseId);
            if (estado == null) return;
            if (estado.pendiente() != null) {
                if (!reconciliar(estado.pendiente())) return;
            } else if (estado.siguiente() != null) {
                var ready = bonita.buscarTareasActivas(caso, estado.siguiente().tarea());
                if (ready.isEmpty()) return;
                if (ready.size() != 1) throw new BonitaIntegrationException("Hay más de una tarea de notificación disponible", null);
                var envio = registro.reservar(estado.id(), emergenciaId, caseId, ready.getFirst(), estado.siguiente());
                if (envio == null || !enviar(envio)) return;
            } else return;
        }
    }

    private boolean reconciliar(NotificacionBonitaRegistro.Envio envio) {
        String caso = envio.caseId().toString();
        boolean completada = bonita.listarActividadesArchivadas(caso).stream().anyMatch(t ->
                envio.taskId().equals(t.sourceObjectId()) && envio.paso().tarea().equals(t.name())
                        && "completed".equals(t.state()));
        if (completada) {
            registro.confirmar(envio);
            return true;
        }
        var ready = bonita.buscarTareasActivas(caso, envio.paso().tarea());
        if (ready.size() == 1 && ready.getFirst().equals(envio.taskId())) {
            var renovado = registro.renovar(envio);
            return renovado != null && enviar(renovado);
        }
        return false;
    }

    private boolean enviar(NotificacionBonitaRegistro.Envio envio) {
        try {
            bonita.completarTareaPorId(envio.taskId());
            registro.confirmar(envio);
            return true;
        } catch (BonitaIntegrationException ex) {
            log.warn("Notificación {} pendiente de confirmación del proceso", envio.id());
            return false;
        }
    }
}

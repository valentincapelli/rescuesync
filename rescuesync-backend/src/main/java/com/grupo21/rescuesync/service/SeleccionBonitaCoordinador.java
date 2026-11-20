package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.client.EstadoDespliegueBonita;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Una oferta por ciclo. Las consultas y los POST a Bonita quedan fuera de transacciones locales. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeleccionBonitaCoordinador {
    private static final String TAREA = "Seleccionar oferta";
    private static final Duration ESPERA_RECONCILIACION = Duration.ofSeconds(30);
    private final SeleccionBonitaRegistro registro;
    private final BonitaClient bonita;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void adjudicacionRegistrada(OfertaService.AdjudicacionRegistrada evento) {
        try {
            despachar(evento.emergenciaId());
        } catch (RuntimeException ex) {
            // La intención está confirmada; el seguimiento de E4-06 puede reanudarla.
            log.warn("La selección de la emergencia {} queda pendiente de sincronización", evento.emergenciaId());
        }
    }

    public void liberacionConfirmada(Long emergenciaId, EstadoDespliegueBonita estado) {
        if (!registro.tieneIntenciones(emergenciaId)) return;
        registro.liberacionesConfirmadas(emergenciaId, estado.caseId(), estado.ofertasLiberadas());
        despachar(emergenciaId);
    }

    public void despachar(Long emergenciaId) {
        try {
            despacharValidado(emergenciaId);
        } catch (BonitaIntegrationException ex) {
            Throwable causa = ex.getCause();
            if (causa == null || causa instanceof HttpClientErrorException http
                    && http.getStatusCode().is4xxClientError()) {
                Long caseId = registro.caso(emergenciaId);
                var actual = registro.enCurso(emergenciaId);
                if (actual == null) registro.incidentePendiente(emergenciaId, caseId,
                        "El caso o la autenticación de Bonita requiere revisión");
                else registro.incidente(emergenciaId, actual,
                        "El caso o la autenticación de Bonita requiere revisión");
            }
            throw ex;
        }
    }

    private void despacharValidado(Long emergenciaId) {
        var enCurso = registro.enCurso(emergenciaId);
        if (enCurso == null && !registro.puedeSeleccionar(emergenciaId)) return;
        Long caseId = registro.caso(emergenciaId);
        String caso = caseId.toString();
        boolean activo = bonita.buscarCaso(caso).isPresent();
        if (!activo && bonita.buscarCasoArchivado(caso).isEmpty()) {
            throw new BonitaIntegrationException("No se encontró el caso de Bonita de la emergencia", null);
        }
        if (!emergenciaId.toString().equals(bonita.leerVariable(caso, "emergenciaId", !activo).orElse(null))) {
            throw new BonitaIntegrationException("El caso de Bonita no corresponde a la emergencia", null);
        }

        if (enCurso != null) {
            if (completada(caso, enCurso.taskId())) {
                registro.confirmar(emergenciaId, enCurso);
                return;
            }
            if (activo && envioConfirmadoPorVariable(caso, enCurso.ofertaId())) {
                registro.confirmar(emergenciaId, enCurso);
                return;
            }
            if (!activo || enCurso.ultimoIntento().plus(ESPERA_RECONCILIACION).isAfter(Instant.now())) return;
            List<String> ready = bonita.buscarTareasActivas(caso, TAREA);
            if (ready.size() == 1 && ready.getFirst().equals(enCurso.taskId())) {
                var renovado = registro.renovar(emergenciaId, enCurso);
                if (renovado != null) ejecutar(emergenciaId, renovado);
            } else if (ready.size() > 1 || ready.size() == 1) {
                registro.incidente(emergenciaId, enCurso, "La tarea reservada ya no es la única tarea ready");
            }
            return;
        }
        if (!activo) return;
        List<String> ready = bonita.buscarTareasActivas(caso, TAREA);
        if (ready.isEmpty()) return;
        if (ready.size() != 1) {
            registro.incidentePendiente(emergenciaId, caseId, "Hay más de una tarea Seleccionar oferta ready");
            return;
        }
        var envio = registro.reservar(emergenciaId, caseId, ready.getFirst());
        if (envio != null) ejecutar(emergenciaId, envio);
    }

    private void ejecutar(Long emergenciaId, SeleccionBonitaRegistro.Envio envio) {
        try {
            bonita.completarTareaPorId(envio.taskId(), Map.of(
                    "ofertaSeleccionadaIdInput", envio.ofertaId(),
                    "continuarSeleccionandoInput", envio.continuar()));
            // Un 204 solo confirma la ejecución de la tarea humana, no el compromiso nacional.
        } catch (BonitaIntegrationException ex) {
            Throwable causa = ex.getCause();
            if (causa instanceof HttpClientErrorException http
                    && (http.getStatusCode().value() == 409 || http.getStatusCode().value() == 401
                    || http.getStatusCode().is4xxClientError())) {
                registro.incidente(emergenciaId, envio, "Bonita rechazó la ejecución: HTTP "
                        + http.getStatusCode().value());
            }
            // Timeout, 5xx o respuesta perdida: conservar taskId y reconciliar luego.
            log.warn("Resultado de selección incierto para emergencia {} y taskId {}", emergenciaId, envio.taskId());
        }
    }

    private boolean completada(String caso, String taskId) {
        return bonita.listarActividadesArchivadas(caso).stream()
                .anyMatch(t -> taskId.equals(t.sourceObjectId())
                        && TAREA.equals(t.name()) && "completed".equals(t.state()));
    }

    private boolean envioConfirmadoPorVariable(String caso, long ofertaId) {
        return bonita.leerVariable(caso, "ofertaSeleccionadaId", false)
                .filter(valor -> valor.equals(Long.toString(ofertaId))).isPresent();
    }
}

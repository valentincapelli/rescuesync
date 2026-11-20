package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.model.Notificacion;
import com.grupo21.rescuesync.model.PasoNotificacionBonita;
import com.grupo21.rescuesync.model.TipoNotificacion;
import com.grupo21.rescuesync.repository.NotificacionRepository;
import com.grupo21.rescuesync.repository.NotificacionUsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

/** Asocia cada tarea de envío/recepción a su oferta y conserva los envíos de resultado incierto. */
@Service
@RequiredArgsConstructor
public class NotificacionBonitaRegistro {
    private final NotificacionRepository notificaciones;
    private final NotificacionUsuarioRepository destinatarios;
    private static final Duration ESPERA_REINTENTO = Duration.ofSeconds(30);

    public record Estado(Long id, PasoNotificacionBonita siguiente, Envio pendiente) {}
    public record Envio(Long id, Long emergenciaId, Long ofertaId, Long caseId, String taskId,
                        PasoNotificacionBonita paso, Instant ultimoIntento) {}

    @Transactional(readOnly = true)
    public List<Envio> pendientes(Long emergenciaId, Long caseId) {
        return notificaciones.findByOferta_Lote_Emergencia_IdAndBonitaPendienteTrue(emergenciaId).stream().map(n -> {
            validarCaso(n, emergenciaId, caseId);
            return envio(n);
        }).toList();
    }

    @Transactional(readOnly = true)
    public Estado consultar(Long emergenciaId, Long ofertaId, Long caseId) {
        var n = notificaciones.findByOfertaId(ofertaId).orElse(null);
        if (n == null || n.getTipo() != TipoNotificacion.OFERTA_SELECCIONADA) return null;
        validarCaso(n, emergenciaId, caseId);
        return new Estado(n.getId(), siguiente(n), n.isBonitaPendiente() ? envio(n) : null);
    }

    @Transactional
    public Envio reservar(Long id, Long emergenciaId, Long caseId, String taskId, PasoNotificacionBonita paso) {
        var n = notificaciones.buscarConBloqueo(id).orElseThrow();
        validarCaso(n, emergenciaId, caseId);
        if (n.isBonitaPendiente() || siguiente(n) != paso) return null;
        n.setBonitaCaseId(caseId);
        n.setBonitaTaskId(taskId);
        n.setPasoBonita(paso);
        n.setBonitaUltimoIntento(Instant.now());
        n.setBonitaPendiente(true);
        notificaciones.flush();
        return envio(n);
    }

    @Transactional
    public Envio renovar(Envio anterior) {
        var n = notificaciones.buscarConBloqueo(anterior.id()).orElseThrow();
        if (!corresponde(n, anterior) || !Objects.equals(n.getBonitaUltimoIntento(), anterior.ultimoIntento())
                || n.getBonitaUltimoIntento().plus(ESPERA_REINTENTO).isAfter(Instant.now())) return null;
        n.setBonitaUltimoIntento(Instant.now());
        notificaciones.flush();
        return envio(n);
    }

    @Transactional
    public void confirmar(Envio envio) {
        var n = notificaciones.buscarConBloqueo(envio.id()).orElseThrow();
        if (!corresponde(n, envio)) return;
        if (envio.paso() == PasoNotificacionBonita.ENVIAR) n.setNotificadaEn(Instant.now());
        else n.setRecibidaEn(Instant.now());
        n.setBonitaPendiente(false);
    }

    private PasoNotificacionBonita siguiente(Notificacion n) {
        if (n.getNotificadaEn() == null) return PasoNotificacionBonita.ENVIAR;
        if (n.getRecibidaEn() == null && destinatarios.existsByNotificacionIdAndFechaLecturaIsNotNull(n.getId())) {
            return PasoNotificacionBonita.RECIBIR;
        }
        return null;
    }

    private boolean corresponde(Notificacion n, Envio envio) {
        validarCaso(n, envio.emergenciaId(), envio.caseId());
        return n.isBonitaPendiente() && Objects.equals(n.getBonitaTaskId(), envio.taskId())
                && n.getPasoBonita() == envio.paso() && n.getOferta().getId().equals(envio.ofertaId());
    }

    private void validarCaso(Notificacion n, Long emergenciaId, Long caseId) {
        var e = n.getOferta().getLote().getEmergencia();
        if (!e.getId().equals(emergenciaId) || !Objects.equals(e.getBonitaCaseId(), caseId)) {
            throw new BusinessException("La notificación no corresponde al caso de la emergencia");
        }
    }

    private Envio envio(Notificacion n) {
        return new Envio(n.getId(), n.getOferta().getLote().getEmergencia().getId(), n.getOferta().getId(),
                n.getBonitaCaseId(), n.getBonitaTaskId(), n.getPasoBonita(), n.getBonitaUltimoIntento());
    }
}

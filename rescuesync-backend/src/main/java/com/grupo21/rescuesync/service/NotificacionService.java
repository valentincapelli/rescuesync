
package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.dto.NotificacionResponse;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.Notificacion;
import com.grupo21.rescuesync.model.NotificacionUsuario;
import com.grupo21.rescuesync.model.Ong;
import com.grupo21.rescuesync.model.Oferta;
import org.springframework.context.ApplicationEventPublisher;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.model.TipoNotificacion;
import com.grupo21.rescuesync.repository.NotificacionRepository;
import com.grupo21.rescuesync.repository.NotificacionUsuarioRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final NotificacionUsuarioRepository notificacionUsuarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;

    @Transactional
    public void crearNotificacion(
            Oferta oferta,
            String titulo,
            String mensaje,
            TipoNotificacion tipo
    ) {
        Notificacion notificacion = new Notificacion();
        Ong ong = oferta.getOng();

        notificacion.setOferta(oferta);
        notificacion.setOng(ong);
        notificacion.setTitulo(titulo);
        notificacion.setMensaje(mensaje);
        notificacion.setTipo(tipo);

        List<Usuario> usuarios = usuarioRepository.findByOngId(ong.getId());

        for (Usuario usuario : usuarios) {
            NotificacionUsuario destinatario = new NotificacionUsuario();

            destinatario.setNotificacion(notificacion);
            destinatario.setUsuario(usuario);

            notificacion.getDestinatarios().add(destinatario);
        }

        // Cascade ALL guarda tambien los destinatarios.
        notificacionRepository.save(notificacion);
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> obtenerNotificaciones(String email) {

        Usuario usuario = buscarUsuario(email);

        return notificacionUsuarioRepository
                .findByUsuarioIdOrderByNotificacion_CreatedAtDesc(
                        usuario.getId()
                )
                .stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(String email) {

        Usuario usuario = buscarUsuario(email);

        return notificacionUsuarioRepository
                .countByUsuarioIdAndFechaLecturaIsNull(usuario.getId());
    }

    @Transactional
    public void marcarComoLeida(Long notificacionId, String email) {

        Usuario usuario = buscarUsuario(email);

        NotificacionUsuario destinatario =
                notificacionUsuarioRepository
                        .findByNotificacionIdAndUsuarioId(
                                notificacionId,
                                usuario.getId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Notificacion no encontrada"
                                )
                        );

        // Si ya estaba leida, conserva la fecha original.
        if (destinatario.getFechaLectura() == null) {
            destinatario.setFechaLectura(Instant.now());
        }
        var notificacion = destinatario.getNotificacion();
        if (notificacion.getTipo() == TipoNotificacion.OFERTA_SELECCIONADA && notificacion.getOferta() != null) {
            eventos.publishEvent(new RecepcionRegistrada(notificacion.getOferta().getLote().getEmergencia().getId()));
        }
    }

    public record RecepcionRegistrada(Long emergenciaId) {}

    private Usuario buscarUsuario(String email) {

        return usuarioRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Usuario no encontrado"
                        )
                );
    }

    private NotificacionResponse convertirADTO(
            NotificacionUsuario destinatario
    ) {
        Notificacion notificacion = destinatario.getNotificacion();

        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getTipo(),
                notificacion.getCreatedAt(),
                destinatario.getFechaLectura() != null,
                estadoRecepcion(destinatario)
        );
    }

    private NotificacionResponse.EstadoRecepcion estadoRecepcion(NotificacionUsuario destinatario) {
        var n = destinatario.getNotificacion();
        if (n.getTipo() != TipoNotificacion.OFERTA_SELECCIONADA || n.getOferta() == null) {
            return NotificacionResponse.EstadoRecepcion.NO_REQUIERE;
        }
        if (n.getRecibidaEn() != null) return NotificacionResponse.EstadoRecepcion.CONFIRMADA;
        return destinatario.getFechaLectura() == null
                ? NotificacionResponse.EstadoRecepcion.SIN_LEER : NotificacionResponse.EstadoRecepcion.PENDIENTE;
    }
}

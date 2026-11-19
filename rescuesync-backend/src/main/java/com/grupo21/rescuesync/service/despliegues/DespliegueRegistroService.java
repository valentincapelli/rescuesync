package com.grupo21.rescuesync.service.despliegues;

import com.grupo21.rescuesync.client.EstadoDespliegueBonita;
import com.grupo21.rescuesync.dto.despliegues.DespliegueResponse;
import com.grupo21.rescuesync.dto.despliegues.NovedadDespliegueResponse;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.exception.ResourceNotFoundException;
import com.grupo21.rescuesync.model.Despliegue;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.NovedadDespliegue;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.TipoNovedadDespliegue;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.DespliegueRepository;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.NovedadDespliegueRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;
import com.grupo21.rescuesync.service.HistorialEstadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Transacciones locales cortas: ninguna llamada HTTP se realiza desde esta clase. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DespliegueRegistroService {
    private final EmergenciaRepository emergencias;
    private final OfertaRepository ofertas;
    private final UsuarioRepository usuarios;
    private final DespliegueRepository despliegues;
    private final NovedadDespliegueRepository novedades;
    private final HistorialEstadoService historial;

    public record Caso(Long emergenciaId, Long caseId) {}
    public record Accion(Long id, String taskId, boolean confirmada) {}

    public List<Long> casosPendientes() {
        return emergencias.buscarIdsParaSeguimiento();
    }

    public Caso caso(Long emergenciaId) {
        Emergencia e = emergencia(emergenciaId);
        if (e.getBonitaCaseId() == null) throw new BusinessException("La emergencia todavía no tiene un caso en Bonita");
        return new Caso(e.getId(), e.getBonitaCaseId());
    }

    public List<Long> casosPropios(String email) {
        Usuario u = usuario(email, Rol.REPRESENTANTE_ONG);
        return ofertas.findByOngId(u.getOng().getId()).stream()
                .map(o -> o.getLote().getEmergencia())
                .filter(e -> e.getBonitaCaseId() != null && e.getEstado() != EstadoEmergencia.CERRADA)
                .map(Emergencia::getId).distinct().toList();
    }

    public Long autorizarOferta(Long ofertaId, String email, TipoNovedadDespliegue tipo) {
        Usuario u = usuario(email, tipo == TipoNovedadDespliegue.FINALIZACION
                ? Rol.REPRESENTANTE_ONG : Rol.CENTRO_COORDINADOR);
        Oferta o = oferta(ofertaId);
        if (u.getRol() == Rol.REPRESENTANTE_ONG && !o.getOng().getId().equals(u.getOng().getId())) {
            throw new AuthorizationDeniedException("Solo podés finalizar actividades de tu ONG");
        }
        return o.getLote().getEmergencia().getId();
    }

    public List<DespliegueResponse> listar(Long emergenciaId, String email) {
        usuario(email, Rol.CENTRO_COORDINADOR);
        emergencia(emergenciaId);
        return despliegues.findByOferta_Lote_Emergencia_IdOrderById(emergenciaId).stream()
                .map(d -> respuesta(d, false)).toList();
    }

    public List<DespliegueResponse> propias(String email) {
        Usuario u = usuario(email, Rol.REPRESENTANTE_ONG);
        return despliegues.findByOferta_Ong_IdOrderByIdDesc(u.getOng().getId()).stream()
                .map(d -> respuesta(d, true)).toList();
    }

    public DespliegueResponse obtener(Long ofertaId, String email, TipoNovedadDespliegue tipo) {
        autorizarOferta(ofertaId, email, tipo);
        return respuesta(despliegue(ofertaId), tipo == TipoNovedadDespliegue.FINALIZACION);
    }

    @Transactional
    public void aplicar(Long emergenciaId, EstadoDespliegueBonita estado) {
        Emergencia e = bloquear(emergenciaId);
        if (!Objects.equals(e.getBonitaCaseId(), estado.caseId())) {
            throw new BusinessException("El caso cambió durante la sincronización");
        }
        Map<Long, Oferta> porId = ofertas.findConOngByEmergenciaId(emergenciaId).stream()
                .collect(Collectors.toMap(Oferta::getId, Function.identity()));
        if (!porId.keySet().containsAll(estado.ofertasLiberadas())
                || (estado.ofertaComprometidaId() != null && !porId.containsKey(estado.ofertaComprometidaId()))
                || (estado.ofertaActualId() != null && !porId.containsKey(estado.ofertaActualId()))) {
            throw new BusinessException("El proceso contiene ofertas ajenas a esta emergencia");
        }
        Instant ahora = Instant.now();
        for (Oferta o : porId.values()) {
            boolean liberada = estado.ofertasLiberadas().contains(o.getId());
            boolean comprometida = o.getId().equals(estado.ofertaComprometidaId());
            if (!liberada && !comprometida) continue;
            if (o.getEstado() == EstadoOferta.RECHAZADA) {
                throw new BusinessException("Bonita procesó una oferta rechazada. Revisá la selección antes de continuar");
            }
            if (comprometida && !o.getCantidadOfrecida().equals(estado.cantidadComprometida())) {
                throw new BusinessException("La cantidad comprometida no coincide con la oferta");
            }
            Despliegue d = despliegues.findByOfertaId(o.getId()).orElseGet(() -> {
                Despliegue nuevo = new Despliegue();
                nuevo.setOferta(o);
                nuevo.setBonitaCaseId(estado.caseId());
                nuevo.setComprometidoEn(ahora);
                return despliegues.save(nuevo);
            });
            if (!d.getBonitaCaseId().equals(estado.caseId())) {
                throw new BusinessException("La oferta ya tiene seguimiento en otro caso");
            }
            if (o.getEstado() != EstadoOferta.COMPROMETIDA && o.getEstado() != EstadoOferta.FINALIZADA) {
                historial.cambiarEstado(o, EstadoOferta.COMPROMETIDA);
            }
            if (liberada) {
                if (d.getMonitoreadoEn() == null) d.setMonitoreadoEn(ahora);
                if (d.getFinalizadoEn() == null) d.setFinalizadoEn(ahora);
                if (d.getLiberadoEn() == null) d.setLiberadoEn(ahora);
                historial.cambiarEstado(o, EstadoOferta.FINALIZADA);
            }
        }
        List<Despliegue> registros = despliegues.findByOferta_Lote_Emergencia_IdOrderById(emergenciaId);
        for (Despliegue d : registros) {
            boolean actual = d.getOferta().getId().equals(estado.ofertaActualId()) && d.getLiberadoEn() == null;
            if (d.getTareaMonitoreoId() != null && estado.tareasCompletadas().contains(d.getTareaMonitoreoId())
                    || actual && estado.tareaFinalizacionId() != null) {
                if (d.getMonitoreadoEn() == null) d.setMonitoreadoEn(ahora);
            }
            if (d.getTareaFinalizacionId() != null && estado.tareasCompletadas().contains(d.getTareaFinalizacionId())) {
                if (d.getFinalizadoEn() == null) d.setFinalizadoEn(ahora);
                historial.cambiarEstado(d.getOferta(), EstadoOferta.FINALIZADA);
            }
            d.setMonitoreoDisponible(actual && estado.tareaMonitoreoId() != null);
            d.setFinalizacionDisponible(actual && estado.tareaFinalizacionId() != null);
            if (d.isMonitoreoDisponible()) d.setTareaMonitoreoId(estado.tareaMonitoreoId());
            if (d.isFinalizacionDisponible()) d.setTareaFinalizacionId(estado.tareaFinalizacionId());
            d.setSincronizadoEn(ahora);
        }
        for (NovedadDespliegue n : novedades.findByDespliegue_Oferta_Lote_Emergencia_IdAndConfirmadaEnIsNull(emergenciaId)) {
            Despliegue actividad = n.getDespliegue();
            boolean confirmadaPorAvance = n.getTipo() == TipoNovedadDespliegue.MONITOREO_COMPLETADO
                    ? actividad.getMonitoreadoEn() != null : actividad.getFinalizadoEn() != null;
            if (estado.tareasCompletadas().contains(n.getBonitaTaskId()) || confirmadaPorAvance) {
                n.setConfirmadaEn(ahora);
                Despliegue d = n.getDespliegue();
                if (n.getTipo() == TipoNovedadDespliegue.MONITOREO_COMPLETADO && d.getMonitoreadoEn() == null) {
                    d.setMonitoreadoEn(ahora);
                } else if (n.getTipo() == TipoNovedadDespliegue.FINALIZACION) {
                    if (d.getFinalizadoEn() == null) d.setFinalizadoEn(ahora);
                    historial.cambiarEstado(d.getOferta(), EstadoOferta.FINALIZADA);
                }
            }
        }
        if (!registros.isEmpty()) {
            if (e.getEstado() != EstadoEmergencia.CERRADA) historial.cambiarEstado(e, EstadoEmergencia.EN_EJECUCION);
            boolean pendientes = porId.values().stream().anyMatch(o -> o.getEstado() == EstadoOferta.SELECCIONADA
                    || o.getEstado() == EstadoOferta.COMPROMETIDA
                    || o.getEstado() == EstadoOferta.FINALIZADA && registros.stream()
                            .noneMatch(d -> d.getOferta().getId().equals(o.getId()) && d.getLiberadoEn() != null));
            if (estado.casoTerminado() && !pendientes && registros.stream().allMatch(d -> d.getLiberadoEn() != null)) {
                historial.cambiarEstado(e, EstadoEmergencia.CERRADA);
            }
        }
    }

    @Transactional
    public Accion registrar(Long ofertaId, String email, TipoNovedadDespliegue tipo, String observacion) {
        Long emergenciaId = autorizarOferta(ofertaId, email, tipo);
        bloquear(emergenciaId); // Serializa altas y doble clic incluso con varias instancias del backend.
        Despliegue d = despliegue(ofertaId);
        if (tipo != TipoNovedadDespliegue.NOVEDAD) {
            var anterior = novedades.findByDespliegueIdAndTipo(d.getId(), tipo);
            if (anterior.isPresent()) return accion(anterior.get());
        }
        if (d.getFinalizadoEn() != null || d.getLiberadoEn() != null) {
            throw new BusinessException("La actividad ya finalizó");
        }
        String tarea = null;
        if (tipo == TipoNovedadDespliegue.MONITOREO_COMPLETADO) {
            tarea = d.getTareaMonitoreoId();
            if (d.getMonitoreadoEn() != null || !d.isMonitoreoDisponible()) throw new BusinessException("El monitoreo no está disponible");
        } else if (tipo == TipoNovedadDespliegue.FINALIZACION) {
            tarea = d.getTareaFinalizacionId();
            if (d.getMonitoreadoEn() == null || !d.isFinalizacionDisponible()) throw new BusinessException("El Centro Coordinador debe completar el monitoreo primero");
        }
        NovedadDespliegue n = new NovedadDespliegue();
        n.setDespliegue(d);
        n.setAutor(usuarios.findByEmail(email).orElseThrow());
        n.setTipo(tipo);
        n.setObservacion(observacion.trim());
        n.setBonitaTaskId(tarea);
        if (tarea == null) n.setConfirmadaEn(Instant.now());
        return accion(novedades.save(n));
    }

    public List<Accion> pendientes(Long emergenciaId) {
        return novedades.findByDespliegue_Oferta_Lote_Emergencia_IdAndConfirmadaEnIsNull(emergenciaId).stream()
                .map(this::accion).toList();
    }

    public Optional<Accion> accionRegistrada(Long ofertaId, TipoNovedadDespliegue tipo) {
        if (tipo == TipoNovedadDespliegue.NOVEDAD) return Optional.empty();
        return despliegues.findByOfertaId(ofertaId)
                .flatMap(d -> novedades.findByDespliegueIdAndTipo(d.getId(), tipo)).map(this::accion);
    }

    private Accion accion(NovedadDespliegue n) {
        return new Accion(n.getId(), n.getBonitaTaskId(), n.getConfirmadaEn() != null);
    }

    private DespliegueResponse respuesta(Despliegue d, boolean ong) {
        Oferta o = d.getOferta();
        Lote l = o.getLote();
        Emergencia e = l.getEmergencia();
        List<NovedadDespliegueResponse> registros = novedades.findByDespliegueIdOrderById(d.getId()).stream()
                .map(n -> new NovedadDespliegueResponse(n.getId(), n.getTipo(), n.getObservacion(),
                        n.getAutor().getEmail(), n.getCreatedAt(), n.getConfirmadaEn() != null)).toList();
        boolean pendiente = registros.stream().anyMatch(n -> !n.confirmada());
        return new DespliegueResponse(o.getId(), e.getId(), e.getZonaAfectada(), e.getMunicipio().getNombre(),
                e.getEstado(), l.getId(), l.getTipoRecurso(), l.getDescripcion(), o.getOng().getNombre(),
                o.getCantidadOfrecida(), l.getUnidadMedida(), d.getComprometidoEn(), d.getMonitoreadoEn(),
                d.getFinalizadoEn(), d.getLiberadoEn(), d.getSincronizadoEn(),
                !ong && !pendiente && d.getMonitoreadoEn() == null && d.isMonitoreoDisponible(),
                ong && !pendiente && d.getMonitoreadoEn() != null && d.getFinalizadoEn() == null && d.isFinalizacionDisponible(),
                pendiente, registros);
    }

    private Usuario usuario(String email, Rol rol) {
        Usuario u = usuarios.findByEmail(email).orElseThrow(() -> new AuthorizationDeniedException("Usuario sin acceso"));
        if (u.getRol() != rol || rol == Rol.REPRESENTANTE_ONG && u.getOng() == null) {
            throw new AuthorizationDeniedException("El usuario no tiene acceso a estas actividades");
        }
        return u;
    }
    private Emergencia emergencia(Long id) {
        return emergencias.findById(id).orElseThrow(() -> new ResourceNotFoundException("Emergencia no encontrada"));
    }
    private Emergencia bloquear(Long id) {
        return emergencias.buscarConBloqueo(id).orElseThrow(() -> new ResourceNotFoundException("Emergencia no encontrada"));
    }
    private Oferta oferta(Long id) {
        return ofertas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Oferta no encontrada"));
    }
    private Despliegue despliegue(Long ofertaId) {
        return despliegues.findByOfertaId(ofertaId).orElseThrow(() -> new BusinessException("No hay un compromiso confirmado para esta oferta"));
    }
}

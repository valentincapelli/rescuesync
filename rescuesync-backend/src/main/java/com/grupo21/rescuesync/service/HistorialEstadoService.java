package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.HistorialEstado;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.TipoEntidadHistorial;
import com.grupo21.rescuesync.repository.HistorialEstadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Único punto para cambiar el estado de Emergencia, Lote y Oferta dejando rastro en
 * {@code historial_estados}. Los indicadores (E4) dependen de esto.
 *
 * <p>Regla del equipo: no llamar a {@code setEstado(...)} directamente en esas entidades;
 * usar {@code cambiarEstado(...)} (entidad ya guardada) o, al crearla, {@code setEstado} +
 * {@code save} + {@code registrarAlta(...)}.
 *
 * <p>Corre dentro de la transacción del llamador: si la operación de negocio hace rollback,
 * el historial también.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class HistorialEstadoService {

    private final HistorialEstadoRepository historialRepository;

    public void registrarAlta(Emergencia emergencia) {
        registrar(TipoEntidadHistorial.EMERGENCIA, emergencia.getId(), emergencia.getId(),
                null, emergencia.getEstado());
    }

    public void registrarAlta(Lote lote) {
        registrar(TipoEntidadHistorial.LOTE, lote.getId(), lote.getEmergencia().getId(),
                null, lote.getEstado());
    }

    public void registrarAlta(Oferta oferta) {
        registrar(TipoEntidadHistorial.OFERTA, oferta.getId(), oferta.getLote().getEmergencia().getId(),
                null, oferta.getEstado());
    }

    /** Cambia el estado y lo registra. No hace nada si el estado no cambia. */
    public void cambiarEstado(Emergencia emergencia, EstadoEmergencia nuevo) {
        EstadoEmergencia anterior = emergencia.getEstado();
        if (anterior == nuevo) {
            return;
        }
        emergencia.setEstado(nuevo);
        registrar(TipoEntidadHistorial.EMERGENCIA, emergencia.getId(), emergencia.getId(), anterior, nuevo);
    }

    /** Cambia el estado y lo registra. No hace nada si el estado no cambia. */
    public void cambiarEstado(Lote lote, EstadoLote nuevo) {
        EstadoLote anterior = lote.getEstado();
        if (anterior == nuevo) {
            return;
        }
        lote.setEstado(nuevo);
        registrar(TipoEntidadHistorial.LOTE, lote.getId(), lote.getEmergencia().getId(), anterior, nuevo);
    }

    /** Cambia el estado y lo registra. No hace nada si el estado no cambia. */
    public void cambiarEstado(Oferta oferta, EstadoOferta nuevo) {
        EstadoOferta anterior = oferta.getEstado();
        if (anterior == nuevo) {
            return;
        }
        oferta.setEstado(nuevo);
        registrar(TipoEntidadHistorial.OFERTA, oferta.getId(), oferta.getLote().getEmergencia().getId(),
                anterior, nuevo);
    }

    private void registrar(TipoEntidadHistorial tipo, Long entidadId, Long emergenciaId,
                           Enum<?> anterior, Enum<?> nuevo) {
        Objects.requireNonNull(entidadId, "La entidad debe estar guardada antes de registrar su estado");
        historialRepository.save(new HistorialEstado(tipo, entidadId, emergenciaId, anterior, nuevo, Instant.now()));
    }
}

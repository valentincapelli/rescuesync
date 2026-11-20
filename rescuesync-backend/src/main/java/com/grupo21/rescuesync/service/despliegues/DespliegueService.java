package com.grupo21.rescuesync.service.despliegues;

import com.grupo21.rescuesync.client.DespliegueBonitaClient;
import com.grupo21.rescuesync.client.EstadoDespliegueBonita;
import com.grupo21.rescuesync.dto.despliegues.DespliegueResponse;
import com.grupo21.rescuesync.dto.despliegues.SeleccionOfertaEstadoResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.model.TipoNovedadDespliegue;
import com.grupo21.rescuesync.service.SeleccionBonitaCoordinador;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** Orquestación HTTP fuera de las transacciones de persistencia. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DespliegueService {
    private final DespliegueRegistroService registro;
    private final DespliegueBonitaClient bonita;
    private final SeleccionBonitaCoordinador seleccion;
    private final SeleccionBonitaRegistro selecciones;

    public List<SeleccionOfertaEstadoResponse> listarSelecciones(Long emergenciaId, String email) {
        registro.listar(emergenciaId, email); // Reutiliza autorización de Centro Coordinador.
        return selecciones.estados(emergenciaId);
    }

    public List<DespliegueResponse> listar(Long emergenciaId, String email, boolean actualizar) {
        // Autorizar antes de consultar sistemas externos.
        List<DespliegueResponse> actuales = registro.listar(emergenciaId, email);
        if (!actualizar) return actuales;
        sincronizarCaso(emergenciaId);
        return registro.listar(emergenciaId, email);
    }

    public List<DespliegueResponse> propias(String email, boolean actualizar) {
        List<DespliegueResponse> actuales = registro.propias(email);
        if (!actualizar) return actuales;
        sincronizarCasos(registro.casosPropios(email));
        return registro.propias(email);
    }

    /**
     * Sincroniza cada caso por separado: un caso que falla (por ejemplo, uno que ya no existe en
     * Bonita) no debe impedir actualizar los demás. Si fallan todos se propaga el primer error, para
     * que una caída real de Bonita siga informándose y no parezca un éxito.
     */
    private void sincronizarCasos(List<Long> emergenciaIds) {
        RuntimeException primerError = null;
        int fallidos = 0;
        for (Long id : emergenciaIds) {
            try {
                sincronizarCaso(id);
            } catch (RuntimeException ex) {
                fallidos++;
                if (primerError == null) primerError = ex;
                log.warn("No se pudo sincronizar el seguimiento de la emergencia {}: {}", id, ex.getMessage());
            }
        }
        if (primerError != null && fallidos == emergenciaIds.size()) throw primerError;
    }

    public DespliegueResponse registrar(Long ofertaId, String email, TipoNovedadDespliegue tipo, String observacion) {
        Long emergenciaId = registro.autorizarOferta(ofertaId, email, tipo);
        var anterior = registro.accionRegistrada(ofertaId, tipo);
        if (anterior.isPresent()) {
            if (!anterior.get().confirmada()) intentarSincronizar(emergenciaId, anterior.get().id());
            return registro.obtener(ofertaId, email, tipo);
        }
        sincronizarCaso(emergenciaId);
        var accion = registro.registrar(ofertaId, email, tipo, observacion);
        if (!accion.confirmada()) {
            intentarSincronizar(emergenciaId, accion.id());
        }
        return registro.obtener(ofertaId, email, tipo);
    }

    private void intentarSincronizar(Long emergenciaId, Long accionId) {
        try {
            sincronizarCaso(emergenciaId);
        } catch (BonitaIntegrationException ex) {
            // La intención está guardada. No afirmar éxito ni perder el pedido por un timeout.
            log.warn("Acción de despliegue {} pendiente de confirmación", accionId);
        }
    }

    public void sincronizarCaso(Long emergenciaId) {
        var caso = registro.caso(emergenciaId);
        EstadoDespliegueBonita estado = bonita.consultar(emergenciaId, caso.caseId());
        registro.aplicar(emergenciaId, estado);
        for (var accion : registro.pendientes(emergenciaId)) {
            // Solo se reintenta el mismo ID, nunca una tarea del siguiente ciclo.
            if (Objects.equals(accion.taskId(), estado.tareaMonitoreoId())
                    || Objects.equals(accion.taskId(), estado.tareaFinalizacionId())) {
                try {
                    bonita.completar(accion.taskId());
                } catch (BonitaIntegrationException ex) {
                    log.warn("No se confirmó la ejecución de la acción de despliegue {}", accion.id());
                }
                estado = bonita.consultar(emergenciaId, caso.caseId());
                registro.aplicar(emergenciaId, estado);
            }
        }
        try {
            seleccion.liberacionConfirmada(emergenciaId, estado);
        } catch (BonitaIntegrationException ex) {
            log.warn("La siguiente selección de la emergencia {} queda pendiente", emergenciaId);
        }
    }
}

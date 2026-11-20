package com.grupo21.rescuesync.service;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.dto.CoberturaResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.model.DecisionCobertura;
import com.grupo21.rescuesync.model.OperacionCobertura;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Coordina las tareas de cobertura fuera de las transacciones locales. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoberturaService {
    private final CoberturaRegistro registro;
    private final BonitaClient bonita;

    public CoberturaResponse consultar(Long id) { return registro.resumen(id); }

    public CoberturaResponse ejecutar(Long id, OperacionCobertura operacion, DecisionCobertura decision, String resolucion) {
        Long caseId = registro.validar(id, operacion, decision);
        if (registro.pendiente(id) != null) return sincronizar(id);
        validarCaso(id, caseId);
        var tareas = bonita.buscarTareasActivas(caseId.toString(), operacion.tarea());
        if (tareas.size() != 1) throw new BonitaIntegrationException(
                "No se encontró una única tarea disponible: " + operacion.tarea(), null);
        var envio = registro.reservar(id, caseId, tareas.getFirst(), operacion, decision, resolucion);
        if (envio != null) enviar(envio);
        return registro.resumen(id);
    }

    public CoberturaResponse sincronizar(Long id) {
        var pendiente = registro.pendiente(id);
        if (pendiente == null) return registro.resumen(id);
        validarCaso(id, pendiente.caseId());
        boolean completada = bonita.listarActividadesArchivadas(pendiente.caseId().toString()).stream()
                .anyMatch(t -> pendiente.taskId().equals(t.sourceObjectId())
                        && pendiente.operacion().tarea().equals(t.name()) && "completed".equals(t.state()));
        if (completada) {
            registro.confirmar(pendiente);
        } else {
            var ready = bonita.buscarTareasActivas(pendiente.caseId().toString(), pendiente.operacion().tarea());
            if (ready.size() == 1 && ready.getFirst().equals(pendiente.taskId())) {
                var renovado = registro.renovar(pendiente);
                if (renovado != null) enviar(renovado);
            }
        }
        return registro.resumen(id);
    }

    private void enviar(CoberturaRegistro.Envio envio) {
        Map<String, Object> contrato = envio.operacion() == OperacionCobertura.EVALUAR
                ? Map.of("decisionCoberturaInput", envio.decision().name()) : Map.of();
        try {
            bonita.completarTareaPorId(envio.taskId(), contrato);
            registro.confirmar(envio);
        } catch (BonitaIntegrationException ex) {
            // La intención ya está guardada. Ante resultado incierto se reconcilia el mismo task ID.
            log.warn("Cobertura de emergencia {} pendiente de confirmación", envio.emergenciaId());
        }
    }

    private void validarCaso(Long id, Long caseId) {
        String caso = caseId.toString();
        if (bonita.buscarCaso(caso).isEmpty()
                || !id.toString().equals(bonita.leerVariable(caso, "emergenciaId", false).orElse(null))) {
            throw new BonitaIntegrationException("El caso activo de Bonita no corresponde a la emergencia", null);
        }
    }
}

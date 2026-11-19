package com.grupo21.rescuesync.service.despliegues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Recupera cambios hechos en Bonita y operaciones pendientes tras reiniciar el backend. */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bonita.despliegues.sincronizacion-habilitada", havingValue = "true")
public class DespliegueSincronizador {
    private final DespliegueRegistroService registro;
    private final DespliegueService servicio;

    @Scheduled(fixedDelayString = "${bonita.despliegues.intervalo-ms:30000}", initialDelayString = "${bonita.despliegues.intervalo-ms:30000}")
    public void sincronizar() {
        for (Long id : registro.casosPendientes()) {
            try {
                servicio.sincronizarCaso(id);
            } catch (RuntimeException ex) {
                log.warn("Seguimiento de emergencia {} pendiente: {}", id, ex.getMessage());
            }
        }
    }
}

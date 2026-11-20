package com.grupo21.rescuesync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicBoolean;

/** Recupera únicamente intenciones sin incidente; el registro serializa cada emergencia en la base. */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bonita.seleccion.recuperacion-habilitada", havingValue = "true")
public class SeleccionBonitaSincronizador {
    private final SeleccionBonitaRegistro registro;
    private final SeleccionBonitaCoordinador coordinador;
    private final AtomicBoolean ejecutando = new AtomicBoolean();

    @Value("${bonita.seleccion.maximo-casos:20}")
    private int maximoCasos;

    @Scheduled(fixedDelayString = "${bonita.seleccion.intervalo-ms:30000}",
            initialDelayString = "${bonita.seleccion.intervalo-ms:30000}")
    public void recuperar() {
        if (!ejecutando.compareAndSet(false, true)) return;
        try {
            for (Long id : registro.recuperables(Math.max(1, maximoCasos))) {
                try {
                    coordinador.despachar(id);
                } catch (RuntimeException ex) {
                    log.warn("Selección de emergencia {} pendiente de recuperación", id);
                }
            }
        } finally {
            ejecutando.set(false);
        }
    }
}

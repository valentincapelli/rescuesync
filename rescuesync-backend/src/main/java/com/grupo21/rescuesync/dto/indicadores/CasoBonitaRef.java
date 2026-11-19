package com.grupo21.rescuesync.dto.indicadores;

/** Emergencia con instancia en Bonita: es lo que recibe el cálculo de tiempos por etapa. */
public record CasoBonitaRef(Long emergenciaId, Long bonitaCaseId) {}

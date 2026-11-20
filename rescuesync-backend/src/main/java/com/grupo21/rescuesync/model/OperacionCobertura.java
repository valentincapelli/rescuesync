package com.grupo21.rescuesync.model;

public enum OperacionCobertura {
    EVALUAR("Evaluar cobertura de lotes"),
    REPUBLICAR("Publicar convocatoria"),
    RESOLVER("Resolver cobertura incompleta");

    private final String tarea;

    OperacionCobertura(String tarea) { this.tarea = tarea; }

    public String tarea() { return tarea; }
}

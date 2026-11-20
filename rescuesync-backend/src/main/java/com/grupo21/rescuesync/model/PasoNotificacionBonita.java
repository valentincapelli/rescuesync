package com.grupo21.rescuesync.model;

public enum PasoNotificacionBonita {
    ENVIAR("Notificar a ONG"), RECIBIR("Recibir notificación");

    private final String tarea;
    PasoNotificacionBonita(String tarea) { this.tarea = tarea; }
    public String tarea() { return tarea; }
}

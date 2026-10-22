package com.example.staycontrol.model;

public enum EstadoLimpieza {
    PENDIENTE, EN_PROCESO, COMPLETADA, CANCELADA;

    public boolean cerrada() { return this == COMPLETADA || this == CANCELADA; }
}

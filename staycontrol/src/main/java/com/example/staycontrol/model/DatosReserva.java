package com.example.staycontrol.model;

/** Fechas civiles ISO yyyy-MM-dd: sin conversiones de zona horaria en MongoDB. */
public record DatosReserva(String habitacionId, String huesped, int personas,
    String fechaEntrada, String fechaSalida) { }

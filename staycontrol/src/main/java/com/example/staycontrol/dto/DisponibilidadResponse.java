package com.example.staycontrol.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DisponibilidadResponse(String habitacionId, String numeroHabitacion,
    LocalDate fechaEntrada, LocalDate fechaSalida, int personas, long noches,
    boolean disponible, String motivo, BigDecimal precioNoche, BigDecimal total) { }

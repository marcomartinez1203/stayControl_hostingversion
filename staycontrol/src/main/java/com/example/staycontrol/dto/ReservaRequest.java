package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.DatosReserva;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ReservaRequest(String habitacionId, String huesped, Integer personas,
    LocalDate fechaEntrada, LocalDate fechaSalida) {
    public DatosReserva validarYNormalizar() {
        if (habitacionId == null || !habitacionId.matches("[a-f0-9]{64}")) {
            throw ApiException.invalida("habitacionId debe ser el id devuelto por la API de habitaciones.");
        }
        String nombre = huesped == null ? "" : huesped.strip();
        if (nombre.isEmpty() || nombre.length() > 120 || nombre.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("huesped debe tener entre 1 y 120 caracteres sin controles.");
        }
        validarEstancia(habitacionId, personas, fechaEntrada, fechaSalida);
        return new DatosReserva(habitacionId, nombre, personas, fechaEntrada.toString(), fechaSalida.toString());
    }

    public static void validarEstancia(String habitacionId, Integer personas, LocalDate fechaEntrada, LocalDate fechaSalida) {
        if (habitacionId == null || !habitacionId.matches("[a-f0-9]{64}")) {
            throw ApiException.invalida("habitacionId debe ser el id devuelto por la API de habitaciones.");
        }
        if (personas == null || personas < 1 || personas > 20) {
            throw ApiException.invalida("personas debe ser un entero entre 1 y 20.");
        }
        if (fechaEntrada == null || fechaSalida == null || !fechaSalida.isAfter(fechaEntrada)
            || fechaEntrada.getYear() < 2000 || fechaSalida.getYear() > 2100
            || ChronoUnit.DAYS.between(fechaEntrada, fechaSalida) > 365) {
            throw ApiException.invalida("Fechas entre 2000 y 2100; la estancia debe durar entre 1 y 365 noches.");
        }
    }
}

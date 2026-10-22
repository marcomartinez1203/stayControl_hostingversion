package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;
import com.example.staycontrol.model.DatosHabitacion;
import com.example.staycontrol.model.EstadoHabitacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public record HabitacionRequest(
    String numero, String tipo, Integer capacidad, BigDecimal precioNoche, EstadoHabitacion estado
) {
    public DatosHabitacion validarYNormalizar() {
        String numeroNormalizado = numero == null ? "" : numero.strip().toUpperCase(Locale.ROOT);
        String tipoNormalizado = tipo == null ? "" : tipo.strip();
        if (!numeroNormalizado.matches("[A-Z0-9][A-Z0-9-]{0,19}")) {
            throw ApiException.invalida("numero debe tener entre 1 y 20 letras, dígitos o guiones.");
        }
        if (tipoNormalizado.isEmpty() || tipoNormalizado.length() > 60
            || tipoNormalizado.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("tipo es obligatorio y admite hasta 60 caracteres sin controles.");
        }
        if (capacidad == null || capacidad < 1 || capacidad > 20) {
            throw ApiException.invalida("capacidad debe ser un entero entre 1 y 20.");
        }
        if (precioNoche == null || precioNoche.signum() <= 0
            || precioNoche.compareTo(new BigDecimal("999999999.99")) > 0) {
            throw ApiException.invalida("precioNoche debe ser mayor que cero y no superar 999999999.99.");
        }
        BigDecimal precioNormalizado;
        try {
            precioNormalizado = precioNoche.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw ApiException.invalida("precioNoche admite como máximo dos decimales significativos.");
        }
        if (estado == null) {
            throw ApiException.invalida("estado es obligatorio: DISPONIBLE, OCUPADA, LIMPIEZA o MANTENIMIENTO.");
        }
        return new DatosHabitacion(numeroNormalizado, tipoNormalizado, capacidad, precioNormalizado, estado);
    }
}

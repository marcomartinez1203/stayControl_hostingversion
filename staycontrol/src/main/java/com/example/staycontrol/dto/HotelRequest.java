package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;

public record HotelRequest(String nombreHotel) {
    public String validarYNormalizar() { return normalizar(nombreHotel); }

    public static String normalizar(String valor) {
        String nombre = valor == null ? "" : valor.strip();
        if (nombre.isEmpty() || nombre.length() > 120 || nombre.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("El nombre del hotel debe tener entre 1 y 120 caracteres sin controles.");
        }
        return nombre;
    }
}

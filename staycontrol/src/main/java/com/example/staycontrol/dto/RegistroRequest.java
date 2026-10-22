package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;

public record RegistroRequest(String usuario, String email, String password, String nombreHotel) {
    public String hotelNormalizado() {
        return HotelRequest.normalizar(nombreHotel);
    }
    public String nombreNormalizado() {
        String nombre = usuario == null ? "" : usuario.strip();
        if (nombre.isEmpty() || nombre.length() > 120 || nombre.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalida("El nombre de usuario debe tener entre 1 y 120 caracteres sin controles.");
        }
        return nombre;
    }

    public void validarPassword() {
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 128) {
            throw ApiException.invalida("La contraseña debe tener entre 12 y 128 caracteres y no ser solo espacios.");
        }
    }

    @Override public String toString() { return "RegistroRequest[credenciales ocultas]"; }
}

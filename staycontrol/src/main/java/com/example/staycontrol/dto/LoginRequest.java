package com.example.staycontrol.dto;

import com.example.staycontrol.exception.ApiException;
import java.util.Locale;

public record LoginRequest(String email, String password) {
    public static String normalizarEmail(String email) {
        String normalizado = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 254 || !normalizado.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) {
            throw ApiException.invalida("El email no tiene un formato válido.");
        }
        return normalizado;
    }

    public void validar() {
        normalizarEmail(email);
        if (password == null || password.isEmpty() || password.length() > 128) {
            throw ApiException.invalida("La contraseña es obligatoria y admite hasta 128 caracteres.");
        }
    }

    @Override public String toString() { return "LoginRequest[credenciales ocultas]"; }
}

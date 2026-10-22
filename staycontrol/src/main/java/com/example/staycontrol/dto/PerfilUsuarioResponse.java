package com.example.staycontrol.dto;

/** Datos públicos de la cuenta autenticada, sin credenciales ni claves internas. */
public record PerfilUsuarioResponse(String email, String usuario, String nombreHotel) { }

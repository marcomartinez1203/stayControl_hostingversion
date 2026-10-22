package com.example.staycontrol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("usuarios")
public record Usuario(@Id String email, @JsonIgnore String passwordHash, String usuario,
    String rol, @JsonIgnore String registroKey, String nombreHotel) {
    public Usuario(String email, String passwordHash) {
        this(email, passwordHash, null, "ADMIN", null, null);
    }
    public Usuario(String email, String passwordHash, String usuario, String rol, String registroKey) {
        this(email, passwordHash, usuario, rol, registroKey, null);
    }

    public String rolEfectivo() {
        // Los documentos anteriores al registro público fueron provisionados como ADMIN.
        return rol == null && registroKey == null ? "ADMIN" : "ADMIN".equals(rol) ? "ADMIN" : "USER";
    }
    @Override public String toString() { return "Usuario[credenciales ocultas]"; }
}

import { requestJson } from "./apiService.js";

export function iniciarSesion(email, password) {
    return requestJson("/api/v1/auth/login", { method: "POST", body: { email, password } });
}

export function consultarSesion() {
    return requestJson("/api/v1/auth/me");
}

export function actualizarHotel(nombreHotel) {
    return requestJson("/api/v1/auth/me/hotel", { method: "PUT", body: { nombreHotel } });
}

export function cerrarSesion() {
    return requestJson("/api/v1/auth/logout", { method: "POST" });
}

export function registrarUsuario(datos, clave) {
    return requestJson("/api/v1/auth/registro", {
        method: "POST", body: datos, headers: { "Idempotency-Key": clave }
    });
}

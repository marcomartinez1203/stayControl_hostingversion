import { actualizarHotel } from "../services/authService.js";

export function conectarCuenta(perfil, { bloqueado, cambiarBloqueo, cambios }) {
    const panel = document.querySelector(".api-cuenta-panel");
    const form = document.createElement("form"); form.className = "api-hotel-form";
    const label = document.createElement("label"); label.textContent = "Nombre del hotel";
    const input = document.createElement("input");
    input.name = "nombreHotel";
    input.type = "text"; input.required = true; input.maxLength = 120;
    input.autocomplete = "organization"; input.value = perfil.nombreHotel || "";
    input.placeholder = "Escribe el nombre de tu hotel";
    label.append(input);
    const guardar = document.createElement("button"); guardar.type = "submit"; guardar.textContent = "Guardar hotel";
    const mensaje = document.createElement("p"); mensaje.className = "api-hotel-mensaje";
    mensaje.setAttribute("role", "status"); mensaje.setAttribute("aria-live", "polite");
    form.append(label, guardar, mensaje);
    panel.insertBefore(form, document.getElementById("salir"));
    const proteccion = cambios.vigilar(form);
    input.addEventListener("input", () => { input.setCustomValidity(""); mensaje.textContent = ""; });
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (bloqueado()) { mensaje.textContent = "Espera a que termine la operación actual."; return; }
        input.setCustomValidity(!input.value.trim() || /[\u0000-\u001f\u007f-\u009f]/u.test(input.value)
            ? "Escribe un nombre sin caracteres de control." : "");
        if (!form.reportValidity()) { return; }
        cambiarBloqueo(true); input.disabled = guardar.disabled = true;
        form.setAttribute("aria-busy", "true"); mensaje.textContent = "Guardando…";
        proteccion.enviado();
        try {
            const datos = await actualizarHotel(input.value.trim());
            input.value = datos.nombreHotel;
            proteccion.guardado();
            const titulo = document.getElementById("nombreHotelDashboard");
            if (titulo) { titulo.textContent = datos.nombreHotel; titulo.title = datos.nombreHotel; }
            mensaje.textContent = "Nombre del hotel guardado.";
        } catch (error) {
            if (error.status === 401) { window.location.assign("/login"); return; }
            mensaje.textContent = error.status ? error.message : "No se pudo confirmar el cambio. Reintenta con el mismo nombre.";
        } finally {
            cambiarBloqueo(false); input.disabled = guardar.disabled = false; form.removeAttribute("aria-busy");
        }
    });
}

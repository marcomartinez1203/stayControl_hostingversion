import { registrarUsuario } from "../services/authService.js";

const form = document.getElementById("registroForm");
const usuario = document.getElementById("usuario");
const nombreHotel = document.getElementById("nombreHotel");
const email = document.getElementById("email");
const password = document.getElementById("password");
const confirmacion = document.getElementById("confirmPassword");
const boton = document.getElementById("registroButton");
const aviso = document.getElementById("registroNotice");
let clave = null;
let completado = false;

function mensaje(texto, estado) {
    aviso.textContent = texto;
    aviso.dataset.estado = estado;
}

form.addEventListener("input", event => {
    // Un reintento sin editar conserva la clave; otro contenido inicia otra operación.
    if (event.target !== confirmacion) { clave = null; }
    for (const input of form.querySelectorAll("input")) { input.setCustomValidity(""); }
});

form.addEventListener("submit", async event => {
    event.preventDefault();
    if (boton.disabled || completado) { return; }
    usuario.setCustomValidity(usuario.value.trim() ? "" : "Escribe un nombre de usuario.");
    nombreHotel.setCustomValidity(nombreHotel.value.trim() ? "" : "Escribe el nombre del hotel.");
    password.setCustomValidity(password.value.trim() && password.value.length >= 12
        && password.value.length <= 128 ? "" : "La contraseña debe tener entre 12 y 128 caracteres y no ser solo espacios.");
    confirmacion.setCustomValidity(password.value === confirmacion.value ? "" : "Las contraseñas no coinciden.");
    if (!form.reportValidity()) { return; }

    boton.disabled = true;
    boton.textContent = "Creando cuenta…";
    form.setAttribute("aria-busy", "true");
    const inputs = [...form.querySelectorAll("input")];
    inputs.forEach(input => { input.disabled = true; });
    mensaje("Enviando registro…", "pendiente");
    try {
        if (!clave) {
            const bytes = crypto.getRandomValues(new Uint8Array(16));
            clave = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
        }
        await registrarUsuario({ usuario: usuario.value.trim(), email: email.value.trim().toLowerCase(),
            password: password.value, nombreHotel: nombreHotel.value.trim() }, clave);
        completado = true;
        form.reset();
        clave = null;
        mensaje("Cuenta creada. Inicia sesión para administrar tu hotel. Tus habitaciones, reservas y finanzas son privadas de esta cuenta.", "exito");
    } catch (error) {
        mensaje(error.status ? error.message : "No se pudo confirmar el registro. Reintenta sin cambiar los datos para evitar duplicados.", "error");
    } finally {
        form.removeAttribute("aria-busy");
        inputs.forEach(input => { input.disabled = completado; });
        boton.disabled = completado;
        boton.textContent = completado ? "Cuenta creada" : "Crear cuenta";
    }
});

boton.disabled = false;

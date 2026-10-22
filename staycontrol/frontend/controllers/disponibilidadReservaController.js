import { consultarDisponibilidad } from "../services/negocioService.js";

// La cotización no reserva fechas: el POST vuelve a validar dentro de su transacción.
export function conectarDisponibilidad(form) {
    const nombres = ["habitacionId", "personas", "fechaEntrada", "fechaSalida"];
    const boton = document.createElement("button");
    boton.type = "button"; boton.textContent = "Consultar disponibilidad y precio";
    const resultado = document.createElement("p");
    resultado.className = "api-aviso";
    resultado.setAttribute("role", "status");
    resultado.setAttribute("aria-live", "polite");
    let version = 0;
    function invalidar() {
        version++;
        boton.removeAttribute("aria-busy");
        resultado.dataset.error = "false";
        resultado.textContent = "Datos modificados. Consulta de nuevo la disponibilidad.";
    }
    form.addEventListener("input", invalidar);
    // El selector de habitaciones cambia el ID por código y emite change, no input.
    form.addEventListener("change", invalidar);
    boton.addEventListener("click", async () => {
        if (form.getAttribute("aria-busy") === "true") { return; }
        const parametros = {};
        for (const nombre of nombres) {
            const input = form.elements.namedItem(nombre);
            if (!input.reportValidity()) { return; }
            parametros[nombre] = input.value;
        }
        const actual = ++version;
        resultado.dataset.error = "false";
        resultado.textContent = "Consultando disponibilidad…";
        boton.setAttribute("aria-busy", "true");
        try {
            const datos = await consultarDisponibilidad(parametros);
            if (actual !== version || !form.isConnected) { return; }
            resultado.dataset.error = String(!datos.disponible);
            resultado.textContent = datos.disponible
                ? `Hab. ${datos.numeroHabitacion}: disponible. ${datos.noches} noches × ${datos.precioNoche} = ${datos.total}. Precio orientativo según la tarifa de la habitación; no es un cobro ni garantiza disponibilidad. Se confirma al guardar.`
                : `No disponible: ${datos.motivo}`;
        } catch (error) {
            if (actual !== version || !form.isConnected) { return; }
            if (error.status === 401) { window.location.assign("/login"); return; }
            resultado.dataset.error = "true";
            resultado.textContent = error.status ? error.message : "No se pudo consultar. Revisa la conexión y reintenta.";
        } finally {
            if (actual === version) { boton.removeAttribute("aria-busy"); }
        }
    });
    form.append(boton, resultado);
}

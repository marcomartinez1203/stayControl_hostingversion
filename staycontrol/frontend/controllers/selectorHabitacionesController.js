import { consultar } from "../services/negocioService.js";

export function conectarSelectorHabitaciones(form, habitacion, { bloqueado, alError, inicial = null }) {
    const panel = document.createElement("div"); panel.className = "selector-habitaciones";
    const label = document.createElement("label"); label.textContent = "Buscar habitación por número o tipo";
    const buscar = document.createElement("input"); buscar.type = "search"; buscar.maxLength = 60;
    buscar.placeholder = "Ej.: 101, Doble, Suite"; label.append(buscar);
    const selectLabel = document.createElement("label"); selectLabel.textContent = "Habitación";
    const select = document.createElement("select"); selectLabel.append(select);
    const info = document.createElement("p"); info.className = "api-aviso"; info.setAttribute("role", "status");
    const elegida = document.createElement("p"); elegida.className = "api-aviso";
    const acciones = document.createElement("div"); acciones.className = "business-actions";
    const paginas = document.createElement("div"); paginas.className = "business-actions";
    let pagina = 0, texto = "", total = 0, version = 0, cargando = false;
    const habitaciones = new Map();
    if (inicial) { habitaciones.set(inicial.id, inicial); }
    let resultados = [];
    function boton(texto, accion) {
        const b = document.createElement("button"); b.type = "button"; b.textContent = texto;
        b.addEventListener("click", () => { if (!bloqueado()) { accion(); } }); return b;
    }
    function aplicar() {
        if (!buscar.reportValidity()) { return; }
        texto = buscar.value.trim(); pagina = 0; cargar();
    }
    const buscarBoton = boton("Buscar habitaciones", aplicar);
    const limpiar = boton("Ver todas", () => { buscar.value = ""; aplicar(); });
    const anterior = boton("Anteriores", () => { if (pagina > 0 && !cargando) { pagina--; cargar(); } });
    const siguiente = boton("Siguientes", () => { if (!cargando && (pagina + 1) * 20 < total) { pagina++; cargar(); } });
    acciones.append(buscarBoton, limpiar); paginas.append(anterior, siguiente);
    panel.append(label, acciones, selectLabel, elegida, info, paginas); form.append(panel);
    function actualizarBloqueo() {
        buscar.disabled = buscarBoton.disabled = limpiar.disabled = bloqueado();
        select.disabled = bloqueado() || cargando;
        anterior.disabled = bloqueado() || cargando || pagina === 0;
        siguiente.disabled = bloqueado() || cargando || (pagina + 1) * 20 >= total || pagina >= 10000;
    }
    function descripcion(h) { return `Hab. ${h.numero} · ${h.tipo} · ${h.estado} · Capacidad ${h.capacidad}`; }
    function pintarSeleccion() {
        select.replaceChildren(new Option("Selecciona una habitación", ""));
        const id = habitacion.value;
        if (id && !resultados.some(h => h.id === id)) {
            select.append(new Option(habitaciones.has(id) ? descripcion(habitaciones.get(id)) + " (seleccionada)" : "ID introducido manualmente", id));
        }
        for (const h of resultados) { select.append(new Option(descripcion(h), h.id)); }
        select.value = id;
        elegida.textContent = !id ? "Ninguna habitación seleccionada."
            : habitaciones.has(id) ? `Seleccionada: ${descripcion(habitaciones.get(id))}` : "ID manual: el servidor verificará la habitación al consultar o guardar.";
    }
    // Buscar no modifica el cuerpo de creación ni invalida su clave de idempotencia.
    buscar.addEventListener("input", event => event.stopPropagation());
    buscar.addEventListener("change", event => event.stopPropagation());
    buscar.addEventListener("keydown", event => {
        if (event.key === "Enter") { event.preventDefault(); event.stopPropagation(); if (!bloqueado()) { aplicar(); } }
    });
    select.addEventListener("change", () => {
        habitacion.value = select.value;
        habitacion.dispatchEvent(new Event("input", { bubbles: true }));
    });
    habitacion.addEventListener("input", pintarSeleccion);
    async function cargar() {
        const actual = ++version;
        cargando = true; actualizarBloqueo(); info.textContent = "Buscando habitaciones…";
        info.dataset.error = "false";
        try {
            const datos = await consultar("habitaciones", { pagina, tamano: 20, ...(texto ? { buscar: texto } : {}) });
            if (actual !== version || !form.isConnected) { return; }
            resultados = datos.items; total = datos.total;
            for (const h of resultados) { habitaciones.set(h.id, h); }
            pintarSeleccion();
            info.textContent = total ? `${total} habitaciones · Página ${pagina + 1}${texto ? ` · «${texto}»` : ""}`
                : "No se encontraron habitaciones. Cambia la búsqueda o crea una en Habitaciones.";
        } catch (error) {
            if (actual !== version || !form.isConnected) { return; }
            resultados = []; total = 0; pintarSeleccion();
            info.dataset.error = "true"; info.textContent = "No se pudieron cargar las habitaciones. Pulsa Buscar habitaciones para reintentar.";
            alError(error);
        } finally {
            if (actual === version) { cargando = false; actualizarBloqueo(); }
        }
    }
    pintarSeleccion(); actualizarBloqueo();
    return { iniciar: cargar, actualizarBloqueo };
}

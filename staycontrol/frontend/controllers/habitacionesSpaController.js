import { consultar, obtenerHabitacion, guardar, eliminar } from "../services/negocioService.js";
import { consultarSesion, cerrarSesion } from "../services/authService.js";
import { prepararDiseno } from "./disenoOriginal.js";
import { conectarCuenta } from "./cuentaController.js";
import { protegerCambios } from "./cambiosPendientesController.js";

const estados = ["DISPONIBLE", "OCUPADA", "LIMPIEZA", "MANTENIMIENTO"];
const filtrosEstado = { disponibles: "DISPONIBLE", ocupadas: "OCUPADA", limpieza: "LIMPIEZA", mantenimiento: "MANTENIMIENTO" };
const nodo = (tag, texto, clase) => {
    const el = document.createElement(tag);
    if (texto != null) el.textContent = texto;
    if (clase) el.className = clase;
    return el;
};

// SPA limitada a Habitaciones. Cabecera/cuenta/navegación permanecen montadas.
export async function iniciarHabitacionesSpa() {
    const diseno = prepararDiseno("habitaciones");
    const main = document.querySelector("main");
    const aviso = document.getElementById("estadoPagina");
    const barraFiltros = document.querySelector(".filter-scroll");
    const buscar = document.querySelector(".search-btn");
    const outlet = nodo("section", null, "habitaciones-spa");
    outlet.setAttribute("aria-label", "Contenido de habitaciones");
    diseno.lista.remove();
    main.append(outlet);
    const eventos = new AbortController();
    let ocupado = false, vista = null, activa = null, restaurando = false;
    let ultimaLista = "#/listado";
    const informar = (texto, error = false) => {
        aviso.textContent = texto; aviso.dataset.error = String(error);
    };
    const cambios = protegerCambios({ bloqueado: () => ocupado,
        alBloquear: () => informar("Espera a que termine la operación actual.") });
    function fallo(error) {
        if (error.name === "AbortError") return;
        if (error.status === 401) { cambios.autorizarSalida(); window.location.assign("/login"); return; }
        informar(error.status ? error.message : "No se pudo confirmar la operación. Comprueba la conexión y reintenta.", true);
    }
    function desmontar() {
        if (!vista) return;
        vista.eventos.abort(); // Retira listeners y cancela únicamente lecturas pendientes.
        vista.proteccion?.retirar();
        vista = null;
        outlet.replaceChildren();
    }
    function crearVista() {
        desmontar();
        vista = { eventos: new AbortController(), form: null, proteccion: null };
        return vista;
    }
    function boton(texto, accion, actual = vista) {
        const el = nodo("button", texto); el.type = "button";
        el.addEventListener("click", accion, { signal: actual.eventos.signal });
        return el;
    }
    function puedeSalir() {
        if (ocupado) { informar("Espera a que termine la operación actual."); return false; }
        return !vista?.form || cambios.confirmar(vista.form);
    }
    function hashListado(params = {}) {
        const query = new URLSearchParams(params).toString();
        return "#/listado" + (query ? `?${query}` : "");
    }
    function ruta(hash) {
        let url;
        try { url = new URL(hash.slice(1) || "/listado", location.origin); }
        catch { return null; }
        if (url.origin !== location.origin) return null;
        if (url.pathname === "/listado") {
            const estado = url.searchParams.get("estado") || "";
            const pagina = Number(url.searchParams.get("pagina") || 0);
            if ((estado && !estados.includes(estado)) || !Number.isInteger(pagina) || pagina < 0 || pagina > 10000) return null;
            return { nombre: "listado", estado, pagina, buscar: (url.searchParams.get("buscar") || "").slice(0, 60) };
        }
        if (url.pathname === "/nueva") return { nombre: "nueva" };
        const editar = /^\/editar\/([a-f0-9]{64})$/.exec(url.pathname);
        return editar ? { nombre: "editar", id: editar[1] } : null;
    }
    function navegar(hash, { reemplazar = false, autorizado = false } = {}) {
        if (!activa || restaurando) return;
        if (!autorizado && !puedeSalir()) return;
        reemplazar ||= hash === activa.hash;
        const indice = reemplazar ? activa.indice : activa.indice + 1;
        const state = { ...history.state, habitacionesSpa: indice };
        history[reemplazar ? "replaceState" : "pushState"](state, "", hash);
        activa = { hash, indice };
        montar(hash);
    }
    function historial() {
        if (restaurando) {
            if (location.hash === activa.hash) restaurando = false;
            return;
        }
        const destino = history.state?.habitacionesSpa;
        if (location.hash === activa.hash) {
            if (Number.isInteger(destino)) activa.indice = destino;
            return;
        }
        if (!puedeSalir()) {
            // Deshacer Atrás/Adelante sin desmontar ni perder el formulario.
            if (Number.isInteger(destino) && destino !== activa.indice) {
                restaurando = true; history.go(activa.indice - destino);
            } else {
                history.replaceState({ ...history.state, habitacionesSpa: activa.indice }, "", activa.hash);
            }
            return;
        }
        const indice = Number.isInteger(destino) ? destino : activa.indice + 1;
        history.replaceState({ ...history.state, habitacionesSpa: indice }, "", location.hash || "#/listado");
        activa = { hash: location.hash, indice };
        montar(activa.hash);
    }
    async function montar(hash) {
        const actual = crearVista();
        const signal = actual.eventos.signal;
        const destino = ruta(hash);
        const listado = destino?.nombre === "listado";
        barraFiltros.hidden = !listado;
        buscar.hidden = !listado;
        main.scrollTop = 0;
        document.title = `StayControl - ${listado ? "Habitaciones" : destino?.nombre === "nueva" ? "Nueva habitación" : "Editar habitación"}`;
        informar("Cargando…");
        outlet.setAttribute("aria-busy", "true");
        try {
            if (!destino) {
                informar("Esta pantalla de Habitaciones no existe.", true);
                outlet.append(boton("Volver a habitaciones", () => navegar("#/listado")));
            } else if (listado) {
                ultimaLista = hash;
                await montarListado(destino, actual);
            } else {
                outlet.append(boton("Volver al listado", () => navegar(ultimaLista)));
                const item = destino.id ? await obtenerHabitacion(destino.id, { signal }) : null;
                if (signal.aborted) return;
                montarFormulario(item, actual);
                informar(destino.id ? "Edita los datos de la habitación." : "Completa los datos de la nueva habitación.");
            }
        } catch (error) {
            if (!signal.aborted) {
                fallo(error);
                outlet.append(boton("Reintentar", () => montar(hash)));
            }
        } finally {
            if (!signal.aborted) outlet.removeAttribute("aria-busy");
        }
    }
    async function montarListado(params, actual) {
        const signal = actual.eventos.signal;
        barraFiltros.querySelectorAll(".filter").forEach(el => {
            const seleccionado = (filtrosEstado[el.dataset.filter] || "") === params.estado;
            el.classList.toggle("active", seleccionado); el.setAttribute("aria-pressed", String(seleccionado));
        });
        const herramientas = nodo("details", null, "api-herramientas");
        const filtros = nodo("form", null, "business-filters");
        const label = nodo("label", "Buscar por número o tipo");
        const input = nodo("input"); input.type = "search"; input.maxLength = 60;
        input.name = "buscar"; input.placeholder = "Ej.: 101, Doble, Suite"; input.value = params.buscar;
        label.append(input); filtros.append(label);
        const aplicar = () => navegar(hashListado({ estado: params.estado, buscar: input.value.trim() }));
        filtros.addEventListener("submit", e => { e.preventDefault(); aplicar(); }, { signal });
        filtros.append(boton("Buscar / actualizar", aplicar), boton("Limpiar búsqueda", () => navegar(hashListado({ estado: params.estado }))));
        herramientas.append(nodo("summary", "Gestionar / actualizar"), filtros);
        herramientas.open = !!params.buscar;
        buscar.addEventListener("click", () => { herramientas.open = true; input.focus(); }, { signal });
        barraFiltros.querySelectorAll(".filter").forEach(el => el.addEventListener("click", () => {
            navegar(hashListado({ estado: filtrosEstado[el.dataset.filter] || "", buscar: params.buscar }));
        }, { signal }));
        const acciones = nodo("div", null, "business-actions");
        acciones.append(boton("Crear habitación", () => navegar("#/nueva")));
        const lista = nodo("div", null, "api-lista");
        outlet.append(herramientas, acciones, lista);
        const datos = await consultar("habitaciones", {
            pagina: params.pagina, tamano: 20,
            ...(params.estado ? { estado: params.estado } : {}),
            ...(params.buscar ? { buscar: params.buscar } : {})
        }, { signal });
        if (signal.aborted) return;
        for (const item of datos.items) {
            const card = diseno.tarjeta(item);
            const detalle = nodo("details", null, "api-detalle");
            detalle.append(nodo("summary", "Detalles / acciones"));
            const botones = nodo("div", null, "business-actions");
            botones.append(boton("Editar", () => navegar(`#/editar/${item.id}`)), boton("Eliminar", async () => {
                if (ocupado || !window.confirm(`¿Eliminar la habitación ${item.numero}? El historial se conserva.`)) return;
                ocupado = true;
                const controles = [...card.querySelectorAll("button")];
                const previos = controles.map(el => el.disabled); controles.forEach(el => { el.disabled = true; });
                try {
                    await eliminar("habitaciones", item.id);
                    ocupado = false;
                    navegar(hashListado({ estado: params.estado, buscar: params.buscar }), { reemplazar: true });
                } catch (error) { fallo(error); }
                finally { ocupado = false; controles.forEach((el, i) => { el.disabled = previos[i]; }); }
            }));
            const externa = (modulo, crear) => cambios.salir(`/views/${modulo}.html?${new URLSearchParams({ habitacionId: item.id, ...(crear ? { crear: "1" } : {}) })}`);
            const reservar = boton("Crear reserva", () => externa("reservas", true));
            reservar.disabled = item.estado === "MANTENIMIENTO";
            if (reservar.disabled) reservar.title = "No se puede reservar una habitación en mantenimiento.";
            botones.append(reservar, boton("Programar limpieza", () => externa("limpieza", true)),
                boton("Ver reservas", () => externa("reservas", false)), boton("Ver limpieza", () => externa("limpieza", false)));
            detalle.append(botones); card.append(detalle); lista.append(card);
        }
        if (!datos.items.length) lista.append(nodo("p", "No hay habitaciones para este filtro."));
        const paginacion = nodo("div", null, "business-actions");
        const pagina = n => navegar(hashListado({ estado: params.estado, buscar: params.buscar, pagina: n }));
        const anterior = boton("Anterior", () => pagina(params.pagina - 1)); anterior.disabled = params.pagina === 0;
        const siguiente = boton("Siguiente", () => pagina(params.pagina + 1));
        siguiente.disabled = (params.pagina + 1) * 20 >= datos.total || params.pagina >= 10000;
        paginacion.append(anterior, nodo("span", `Página ${params.pagina + 1} · ${datos.total} registros`), siguiente);
        outlet.append(paginacion); informar("Datos actualizados.");
    }
    function montarFormulario(item, actual) {
        const signal = actual.eventos.signal;
        const form = nodo("form", null, "business-form"); actual.form = form;
        const titulo = nodo("h2", item ? `Editar Hab. ${item.numero}` : "Nueva habitación"); titulo.tabIndex = -1;
        form.append(titulo);
        const campo = (name, texto, opciones) => {
            const label = nodo("label", texto), input = nodo("input");
            Object.assign(input, { name, required: true, ...opciones }); label.append(input); form.append(label);
        };
        campo("numero", "Número", { maxLength: 20, value: item?.numero || "", readOnly: !!item });
        campo("tipo", "Tipo", { maxLength: 60, value: item?.tipo || "" });
        campo("capacidad", "Capacidad", { type: "number", min: 1, max: 20, step: 1, value: item?.capacidad || 1 });
        campo("precioNoche", "Precio por noche", { type: "number", min: "0.01", max: "999999999.99", step: "0.01", value: item?.precioNoche || "" });
        const label = nodo("label", "Estado"), select = nodo("select"); select.name = "estado";
        for (const valor of estados) { const option = nodo("option", valor); option.value = valor; select.append(option); }
        select.value = item?.estado || "DISPONIBLE"; label.append(select); form.append(label);
        const enviar = nodo("button", "Guardar habitación"); enviar.type = "submit";
        form.append(enviar, boton("Cancelar", () => navegar(ultimaLista)));
        let clave = null;
        form.addEventListener("input", () => { clave = null; }, { signal });
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (ocupado || !form.reportValidity()) return;
            const datos = Object.fromEntries(new FormData(form)); datos.capacidad = Number(datos.capacidad);
            const campos = [...form.querySelectorAll("input, select, button")];
            ocupado = true; campos.forEach(el => { el.disabled = true; }); form.setAttribute("aria-busy", "true");
            actual.proteccion.enviado();
            informar("Guardando habitación…");
            try {
                clave ??= [...crypto.getRandomValues(new Uint8Array(16))].map(n => n.toString(16).padStart(2, "0")).join("");
                // No cancelar escrituras: un corte de red no demuestra que MongoDB no guardó.
                await guardar("habitaciones", datos, clave, item?.id);
                actual.proteccion.guardado(); ocupado = false;
                if (!signal.aborted) navegar(ultimaLista, { reemplazar: true, autorizado: true });
            } catch (error) { if (!signal.aborted) fallo(error); }
            finally {
                ocupado = false; campos.forEach(el => { el.disabled = false; }); form.removeAttribute("aria-busy");
            }
        }, { signal });
        outlet.append(form); actual.proteccion = cambios.vigilar(form); titulo.focus();
    }

    const enlaceHabitaciones = document.querySelector(".nav-item.active");
    enlaceHabitaciones.href = "#/listado";
    enlaceHabitaciones.setAttribute("aria-current", "page");
    enlaceHabitaciones.addEventListener("click", event => {
        if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault(); navegar("#/listado");
    }, { signal: eventos.signal });
    const salir = document.getElementById("salir");
    salir.addEventListener("click", async () => {
        if (!cambios.confirmar()) return;
        ocupado = true; salir.disabled = true;
        try { await cerrarSesion(); cambios.autorizarSalida(); window.location.assign("/login"); }
        catch (error) { fallo(error); }
        finally { ocupado = false; salir.disabled = false; }
    }, { signal: eventos.signal });
    const destruir = () => { desmontar(); eventos.abort(); cambios.destruir(); };
    window.addEventListener("pagehide", event => { if (!event.persisted) destruir(); }, { signal: eventos.signal });
    try {
        informar("Comprobando sesión…");
        const perfil = await consultarSesion();
        if (eventos.signal.aborted) return;
        document.getElementById("usuarioSesion").textContent = perfil.email;
        conectarCuenta(perfil, { bloqueado: () => ocupado, cambiarBloqueo: valor => { ocupado = valor; }, cambios });
        activa = { hash: location.hash || "#/listado", indice: history.state?.habitacionesSpa || 0 };
        history.replaceState({ ...history.state, habitacionesSpa: activa.indice }, "", activa.hash);
        window.addEventListener("popstate", historial, { signal: eventos.signal });
        window.addEventListener("hashchange", historial, { signal: eventos.signal });
        await montar(activa.hash);
    } catch (error) { fallo(error); }
    return { destruir };
}

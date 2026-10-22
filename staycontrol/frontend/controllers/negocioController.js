import { obtenerHabitacion, consultar, guardar, cambiarEstado, eliminar, anularMovimiento, resumenFinanzas, resumenDashboard } from "../services/negocioService.js";
import { consultarSesion, cerrarSesion } from "../services/authService.js";
import { prepararDiseno } from "./disenoOriginal.js";
import { conectarCuenta } from "./cuentaController.js";
import { conectarCalendario } from "./calendarioReservasController.js";
import { conectarDisponibilidad } from "./disponibilidadReservaController.js";
import { conectarSelectorHabitaciones } from "./selectorHabitacionesController.js";
import { protegerCambios } from "./cambiosPendientesController.js";

const estados = {
    habitaciones: ["DISPONIBLE", "OCUPADA", "LIMPIEZA", "MANTENIMIENTO"],
    reservas: ["CONFIRMADA", "CANCELADA", "FINALIZADA"],
    limpieza: ["PENDIENTE", "EN_PROCESO", "COMPLETADA", "CANCELADA"],
    finanzas: ["REGISTRADO", "ANULADO"]
};
// Todo contenido procedente de la API se inserta como texto, nunca como HTML.
function nodo(tag, texto, clase) {
    const el = document.createElement(tag);
    if (texto != null) { el.textContent = texto; }
    if (clase) { el.className = clase; }
    return el;
}
function boton(texto, accion) {
    const el = nodo("button", texto);
    el.type = "button";
    el.addEventListener("click", accion);
    return el;
}
function claveNueva() {
    return [...crypto.getRandomValues(new Uint8Array(16))].map(n => n.toString(16).padStart(2, "0")).join("");
}
function campo(form, nombre, titulo, opciones = {}) {
    const label = nodo("label", titulo);
    const input = nodo(opciones.valores ? "select" : "input");
    input.name = nombre;
    if (opciones.valores) {
        for (const valor of opciones.valores) {
            const option = nodo("option", valor || "Todos"); option.value = valor; input.append(option);
        }
    } else { input.type = opciones.type || "text"; }
    for (const [key, value] of Object.entries(opciones)) {
        if (!["valores", "type"].includes(key)) { input[key] = value; }
    }
    label.append(input); form.append(label);
    return input;
}

export async function iniciarNegocio(modulo) {
    const diseno = prepararDiseno(modulo);
    const main = document.querySelector("main");
    const aviso = document.getElementById("estadoPagina");
    let pagina = 0, ocupado = false, version = 0;
    const cambios = protegerCambios({ bloqueado: () => ocupado,
        alBloquear: () => informar("Espera a que termine la operación actual.") });
    let proteccionFormulario = null;
    function informar(texto, error = false) {
        aviso.textContent = texto; aviso.dataset.error = String(error);
    }
    function fallo(error) {
        if (error.status === 401) { window.location.assign("/login"); return; }
        informar(error.status === 403 ? "La solicitud fue rechazada. Actualiza la sesión y vuelve a intentarlo."
            : error.status ? error.message : "No se pudo conectar. Comprueba Spring Boot y la conexión de MongoDB, y reintenta.", true);
    }
    const salir = document.getElementById("salir");
    salir.addEventListener("click", async () => {
        if (ocupado) { informar("Espera a que termine la operación antes de cerrar sesión."); return; }
        if (!cambios.confirmar()) { return; }
        salir.disabled = true;
        try { await cerrarSesion(); cambios.autorizarSalida(); window.location.assign("/login"); }
        catch (error) { fallo(error); salir.disabled = false; }
    });
    try {
        const sesion = await consultarSesion();
        document.getElementById("usuarioSesion").textContent = sesion.email;
        conectarCuenta(sesion, { bloqueado: () => ocupado, cambiarBloqueo: valor => { ocupado = valor; }, cambios });
        const hotel = document.getElementById("nombreHotelDashboard");
        if (hotel) {
            hotel.textContent = sesion.nombreHotel?.trim() || "Mi hotel";
            hotel.title = sesion.nombreHotel?.trim() || "Esta cuenta todavía no tiene nombre de hotel registrado.";
        }
    } catch (error) { fallo(error); return; }

    const filtros = nodo("form", null, "business-filters");
    const estado = modulo === "dashboard" ? null : campo(filtros, "estado", "Estado", { valores: ["", ...estados[modulo]] });
    const busqueda = ["habitaciones", "reservas"].includes(modulo) ? campo(filtros, "buscar",
        modulo === "reservas" ? "Buscar por huésped" : "Buscar por número o tipo", {
            type: "search", maxLength: modulo === "reservas" ? 120 : 60,
            placeholder: modulo === "reservas" ? "Nombre o parte del apellido" : "Ej.: 101, Doble, Suite"
        }) : null;
    let buscarAplicado = "";
    const moneda = ["dashboard", "finanzas"].includes(modulo) ? campo(filtros, "moneda", "Moneda", { value: "COP", maxLength: 3, required: true, pattern: "[A-Za-z]{3}" }) : null;
    const opcionesFecha = { type: "date", min: "2000-01-01", max: "2100-12-31" };
    const fechaLimpieza = modulo === "limpieza" ? campo(filtros, "fecha", "Fecha programada (opcional)", opcionesFecha) : null;
    let limpiezaAplicada = { fecha: "", estado: "" };
    function mostrarFechaLimpieza() {
        const etiqueta = document.querySelector(".today");
        if (!etiqueta) { return; }
        const fecha = limpiezaAplicada.fecha;
        etiqueta.textContent = !fecha ? "Todas las fechas" : fecha === fechaLocal(new Date()) ? "Hoy" : fecha;
        etiqueta.title = fecha ? `Tareas programadas para ${fecha}` : "Tareas de todas las fechas";
    }
    if (modulo === "limpieza") { mostrarFechaLimpieza(); }
    const desde = modulo === "finanzas" ? campo(filtros, "desde", "Desde (opcional)", opcionesFecha) : null;
    const hasta = modulo === "finanzas" ? campo(filtros, "hasta", "Hasta (opcional)", opcionesFecha) : null;
    let finanzasAplicadas = { moneda: "COP", estado: "", fechas: {} };
    let resumenDescargable = null;
    const descargar = document.querySelector(".download-btn");
    function validarPeriodo() {
        hasta?.setCustomValidity(desde.value && hasta.value && desde.value > hasta.value
            ? "La fecha final no puede ser anterior a la inicial." : "");
    }
    desde?.addEventListener("input", validarPeriodo);
    hasta?.addEventListener("input", validarPeriodo);
    function aplicarFiltros() {
        if (modulo === "finanzas") { validarPeriodo(); }
        if (!filtros.reportValidity()) { return; }
        if (modulo === "limpieza") {
            limpiezaAplicada = { fecha: fechaLimpieza.value, estado: estado.value };
            mostrarFechaLimpieza();
        }
        if (modulo === "finanzas") {
            const fechas = {};
            if (desde.value) { fechas.desde = desde.value; }
            if (hasta.value) { fechas.hasta = hasta.value; }
            finanzasAplicadas = { moneda: moneda.value.toUpperCase(), estado: estado.value, fechas };
        }
        buscarAplicado = busqueda?.value.trim() || "";
        sincronizarEstado(); pagina = 0; cargar();
    }
    function sincronizarEstado() {
        if (modulo !== "habitaciones") { return; }
        document.querySelectorAll(".filter").forEach(el => {
            const valor = ({ disponibles: "DISPONIBLE", ocupadas: "OCUPADA", limpieza: "LIMPIEZA", mantenimiento: "MANTENIMIENTO" })[el.dataset.filter] || "";
            const activa = valor === estado.value;
            el.classList.toggle("active", activa); el.setAttribute("aria-pressed", String(activa));
        });
    }
    filtros.append(boton(busqueda ? "Buscar / actualizar" : "Actualizar", aplicarFiltros));
    if (modulo === "limpieza") {
        filtros.append(boton("Hoy", () => { fechaLimpieza.value = fechaLocal(new Date()); aplicarFiltros(); }),
            boton("Todas las fechas", () => { fechaLimpieza.value = ""; aplicarFiltros(); }));
    }
    if (modulo === "finanzas") {
        filtros.append(boton("Este mes", () => {
            const hoy = new Date();
            desde.value = fechaLocal(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
            hasta.value = fechaLocal(new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0));
            aplicarFiltros();
        }), boton("Todas las fechas", () => { desde.value = hasta.value = ""; aplicarFiltros(); }));
    }
    if (busqueda) {
        filtros.append(boton("Limpiar búsqueda", () => { busqueda.value = ""; aplicarFiltros(); }));
        estado.addEventListener("change", aplicarFiltros);
        sincronizarEstado();
    }
    filtros.addEventListener("submit", event => { event.preventDefault(); aplicarFiltros(); });
    const herramientas = nodo("details", null, "api-herramientas");
    herramientas.append(nodo("summary", "Gestionar / actualizar"), filtros);
    main.insertBefore(herramientas, main.firstChild.nextSibling);
    const resumen = nodo("section");
    const numeros = new Map();
    const urlInicial = new URL(window.location.href);
    let habitacionHistorial = ["reservas", "limpieza"].includes(modulo) && urlInicial.searchParams.get("crear") !== "1"
        ? urlInicial.searchParams.get("habitacionId") || "" : "";
    const historial = nodo("section", null, "business-actions api-historial");
    const tituloHistorial = nodo("p");
    historial.setAttribute("aria-label", "Filtro de historial por habitación");
    historial.append(tituloHistorial, boton("Quitar filtro de habitación", () => {
        habitacionHistorial = ""; historial.hidden = true; pagina = 0;
        const url = new URL(window.location.href); url.searchParams.delete("habitacionId");
        window.history.replaceState(window.history.state, "", url.pathname + url.search + url.hash);
        cargar();
    }));
    historial.hidden = !habitacionHistorial;
    if (habitacionHistorial) {
        tituloHistorial.textContent = `Historial de habitación · ID ${habitacionHistorial}`;
        herramientas.after(historial);
    }
    let tipoFinanzas = "INGRESO";
    document.querySelectorAll(".filter").forEach(filtro => filtro.addEventListener("click", () => {
        document.querySelectorAll(".filter").forEach(el => el.classList.remove("active"));
        filtro.classList.add("active");
        estado.value = ({ disponibles: "DISPONIBLE", ocupadas: "OCUPADA", limpieza: "LIMPIEZA", mantenimiento: "MANTENIMIENTO" })[filtro.dataset.filter] || "";
        sincronizarEstado();
        pagina = 0; cargar();
    }));
    document.querySelectorAll(".tab").forEach(tab => tab.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach(el => el.classList.remove("active"));
        tab.classList.add("active"); tipoFinanzas = tab.dataset.tab === "expense" ? "EGRESO" : "INGRESO"; pagina = 0; cargar();
    }));
    document.querySelector(".search-btn")?.addEventListener("click", () => { herramientas.open = true; (busqueda || estado).focus(); });
    const calendario = modulo === "reservas" ? conectarCalendario({ filtros, herramientas,
        alCambiar: () => { pagina = 0; cargar(); } }) : null;
    let formulario;
    if (modulo !== "dashboard") {
        const crear = boton(modulo === "finanzas" ? "Registrar movimiento" : "Crear " + ({ habitaciones: "habitación", reservas: "reserva", limpieza: "tarea" }[modulo]), () => abrirFormulario());
        herramientas.append(crear);
        document.getElementById("addReservation")?.addEventListener("click", () => abrirFormulario());
        formulario = nodo("section"); herramientas.append(formulario);
    }
    const lista = diseno.lista;
    const controles = nodo("div", null, "business-actions");
    if (modulo === "finanzas") { controles.hidden = true; }
    const anterior = boton("Anterior", () => { if (pagina > 0) { pagina--; cargar(); } });
    const siguiente = boton("Siguiente", () => { pagina++; cargar(); });
    const contador = nodo("span");
    if (modulo !== "dashboard") { controles.append(anterior, contador, siguiente); main.append(controles); }

    async function cargar() {
        const actual = ++version;
        const filtroFinanzas = finanzasAplicadas;
        anterior.disabled = siguiente.disabled = true;
        if (modulo === "finanzas") {
            controles.hidden = true; resumenDescargable = null;
            if (descargar) { descargar.disabled = true; }
            document.querySelector(".balance-card h2").textContent = "—";
            document.querySelector(".income-text").textContent = "Ingresos: —";
            document.querySelector(".expense-text").textContent = "Gastos: —";
            document.querySelectorAll(".chart-column").forEach(col => {
                col.title = "";
                col.querySelector("span").textContent = "—";
                col.querySelectorAll(".income-bar, .expense-bar").forEach(bar => { bar.style.height = "0px"; });
            });
        }
        informar("Cargando datos…"); lista.replaceChildren(); resumen.replaceChildren();
        if (habitacionHistorial && !/^[a-f0-9]{64}$/.test(habitacionHistorial)) {
            informar("El ID de habitación del enlace es inválido. Quita el filtro o vuelve a Habitaciones.", true);
            return;
        }
        try {
            const divisa = modulo === "finanzas" ? filtroFinanzas.moneda : moneda?.value.trim().toUpperCase();
            if (modulo === "dashboard") {
                const datos = await resumenDashboard(divisa);
                if (actual !== version) { return; }
                diseno.dashboard(datos);
                informar(`Datos de ${datos.fecha} (${datos.zonaHoraria}). Ocupación actual; finanzas del mes completo.`);
                return;
            }
            const params = { pagina, tamano: 20 };
            if (habitacionHistorial) { params.habitacionId = habitacionHistorial; }
            const estadoAplicado = modulo === "finanzas" ? filtroFinanzas.estado
                : modulo === "limpieza" ? limpiezaAplicada.estado : estado.value;
            if (estadoAplicado) { params.estado = estadoAplicado; }
            if (busqueda && buscarAplicado) { params.buscar = buscarAplicado; }
            if (divisa) { params.moneda = divisa; }
            if (modulo === "finanzas") { Object.assign(params, filtroFinanzas.fechas, { tipo: tipoFinanzas }); }
            if (calendario?.fecha()) { params.fecha = calendario.fecha(); }
            if (modulo === "limpieza" && limpiezaAplicada.fecha) { params.fecha = limpiezaAplicada.fecha; }
            const datos = await consultar(modulo, params);
            if (actual !== version) { return; }
            if (["reservas", "limpieza"].includes(modulo)) {
                const ids = datos.items.map(item => item.habitacionId);
                if (params.habitacionId) { ids.push(params.habitacionId); }
                await Promise.all([...new Set(ids)].filter(id => !numeros.has(id)).map(async id => {
                    try { const h = await obtenerHabitacion(id); numeros.set(id, h.numero); } catch { /* El ID se conserva en detalles. */ }
                }));
                if (actual !== version) { return; }
                if (params.habitacionId) {
                    const numero = numeros.get(params.habitacionId);
                    tituloHistorial.textContent = numero ? `Historial · Hab. ${numero}` : `Historial · ID ${params.habitacionId}`;
                }
            }
            for (const item of datos.items) { lista.append(tarjeta(item)); }
            if (!datos.items.length) { lista.append(nodo("p", "No hay registros para este filtro.")); }
            anterior.disabled = pagina === 0;
            siguiente.disabled = (pagina + 1) * 20 >= datos.total || pagina >= 10000;
            contador.textContent = `Página ${pagina + 1} · ${datos.total} registros`;
            if (modulo === "finanzas") { controles.hidden = datos.total <= 20 && pagina === 0; }
            informar(busqueda && params.buscar
                ? `${datos.total} coincidencias para «${params.buscar}».` : "Datos actualizados.");
            if (modulo === "limpieza") {
                informar(`${datos.total} tareas · ${params.fecha || "Todas las fechas"} · ${params.estado || "Todos los estados"}. Vencida indica una tarea abierta anterior a hoy según tu dispositivo.`);
            }
            if (modulo === "finanzas") {
                const fin = await resumenFinanzas(divisa, filtroFinanzas.fechas);
                if (actual !== version) { return; }
                diseno.finanzas(fin);
                const periodo = `${filtroFinanzas.fechas.desde || "Sin inicio"} → ${filtroFinanzas.fechas.hasta || "Sin fin"}`;
                document.querySelector(".balance-label").textContent = "Balance del período";
                informar(`${periodo} · ${divisa}. Balance de ingresos y gastos registrados; excluye anulados, sin depender de la pestaña o del estado del listado.`);
                resumenDescargable = fin;
                if (descargar) { descargar.disabled = false; }
                await actualizarGrafica(divisa, actual, filtroFinanzas.fechas);
            }
        } catch (error) { if (actual === version) { fallo(error); } }
    }

    function tarjeta(item) {
        const card = diseno.tarjeta(item, numeros.get(item.habitacionId));
        const detalle = nodo("details", null, "api-detalle");
        detalle.append(nodo("summary", "Detalles / acciones"));
        const detalles = modulo === "habitaciones" ? { Tipo: item.tipo, Capacidad: item.capacidad, "Precio por noche": item.precioNoche }
            : modulo === "reservas" ? { "ID habitación": item.habitacionId, Personas: item.personas, Entrada: item.fechaEntrada, Salida: item.fechaSalida, "Precio por noche": item.precioNoche, Total: item.total }
            : modulo === "limpieza" ? { "ID habitación": item.habitacionId, Responsable: item.responsable || "Sin asignar", Fecha: item.fechaProgramada }
            : { Tipo: item.tipo, Importe: `${item.importe} ${item.moneda}`, Fecha: item.fecha, Método: item.metodoPago, "Registrado por": item.creadoPor, "Motivo anulación": item.anulacion?.motivo || "—" };
        for (const [label, value] of Object.entries(detalles)) { detalle.append(nodo("p", `${label}: ${value}`)); }
        (card.querySelector(".task-info, .transaction-info") || card).append(detalle);
        const acciones = nodo("div", null, "business-actions"); detalle.append(acciones);
        if (modulo === "limpieza" && ["PENDIENTE", "EN_PROCESO"].includes(item.estado)) {
            card.querySelector(".checkbox").addEventListener("click", () => ejecutar(card, () => cambiarEstado(modulo, item.id, "COMPLETADA"), "¿Completar esta tarea?"));
        }
        if (modulo === "habitaciones") {
            acciones.append(boton("Editar", () => abrirFormulario(item)), boton("Eliminar", () => ejecutar(card, () => eliminar(modulo, item.id), "¿Eliminar esta habitación? El historial se conserva.")));
            const reservar = boton("Crear reserva", () => abrirDesdeHabitacion("reservas", item.id));
            if (item.estado === "MANTENIMIENTO") {
                reservar.disabled = true;
                reservar.title = "No se puede reservar una habitación en mantenimiento.";
            }
            acciones.append(reservar, boton("Programar limpieza", () => abrirDesdeHabitacion("limpieza", item.id)));
            acciones.append(boton("Ver reservas", () => abrirDesdeHabitacion("reservas", item.id, false)),
                boton("Ver limpieza", () => abrirDesdeHabitacion("limpieza", item.id, false)));
        } else if (modulo === "reservas" && item.estado === "CONFIRMADA") {
            acciones.append(boton("Cancelar", () => ejecutar(card, () => eliminar(modulo, item.id), "¿Cancelar esta reserva?")),
                boton("Finalizar", () => ejecutar(card, () => cambiarEstado(modulo, item.id, "FINALIZADA"), "¿Finalizar la reserva? Solo se permite desde la fecha de salida.")));
        } else if (modulo === "limpieza" && ["PENDIENTE", "EN_PROCESO"].includes(item.estado)) {
            if (item.estado === "PENDIENTE") { acciones.append(boton("Iniciar", () => ejecutar(card, () => cambiarEstado(modulo, item.id, "EN_PROCESO")))); }
            acciones.append(boton("Completar", () => ejecutar(card, () => cambiarEstado(modulo, item.id, "COMPLETADA"), "¿Marcar como completada?")),
                boton("Cancelar", () => ejecutar(card, () => eliminar(modulo, item.id), "¿Cancelar esta tarea?")));
        } else if (modulo === "finanzas" && item.estado === "REGISTRADO") {
            acciones.append(boton("Anular", () => {
                const motivo = window.prompt("Motivo de la anulación (1 a 300 caracteres):");
                if (motivo?.trim()) { ejecutar(card, () => anularMovimiento(item.id, motivo.trim()), "¿Anular este movimiento? No realiza un reembolso."); }
            }));
        }
        return card;
    }
    async function ejecutar(card, operacion, confirmacion) {
        if (ocupado || (confirmacion && !window.confirm(confirmacion))) { return; }
        const botones = [...card.querySelectorAll("button")];
        const deshabilitados = botones.map(b => b.disabled);
        ocupado = true; botones.forEach(b => { b.disabled = true; });
        try { await operacion(); await cargar(); }
        catch (error) { fallo(error); }
        finally { ocupado = false; botones.forEach((b, i) => { b.disabled = deshabilitados[i]; }); }
    }

    function abrirDesdeHabitacion(destino, id, crear = true) {
        if (ocupado) { informar("Espera a que termine la operación actual."); return; }
        // Usar la vista directamente: las rutas cortas redirigen sin conservar parámetros.
        const parametros = { habitacionId: id };
        if (crear) { parametros.crear = "1"; }
        cambios.salir(`/views/${destino}.html?${new URLSearchParams(parametros)}`);
    }

    function abrirFormulario(item = null, habitacionInicial = null) {
        if (ocupado) { return; }
        herramientas.open = true;
        if (formulario.childElementCount && !cambios.confirmar(formulario.querySelector("form"))) { return; }
        proteccionFormulario?.retirar();
        const form = nodo("form", null, "business-form");
        form.append(nodo("h2", item ? "Editar habitación"
            : ({ reservas: "Nueva reserva", limpieza: "Nueva tarea de limpieza" }[modulo] || "Nuevo registro")));
        let clave = null;
        let selectorHabitaciones;
        const requerido = { required: true };
        const fecha = { ...requerido, type: "date", min: "2000-01-01", max: "2100-12-31" };
        if (modulo === "habitaciones") {
            campo(form, "numero", "Número", { ...requerido, maxLength: 20, value: item?.numero || "", readOnly: !!item });
            campo(form, "tipo", "Tipo", { ...requerido, maxLength: 60, value: item?.tipo || "" });
            campo(form, "capacidad", "Capacidad", { ...requerido, type: "number", min: 1, max: 20, step: 1, value: item?.capacidad || 1 });
            campo(form, "precioNoche", "Precio por noche", { ...requerido, type: "number", min: "0.01", max: "999999999.99", step: "0.01", value: item?.precioNoche || "" });
            campo(form, "estado", "Estado", { valores: estados.habitaciones, value: item?.estado || "DISPONIBLE" });
        } else if (["reservas", "limpieza"].includes(modulo)) {
            const habitacion = campo(form, "habitacionId", "ID de habitación", {
                ...requerido, pattern: "[a-f0-9]{64}", maxLength: 64, value: habitacionInicial?.id || ""
            });
            selectorHabitaciones = conectarSelectorHabitaciones(form, habitacion, {
                bloqueado: () => form.getAttribute("aria-busy") === "true", alError: fallo, inicial: habitacionInicial
            });
            form.append(nodo("small", "Selecciona una habitación o pega su ID. El servidor valida capacidad, estado y disponibilidad."));
            if (modulo === "reservas") {
                campo(form, "huesped", "Huésped", { ...requerido, maxLength: 120 });
                campo(form, "personas", "Personas", { ...requerido, type: "number", min: 1, max: 20, step: 1, value: 1 });
                campo(form, "fechaEntrada", "Entrada", fecha); campo(form, "fechaSalida", "Salida", fecha);
            } else {
                campo(form, "descripcion", "Descripción", { ...requerido, maxLength: 500 });
                campo(form, "responsable", "Responsable (opcional)", { maxLength: 120 });
                campo(form, "fechaProgramada", "Fecha programada", { ...fecha, value: limpiezaAplicada.fecha || fechaLocal(new Date()) });
            }
        } else {
            campo(form, "tipo", "Tipo", { valores: ["INGRESO", "EGRESO"] });
            campo(form, "concepto", "Concepto", { ...requerido, maxLength: 200 });
            campo(form, "importe", "Importe", { ...requerido, type: "number", min: "0.0001", max: "999999999999.9999", step: "0.0001" });
            campo(form, "moneda", "Moneda ISO", { ...requerido, maxLength: 3, pattern: "[A-Za-z]{3}", value: finanzasAplicadas.moneda });
            campo(form, "fecha", "Fecha", fecha);
            campo(form, "metodoPago", "Método", { valores: ["EFECTIVO", "TRANSFERENCIA", "TARJETA", "OTRO"] });
            form.append(nodo("small", "Registro manual: no procesa pagos ni se genera automáticamente desde reservas."));
        }
        form.addEventListener("input", () => { clave = null; });
        if (modulo === "reservas") { conectarDisponibilidad(form); }
        const enviar = nodo("button", "Guardar"); enviar.type = "submit";
        const cancelar = boton("Cerrar", () => {
            if (cambios.confirmar(form)) { proteccionFormulario?.retirar(); formulario.replaceChildren(); }
        });
        form.append(enviar, cancelar);
        form.addEventListener("submit", async event => {
            event.preventDefault(); if (ocupado || !form.reportValidity()) { return; }
            const datos = Object.fromEntries(new FormData(form));
            for (const nombre of ["capacidad", "personas"]) { if (nombre in datos) { datos[nombre] = Number(datos[nombre]); } }
            // Los importes viajan como decimal textual, evitando redondearlos en JavaScript.
            if (modulo === "reservas" && datos.fechaSalida <= datos.fechaEntrada) { informar("La salida debe ser posterior a la entrada.", true); return; }
            ocupado = true; const campos = [...form.querySelectorAll("input, select, button")];
            const deshabilitados = campos.map(el => el.disabled); campos.forEach(el => { el.disabled = true; });
            form.setAttribute("aria-busy", "true");
            proteccionFormulario.enviado();
            try {
                clave ??= claveNueva(); await guardar(modulo, datos, clave, item?.id);
                proteccionFormulario.retirar();
                formulario.replaceChildren(); pagina = 0; await cargar();
            } catch (error) { fallo(error); }
            finally {
                ocupado = false; form.removeAttribute("aria-busy");
                campos.forEach((el, i) => { el.disabled = deshabilitados[i]; });
                selectorHabitaciones?.actualizarBloqueo();
            }
        });
        formulario.replaceChildren(form); form.querySelector("input, select")?.focus();
        proteccionFormulario = cambios.vigilar(form);
        selectorHabitaciones?.iniciar();
        if (habitacionInicial) {
            form.querySelector(modulo === "reservas" ? '[name="huesped"]' : '[name="descripcion"]')?.focus();
        }
        return true;
    }
    function fechaLocal(fecha) {
        return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}-${String(fecha.getDate()).padStart(2, "0")}`;
    }
    async function actualizarGrafica(divisa, actual, fechas) {
        const columnas = [...document.querySelectorAll(".chart-column")];
        // Sin límite final, la gráfica termina en el mes actual (o en el inicio futuro).
        const finGrafica = fechas.hasta || [fechaLocal(new Date()), fechas.desde || "2000-01-01"].sort().at(-1);
        const [anoFinal, mesFinal] = finGrafica.split("-").map(Number);
        document.querySelector(".chart-section h3").textContent = `Análisis mensual · hasta 6 meses a ${finGrafica.slice(0, 7)}`;
        const series = await Promise.all(columnas.map(async (_, i) => {
            const date = new Date(anoFinal, mesFinal - columnas.length + i, 1);
            const year = date.getFullYear(), mes = String(date.getMonth() + 1).padStart(2, "0");
            const desde = [`${year}-${mes}-01`, fechas.desde || "2000-01-01"].sort().at(-1);
            const hasta = [`${year}-${mes}-${new Date(year, date.getMonth() + 1, 0).getDate()}`, fechas.hasta || "2100-12-31"].sort()[0];
            const fuera = desde > hasta;
            return { date, fuera, desde, hasta, datos: fuera ? { ingresos: 0, egresos: 0 } : await resumenFinanzas(divisa, { desde, hasta }) };
        }));
        if (actual !== version) { return; }
        const max = Math.max(1, ...series.flatMap(s => [Number(s.datos.ingresos), Number(s.datos.egresos)]));
        series.forEach((s, i) => {
            const col = columnas[i];
            col.querySelector("span").textContent = s.fuera ? "—" : s.date.toLocaleDateString("es", { month: "short" });
            col.querySelector(".income-bar").style.height = `${Number(s.datos.ingresos) / max * 75}px`;
            col.querySelector(".expense-bar").style.height = `${Number(s.datos.egresos) / max * 75}px`;
            col.title = s.fuera ? "Fuera del período seleccionado" : `${s.desde} a ${s.hasta}: Ingresos ${s.datos.ingresos} / Gastos ${s.datos.egresos} ${divisa}`;
        });
    }
    descargar?.addEventListener("click", () => {
        if (!resumenDescargable) { return; }
        try {
            const fin = resumenDescargable;
            const url = URL.createObjectURL(new Blob([JSON.stringify(fin, null, 2)], { type: "application/json" }));
            const a = nodo("a"); a.href = url; a.download = `resumen-finanzas-${fin.moneda}-${fin.desde || "inicio"}-${fin.hasta || "fin"}.json`; a.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) { fallo(error); }
    });
    await cargar();
    if (["reservas", "limpieza"].includes(modulo)) {
        const url = new URL(window.location.href);
        if (url.searchParams.get("crear") !== "1") { return; }
        const id = url.searchParams.get("habitacionId") || "";
        if (!/^[a-f0-9]{64}$/.test(id)) {
            informar("El enlace contiene un ID de habitación inválido. Abre el formulario desde Habitaciones.", true);
            return;
        }
        try {
            const habitacion = await obtenerHabitacion(id);
            // No reemplazar un formulario que el usuario haya abierto durante la carga.
            if (formulario.childElementCount || ocupado) { return; }
            if (modulo === "reservas" && habitacion.estado === "MANTENIMIENTO") {
                informar("La habitación está en mantenimiento. No se abrió una nueva reserva.", true); return;
            }
            if (abrirFormulario(null, habitacion)) {
                url.searchParams.delete("crear"); url.searchParams.delete("habitacionId");
                window.history.replaceState(window.history.state, "", url.pathname + url.search + url.hash);
            }
        } catch (error) { fallo(error); }
    }
}

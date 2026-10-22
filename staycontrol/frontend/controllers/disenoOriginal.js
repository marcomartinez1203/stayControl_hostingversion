// Adaptación de datos sobre las tarjetas y la estructura originales del usuario.
function texto(root, selector, valor) {
    const el = root.querySelector(selector);
    if (el) { el.textContent = valor ?? "—"; }
}
function elemento(tag, contenido) {
    const el = document.createElement(tag); if (contenido != null) { el.textContent = contenido; } return el;
}
const rutas = { Inicio: "dashboard", Habitaciones: "habitaciones", Reservas: "reservas", Finanzas: "finanzas", Limpieza: "limpieza" };
function icono(nombre) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "ui-icon"); svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true"); svg.setAttribute("focusable", "false");
    const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", `../assets/icons/sprite.svg#${nombre}`); svg.append(use); return svg;
}
export function prepararDiseno(modulo) {
    const main = document.querySelector("main");
    const header = document.querySelector(".header");
    document.querySelectorAll(".nav-item").forEach(link => {
        const ruta = rutas[link.textContent.trim()];
        if (ruta) { link.href = `/${ruta}`; }
    });
    const menu = elemento("details"); menu.className = "api-menu";
    menu.append(elemento("summary", "Cuenta"));
    const email = elemento("p"); email.id = "usuarioSesion";
    const salir = elemento("button", "Cerrar sesión"); salir.id = "salir"; salir.type = "button";
    const panelCuenta = elemento("div"); panelCuenta.className = "api-cuenta-panel";
    panelCuenta.append(email, salir); menu.append(panelCuenta); header.append(menu);
    const aviso = elemento("p"); aviso.id = "estadoPagina"; aviso.className = "api-aviso";
    aviso.setAttribute("role", "status"); aviso.setAttribute("aria-live", "polite");
    main.prepend(aviso);
    const selectores = { habitaciones: ".room-card", reservas: ".reservation-card", limpieza: ".task-card", finanzas: ".transaction", dashboard: ".reservation" };
    const selector = selectores[modulo];
    const plantilla = main.querySelector(selector)?.cloneNode(true);
    main.querySelectorAll(selector).forEach(el => el.remove());
    const lista = elemento("div"); lista.className = "api-lista";
    const destino = modulo === "finanzas" ? main.querySelector(".transactions")
        : modulo === "dashboard" ? main.querySelector(".reservations") : main;
    destino.append(lista);
    if (modulo === "dashboard") {
        texto(header, ".hello", "Bienvenido");
        // El nombre del establecimiento se carga desde el perfil autenticado.
        texto(main, ".card-title span", "— / —");
        main.querySelector(".progress-fill").style.width = "0%";
        main.querySelectorAll(".legend-item span").forEach(el => { el.textContent = "—"; });
        texto(main, ".income", "—"); texto(main, ".expense", "—");
        texto(main, ".alert p", "Cargando tareas…");
        const ver = main.querySelector(".section-title a"); if (ver) { ver.href = "/reservas"; }
    }
    if (modulo === "finanzas") {
        texto(main, ".balance-card h2", "—"); texto(main, ".income-text", "Ingresos: —"); texto(main, ".expense-text", "Gastos: —");
        main.querySelectorAll(".income-bar, .expense-bar").forEach(el => { el.style.height = "0px"; });
        main.querySelectorAll(".chart-column > span").forEach(el => { el.textContent = "—"; });
    }
    function tarjeta(item, numeroHabitacion) {
        const card = plantilla.cloneNode(true);
        const numero = numeroHabitacion || "Consultar ID";
        if (modulo === "habitaciones") {
            texto(card, ".room-info h2", `Hab. ${item.numero}`); texto(card, ".room-info span", `• ${item.tipo}`);
            const status = card.querySelector(".status-tag");
            status.className = "status-tag " + ({ DISPONIBLE: "available", OCUPADA: "occupied", LIMPIEZA: "cleaning", MANTENIMIENTO: "maintenance" }[item.estado] || "");
            status.replaceChildren(icono(({ DISPONIBLE: "disponible", OCUPADA: "ocupada", LIMPIEZA: "limpieza", MANTENIMIENTO: "mantenimiento" })[item.estado] || "info"), document.createTextNode(item.estado));
            texto(card, ".room-details p", `Capacidad: ${item.capacidad}`); texto(card, ".price strong", `$${item.precioNoche}`);
            card.dataset.numero = item.numero;
        } else if (modulo === "reservas") {
            texto(card, ".guest strong", item.huesped); texto(card, ".guest span", `${item.fechaEntrada} - ${item.fechaSalida}`);
            texto(card, ".assigned-room strong", numero);
            const status = card.querySelector(".status"); status.textContent = item.estado;
            status.className = "status " + (item.estado === "CONFIRMADA" ? "confirmed" : item.estado === "CANCELADA" ? "cancelled" : "pending");
        } else if (modulo === "limpieza") {
            texto(card, ".room-name strong", `Hab. ${numero}`); texto(card, ".room-name span", item.descripcion);
            const hoy = new Date();
            const fechaHoy = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-${String(hoy.getDate()).padStart(2, "0")}`;
            const vencida = ["PENDIENTE", "EN_PROCESO"].includes(item.estado) && item.fechaProgramada < fechaHoy;
            const prioridad = card.querySelector(".priority");
            prioridad.textContent = vencida ? `Vencida · ${item.fechaProgramada}` : item.fechaProgramada;
            prioridad.className = "priority " + (vencida ? "urgent" : "normal");
            prioridad.title = vencida ? "Tarea abierta cuya fecha programada ya pasó (fecha del dispositivo)." : "Fecha programada";
            texto(card, ".staff span", item.responsable || "Sin asignar");
            const status = card.querySelector(".task-status");
            status.textContent = ({ PENDIENTE: "Pendiente", EN_PROCESO: "En proceso", COMPLETADA: "Completada", CANCELADA: "Cancelada" })[item.estado];
            status.className = "task-status " + ({ PENDIENTE: "pending", EN_PROCESO: "process", COMPLETADA: "completed", CANCELADA: "pending" })[item.estado];
            const check = card.querySelector(".checkbox");
            check.classList.toggle("checked", item.estado === "COMPLETADA");
            check.replaceChildren();
            if (item.estado === "COMPLETADA") { check.append(icono("confirmar")); }
            check.disabled = ["COMPLETADA", "CANCELADA"].includes(item.estado);
            check.setAttribute("aria-label", item.estado === "COMPLETADA" ? "Tarea completada"
                : item.estado === "CANCELADA" ? "Tarea cancelada" : `Completar tarea de habitación ${numero}`);
        } else if (modulo === "finanzas") {
            texto(card, ".transaction-info > strong", item.concepto);
            texto(card, ".transaction-meta > span", item.fecha); texto(card, ".category", item.estado);
            const ingreso = item.tipo === "INGRESO";
            const valor = card.querySelector(".transaction-value"); valor.textContent = `${ingreso ? "+" : "−"}${item.importe} ${item.moneda}`;
            valor.className = "transaction-value " + (ingreso ? "income-value" : "expense-value");
            card.querySelector(".transaction-icon").className = "transaction-icon " + (ingreso ? "income" : "expense");
            card.querySelector(".transaction-icon").replaceChildren(icono(ingreso ? "ingreso" : "gasto"));
        } else {
            texto(card, ".room", item.numeroHabitacion || "—"); texto(card, ".reservation-info strong", item.huesped);
            texto(card, ".reservation-info span", `Entrada: ${item.fechaEntrada}`); texto(card, ".status", "CONFIRMADA");
        }
        return card;
    }
    function dashboard(datos) {
        const h = datos.habitacionesActuales, f = datos.finanzasMes;
        texto(main, ".card-title span", `${h.ocupadas} / ${h.total}`);
        main.querySelector(".progress-fill").style.width = `${Math.max(0, Math.min(100, Number(h.porcentajeOcupacion)))}%`;
        const leyendas = main.querySelectorAll(".legend-item span");
        leyendas[0].textContent = `${h.ocupadas} Ocupadas`; leyendas[1].textContent = `${h.disponibles} Disponibles`;
        texto(main, ".income", `${f.ingresos} ${f.moneda}`); texto(main, ".expense", `${f.egresos} ${f.moneda}`);
        texto(main, ".alert p", `${datos.limpieza.habitacionesConTareas} habitaciones con tareas abiertas.`);
        lista.replaceChildren(...datos.proximasReservas.map(item => tarjeta(item)));
        if (!datos.proximasReservas.length) { lista.append(elemento("p", "No hay próximas reservas.")); }
    }
    function finanzas(fin) {
        texto(main, ".balance-card h2", `${fin.balance} ${fin.moneda}`);
        texto(main, ".income-text", `Ingresos: +${fin.ingresos}`); texto(main, ".expense-text", `Gastos: −${fin.egresos}`);
    }
    return { lista, tarjeta, dashboard, finanzas };
}

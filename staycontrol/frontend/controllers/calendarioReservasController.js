// Conserva los botones del calendario original y filtra en el servidor, antes de paginar.
export function conectarCalendario({ filtros, herramientas, alCambiar }) {
    const dias = [...document.querySelectorAll(".calendar-strip .day")];
    const tira = document.querySelector(".calendar-strip");
    const titulo = document.querySelector("main.reservations > h2");
    const minimo = "2000-01-01", maximo = "2100-12-31";
    const iso = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    const desdeIso = valor => { const [y, m, d] = valor.split("-").map(Number); return new Date(y, m - 1, d, 12); };
    const sumar = (valor, cantidad) => { const date = desdeIso(valor); date.setDate(date.getDate() + cantidad); return iso(date); };
    const hoy = () => { const date = iso(new Date()); return date < minimo ? minimo : date > maximo ? maximo : date; };
    const limitar = fecha => fecha < minimo ? minimo : fecha > sumar(maximo, 1 - dias.length) ? sumar(maximo, 1 - dias.length) : fecha;
    let inicio = limitar(hoy()), seleccion = "";
    const label = document.createElement("label"); label.textContent = "Fecha de estancia (opcional)";
    const input = document.createElement("input"); input.type = "date"; input.min = minimo; input.max = maximo;
    label.append(input); filtros.prepend(label);
    const ayuda = document.createElement("small");
    ayuda.textContent = "Incluye la entrada y las noches de estancia; excluye el día de salida. Hoy usa la fecha de tu dispositivo.";
    label.append(ayuda);
    const controles = document.createElement("div"); controles.className = "calendar-controls";
    const rango = document.createElement("span"); rango.setAttribute("aria-live", "polite");
    function boton(texto, accion) {
        const b = document.createElement("button"); b.type = "button"; b.textContent = texto; b.addEventListener("click", accion); return b;
    }
    function elegir(fecha) {
        seleccion = fecha; input.value = fecha;
        if (fecha && (fecha < inicio || fecha > sumar(inicio, dias.length - 1))) { inicio = limitar(fecha); }
        pintar(); alCambiar();
    }
    const previo = boton("‹", () => { inicio = limitar(sumar(inicio, -dias.length)); elegir(inicio); });
    const siguiente = boton("›", () => { inicio = limitar(sumar(inicio, dias.length)); elegir(inicio); });
    previo.setAttribute("aria-label", "Días anteriores"); siguiente.setAttribute("aria-label", "Días siguientes");
    const todas = boton("Todas", () => elegir(""));
    controles.append(previo, rango, siguiente, boton("Hoy", () => elegir(hoy())), todas);
    tira.before(controles);
    input.addEventListener("change", () => { if (input.reportValidity()) { elegir(input.value); } });
    document.querySelector(".calendar-button")?.addEventListener("click", () => {
        herramientas.open = true; input.focus();
        try { input.showPicker?.(); } catch { /* El campo permanece accesible por teclado. */ }
    });
    dias.forEach(day => {
        day.type = "button";
        day.addEventListener("click", () => elegir(day.dataset.fecha));
    });
    function pintar() {
        const formato = { day: "numeric", month: "short", year: "numeric" };
        rango.textContent = `${desdeIso(inicio).toLocaleDateString("es", formato)} – ${desdeIso(sumar(inicio, dias.length - 1)).toLocaleDateString("es", formato)}`;
        previo.disabled = inicio === minimo;
        siguiente.disabled = inicio === limitar(maximo);
        todas.setAttribute("aria-pressed", String(!seleccion));
        dias.forEach((day, i) => {
            const fecha = sumar(inicio, i), date = desdeIso(fecha);
            day.dataset.fecha = fecha; day.disabled = false;
            day.querySelector("span").textContent = date.toLocaleDateString("es", { weekday: "short" });
            day.querySelector("strong").textContent = date.getDate();
            day.classList.toggle("selected", fecha === seleccion);
            day.setAttribute("aria-pressed", String(fecha === seleccion));
            day.setAttribute("aria-label", `Estancias del ${date.toLocaleDateString("es", formato)}`);
            day.title = day.getAttribute("aria-label");
        });
        if (titulo) { titulo.textContent = seleccion ? `Estancias del ${desdeIso(seleccion).toLocaleDateString("es", formato)}` : "Todas las reservas"; }
    }
    pintar();
    return { fecha: () => seleccion };
}

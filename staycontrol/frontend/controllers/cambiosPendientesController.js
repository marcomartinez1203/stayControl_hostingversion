// Solo memoria de esta pestaña. No persiste huéspedes, importes ni credenciales.
export function protegerCambios({ bloqueado, alBloquear }) {
    const eventos = new AbortController();
    const formularios = new Map();
    let salidaAutorizada = false;
    const valores = form => JSON.stringify([...form.elements]
        .filter(el => el.name && !["button", "submit", "reset"].includes(el.type))
        .map(el => [el.name, ["checkbox", "radio"].includes(el.type) ? el.checked : el.value]));
    function pendientes(form = null) {
        return [...formularios.entries()].some(([el, estado]) => el.isConnected && (!form || el === form)
            && (estado.incierto || valores(el) !== estado.inicial));
    }
    function confirmar(form = null) {
        if (bloqueado()) { alBloquear(); return false; }
        return !pendientes(form) || window.confirm("Hay cambios sin guardar o un envío sin confirmar. ¿Descartarlos? Si hubo un error de red, reintenta sin editar para evitar duplicados.");
    }
    function salir(url) {
        if (!confirmar()) { return; }
        salidaAutorizada = true;
        window.location.assign(url);
    }
    window.addEventListener("beforeunload", event => {
        if (!salidaAutorizada && (bloqueado() || pendientes())) {
            event.preventDefault(); event.returnValue = "";
        }
    }, { signal: eventos.signal });
    document.addEventListener("click", event => {
        if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) { return; }
        const link = event.target.closest?.("a[href]");
        if (!link || link.hasAttribute("download") || (link.target && link.target !== "_self")) { return; }
        const url = new URL(link.href, window.location.href);
        if (url.origin !== window.location.origin || !["http:", "https:"].includes(url.protocol)) { return; }
        if (url.pathname === location.pathname && url.search === location.search && url.hash) { return; }
        if (!pendientes() && !bloqueado()) { return; }
        event.preventDefault(); event.stopImmediatePropagation(); salir(url.href);
    }, { capture: true, signal: eventos.signal });
    return {
        confirmar, salir,
        destruir: () => { eventos.abort(); formularios.clear(); },
        autorizarSalida: () => { salidaAutorizada = true; },
        vigilar(form) {
            const estado = { inicial: valores(form), incierto: false }; formularios.set(form, estado);
            return {
                enviado: () => { estado.incierto = true; },
                guardado: () => { estado.inicial = valores(form); estado.incierto = false; },
                retirar: () => { formularios.delete(form); }
            };
        }
    };
}

import { ref, computed, onBeforeUnmount, onMounted } from "vue";

// Solo memoria. Ningún borrador, contraseña ni token se persiste en el navegador.
const registros = new Set();
const descartados = new Set();
let salidaAutorizada = false;
export function autorizarSalidaDocumento() { salidaAutorizada = true; }
export function confirmarSalida() {
    if ([...registros].some(r => r.ocupado.value)) return false;
    const pendientes = [...registros].filter(r => r.pendiente.value && !descartados.has(r));
    if (!pendientes.length) return true;
    if (!window.confirm("Hay cambios sin guardar o un envío sin confirmar. ¿Descartarlos? Si hubo un error de red, reintenta sin editar para evitar duplicados.")) { descartados.clear(); return false; }
    pendientes.forEach(r => descartados.add(r));
    return true;
}
export function terminarNavegacion(aceptada) {
    if (aceptada) descartados.forEach(r => { if (registros.has(r)) r.descartar(); });
    descartados.clear();
}
export function hayOperacion() { return [...registros].some(r => r.ocupado.value); }
export function useBorrador(datos, ocupado = ref(false)) {
    const inicial = ref(JSON.stringify(datos));
    const incierto = ref(false);
    const pendiente = computed(() => incierto.value || JSON.stringify(datos) !== inicial.value);
    const registro = { ocupado, pendiente, descartar: () => { Object.assign(datos, JSON.parse(inicial.value)); incierto.value = false; } }; registros.add(registro);
    onBeforeUnmount(() => registros.delete(registro));
    return {
        enviado: () => { incierto.value = true; },
        guardado: () => { inicial.value = JSON.stringify(datos); incierto.value = false; }
    };
}
export function useProteccionVentana() {
    const aviso = event => {
        if (!salidaAutorizada && [...registros].some(r => r.ocupado.value || r.pendiente.value)) {
            event.preventDefault(); event.returnValue = "";
        }
    };
    onMounted(() => window.addEventListener("beforeunload", aviso));
    onBeforeUnmount(() => window.removeEventListener("beforeunload", aviso));
}
export function useLectura() {
    let controller;
    const iniciar = () => { controller?.abort(); controller = new AbortController(); return controller.signal; };
    onBeforeUnmount(() => controller?.abort());
    return iniciar;
}
export const claveNueva = () => [...crypto.getRandomValues(new Uint8Array(16))].map(n => n.toString(16).padStart(2, "0")).join("");
export const errorTexto = e => e.status === 401 ? "La sesión venció. Vuelve a iniciar sesión."
    : e.status ? e.message : "No se pudo confirmar la operación. Comprueba la conexión y reintenta sin editar los datos.";
export const fechaLocal = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
export const dinero = (valor, moneda = "COP") => {
    try { return new Intl.NumberFormat("es-CO", { style: "currency", currency: moneda, maximumFractionDigits: 2 }).format(valor); }
    catch { return `${valor} ${moneda}`; }
};

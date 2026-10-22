import { requestJson } from "./apiService.js";

const rutas = { habitaciones: "/habitaciones", reservas: "/reservas", limpieza: "/limpieza", finanzas: "/finanzas/movimientos" };
export function consultar(modulo, parametros = {}, { signal } = {}) {
    return requestJson(`/api/v1${rutas[modulo]}?${new URLSearchParams(parametros)}`, { signal });
}
export function guardar(modulo, datos, clave, id = null) {
    return requestJson(`/api/v1${rutas[modulo]}${id ? `/${encodeURIComponent(id)}` : ""}`, {
        method: id ? "PUT" : "POST", body: datos,
        headers: id ? {} : { "Idempotency-Key": clave }
    });
}
export function cambiarEstado(modulo, id, estado) {
    return requestJson(`/api/v1${rutas[modulo]}/${encodeURIComponent(id)}/estado`, { method: "PUT", body: { estado } });
}
export function eliminar(modulo, id) {
    return requestJson(`/api/v1${rutas[modulo]}/${encodeURIComponent(id)}`, { method: "DELETE" });
}
export function anularMovimiento(id, motivo) {
    return requestJson(`/api/v1/finanzas/movimientos/${encodeURIComponent(id)}/anulacion`, { method: "PUT", body: { motivo } });
}
export function obtenerHabitacion(id, { signal } = {}) {
    return requestJson(`/api/v1/habitaciones/${encodeURIComponent(id)}`, { signal });
}
export function consultarDisponibilidad(parametros) {
    return requestJson(`/api/v1/reservas/disponibilidad?${new URLSearchParams(parametros)}`, { cache: "no-store" });
}
export function resumenFinanzas(moneda, fechas = {}) {
    return requestJson(`/api/v1/finanzas/resumen?${new URLSearchParams({ moneda, ...fechas })}`);
}
export function resumenDashboard(moneda) {
    return requestJson(`/api/v1/dashboard?${new URLSearchParams({ moneda })}`);
}

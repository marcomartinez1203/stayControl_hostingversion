import { createRouter, createWebHashHistory } from "vue-router";
import { confirmarSalida, terminarNavegacion, hayOperacion } from "./lifecycle.js";
import { refrescarSesion, usuario } from "./session.js";
import AuthView from "./views/AuthView.vue";
import DashboardView from "./views/DashboardView.vue";
import ListView from "./views/ListView.vue";
import EditorView from "./views/EditorView.vue";

const routes = [
    { path: "/", redirect: "/dashboard" },
    ...["login", "registro", "recuperar-contrasena"].map(modo => ({ path: `/${modo}`, component: AuthView, props: { modo }, meta: { publica: true } })),
    { path: "/dashboard", component: DashboardView, meta: { titulo: "Dashboard" } },
    ...["habitaciones", "reservas", "limpieza", "finanzas"].flatMap(modulo => [
        { path: `/${modulo}`, component: ListView, props: { modulo }, meta: { titulo: modulo } },
        { path: `/${modulo}/nuevo`, component: EditorView, props: { modulo }, meta: { titulo: modulo } }
    ]),
    { path: "/habitaciones/editar/:id", component: EditorView, props: { modulo: "habitaciones" }, meta: { titulo: "habitaciones" } },
    { path: "/:pathMatch(.*)*", redirect: "/dashboard" }
];
export const router = createRouter({ history: createWebHashHistory(), routes });
router.beforeEach(async to => {
    if (!confirmarSalida()) return false;
    if (!to.meta.publica) {
        try { await refrescarSesion(); }
        catch (error) {
            usuario.value = null;
            return { path: "/login", query: { siguiente: to.fullPath, aviso: error.status === 401 ? "Inicia sesión para continuar." : "No se pudo conectar al servidor." } };
        }
    }
});
// Una escritura puede empezar mientras la comprobación de sesión está pendiente.
router.beforeResolve(() => !hayOperacion());
router.afterEach((to, from, failure) => { terminarNavegacion(!failure); if (!failure) document.title = `StayControl · ${to.meta.titulo || "Acceso"}`; });

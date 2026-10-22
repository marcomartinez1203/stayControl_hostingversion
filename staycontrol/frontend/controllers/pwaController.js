// Mejora progresiva, independiente de los controladores de negocio y de la SPA.
const instalado = () => matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
const panel = document.createElement("details"); panel.className = "pwa-tools";
const summary = document.createElement("summary"); summary.textContent = "Aplicación";
const contenido = document.createElement("div"); contenido.className = "pwa-panel";
const ayuda = document.createElement("p");
ayuda.textContent = instalado() ? "StayControl está abierta como aplicación."
    : "Puedes instalar StayControl desde el menú del navegador. En iPhone/iPad: Compartir → Añadir a pantalla de inicio.";
const instalar = document.createElement("button"); instalar.type = "button";
instalar.textContent = "Instalar StayControl"; instalar.hidden = true;
const estado = document.createElement("p"); estado.setAttribute("role", "status"); estado.setAttribute("aria-live", "polite");
const actualizacion = document.createElement("p"); actualizacion.setAttribute("role", "status");
const conexion = document.createElement("p"); conexion.className = "pwa-connection";
conexion.setAttribute("role", "status"); conexion.setAttribute("aria-live", "polite");
contenido.append(ayuda, instalar, estado, actualizacion); panel.append(summary, contenido);
const shell = document.querySelector(".app, .dashboard");
const destino = shell || document.querySelector(".bottom-area") || document.querySelector("main");
if (shell) {
    shell.insertBefore(conexion, shell.firstChild);
    shell.insertBefore(panel, shell.querySelector(".bottom-nav"));
} else { destino?.append(conexion, panel); }

function mostrarConexion() {
    conexion.hidden = navigator.onLine;
    conexion.textContent = navigator.onLine ? "" : "Sin conexión detectada. Los cambios no se guardan ni se envían automáticamente después.";
}
window.addEventListener("online", mostrarConexion);
window.addEventListener("offline", mostrarConexion);
mostrarConexion();

let invitacion = null;
window.addEventListener("beforeinstallprompt", event => {
    event.preventDefault(); invitacion = event;
    instalar.hidden = instalado(); summary.textContent = instalado() ? "Aplicación" : "Instalar aplicación";
});
instalar.addEventListener("click", async () => {
    if (!invitacion) return;
    const solicitud = invitacion; invitacion = null; instalar.disabled = true;
    try {
        await solicitud.prompt();
        const decision = await solicitud.userChoice;
        estado.textContent = decision.outcome === "accepted" ? "Instalación solicitada al navegador." : "Puedes instalarla más adelante desde el navegador.";
    } catch { estado.textContent = "No se pudo abrir la instalación. Usa el menú del navegador."; }
    finally { instalar.hidden = true; instalar.disabled = false; }
});
window.addEventListener("appinstalled", () => {
    invitacion = null; instalar.hidden = true; summary.textContent = "Aplicación";
    ayuda.textContent = "StayControl instalada. Ábrela desde su icono."; estado.textContent = "";
});

const reintentar = document.getElementById("pwaRetry");
reintentar?.addEventListener("click", () => {
    if (location.pathname === "/offline.html") location.assign("/views/login.html");
    else location.reload();
});

if ("serviceWorker" in navigator && window.isSecureContext) {
    navigator.serviceWorker.register("/sw.js", { scope: "/", updateViaCache: "none" }).then(registro => {
        const avisar = () => {
            if (registro.waiting) actualizacion.textContent = "Actualización disponible. Guarda tus cambios, cierra todas las ventanas de StayControl y vuelve a abrirla.";
        };
        avisar();
        registro.addEventListener("updatefound", () => {
            const worker = registro.installing;
            worker?.addEventListener("statechange", () => { if (worker.state === "installed") avisar(); });
        });
    }).catch(() => {
        estado.textContent = "No se pudo preparar el modo sin conexión. La aplicación sigue disponible mientras tengas conexión al servidor.";
    });
} else {
    estado.textContent = "Para habilitar la PWA abre StayControl por HTTPS o en localhost con un navegador compatible.";
}

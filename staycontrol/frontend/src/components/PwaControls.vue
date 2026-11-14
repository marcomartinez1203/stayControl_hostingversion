<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
const online = ref(navigator.onLine), instalable = ref(false), mensaje = ref(""), ocupado = ref(false);
const comprobando = ref(false), conexionMensaje = ref('');
async function comprobarConexion() {
    if (comprobando.value) return;
    comprobando.value = true;
    conexionMensaje.value = '';
    try {
        const response = await fetch('/api/v1/auth/csrf', { cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000) });
        if (!response.ok) throw new Error('Servidor no disponible');
        await response.json();
        online.value = true;
        conexionMensaje.value = 'Conexión recuperada. Ya puedes volver a intentar tu operación.';
    } catch {
        conexionMensaje.value = navigator.onLine ? 'No se pudo conectar con el servidor. Inténtalo de nuevo.' : 'Todavía no hay conexión. Revisa tu Wi-Fi o tus datos móviles.';
    } finally { comprobando.value = false; }
}
let invitacion, registro, instalando;
const eventos = new AbortController();
async function instalar() {
    if (!invitacion || ocupado.value) return;
    const actual = invitacion; invitacion = null; ocupado.value = true;
    try { await actual.prompt(); const r = await actual.userChoice; mensaje.value = r.outcome === "accepted" ? "Instalación solicitada." : "Puedes instalar más adelante desde el navegador."; }
    catch { mensaje.value = "Usa la opción de instalación del navegador."; }
    finally { instalable.value = false; ocupado.value = false; }
}
const avisar = () => { if (registro?.waiting) mensaje.value = "Actualización disponible. Guarda los cambios, cierra todas las ventanas de StayControl y vuelve a abrirla."; };
onMounted(async () => {
    const options = { signal: eventos.signal };
    window.addEventListener("online", comprobarConexion, options);
    window.addEventListener("offline", () => { online.value = false; conexionMensaje.value = ''; }, options);
    window.addEventListener("beforeinstallprompt", e => { e.preventDefault(); invitacion = e; instalable.value = true; }, options);
    window.addEventListener("appinstalled", () => { invitacion = null; instalable.value = false; mensaje.value = "StayControl instalada."; }, options);
    if (import.meta.env.DEV) { mensaje.value = "Modo desarrollo: la PWA se comprueba con la compilación servida por Spring Boot."; return; }
    if (!("serviceWorker" in navigator) || !window.isSecureContext) { mensaje.value = "La instalación requiere HTTPS o localhost y un navegador compatible."; return; }
    try {
        registro = await navigator.serviceWorker.register("/sw.js", { scope: "/", updateViaCache: "none" });
        if (eventos.signal.aborted) return;
        avisar(); registro.addEventListener("updatefound", () => { instalando = registro.installing; instalando?.addEventListener("statechange", avisar, options); }, options);
    } catch { mensaje.value = "No se pudo preparar la pantalla sin conexión. Puedes seguir usando la aplicación en línea."; }
});
onBeforeUnmount(() => eventos.abort());
</script>
<template>
  <aside v-if="!online || conexionMensaje" class="connection-notice" role="status">
    <svg v-if="!online" viewBox="0 0 24 24" aria-hidden="true"><path d="m3 3 18 18M2 8.5a16 16 0 0 1 3-1.8M9 5.3a16 16 0 0 1 13 3.2M5 12a11 11 0 0 1 3.5-1.8M13 10a11 11 0 0 1 6 2M8.5 15.5a5 5 0 0 1 7 0M12 19h.01"/></svg>
    <div><strong>{{ !online ? 'Sin conexión' : 'Estado de conexión' }}</strong><p>{{ conexionMensaje || 'Revisa tu Wi-Fi o tus datos móviles. Lo que estás escribiendo permanece en esta pantalla.' }}</p>
      <button v-if="!online || conexionMensaje.startsWith('No se pudo')" type="button" :disabled="comprobando" @click="comprobarConexion">{{ comprobando ? 'Comprobando…' : 'Reintentar conexión' }}</button>
      <button v-else type="button" @click="conexionMensaje = ''">Entendido</button>
    </div>
  </aside>
  <details class="pwa-tools"><summary>{{ instalable ? 'Instalar aplicación' : 'Aplicación' }}</summary><div class="pwa-panel">
    <p>Instala StayControl desde el navegador. En iPhone/iPad: Compartir → Añadir a pantalla de inicio.</p>
    <button v-if="instalable" @click="instalar" :disabled="ocupado">Instalar StayControl</button><p role="status">{{ mensaje }}</p>
  </div></details>
</template>
<style scoped>
.connection-notice { flex-shrink:0; display:flex; gap:12px; padding:16px 20px; background:#fff7ed; border-top:1px solid #fed7aa; color:#7c2d12; max-height:35dvh; overflow:auto; }
.connection-notice svg { flex-shrink:0; width:28px; height:28px; fill:none; stroke:currentColor; stroke-width:1.8; stroke-linecap:round; stroke-linejoin:round; }
.connection-notice strong { font-size:17px; }
.connection-notice p { font-size:14px; line-height:1.5; margin:4px 0 10px; }
.connection-notice button { min-height:44px; padding:10px 16px; background:#7c2d12; color:white; border:0; border-radius:12px; font:600 14px var(--font-ui); cursor:pointer; }
</style>

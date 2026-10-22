<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
const online = ref(navigator.onLine), instalable = ref(false), mensaje = ref(""), ocupado = ref(false);
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
    window.addEventListener("online", () => { online.value = true; }, options);
    window.addEventListener("offline", () => { online.value = false; }, options);
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
  <p v-if="!online" class="pwa-connection" role="status">Sin conexión detectada. Las operaciones requieren conexión al servidor.</p>
  <details class="pwa-tools"><summary>{{ instalable ? 'Instalar aplicación' : 'Aplicación' }}</summary><div class="pwa-panel">
    <p>Instala StayControl desde el navegador. En iPhone/iPad: Compartir → Añadir a pantalla de inicio.</p>
    <button v-if="instalable" @click="instalar" :disabled="ocupado">Instalar StayControl</button><p role="status">{{ mensaje }}</p>
  </div></details>
</template>

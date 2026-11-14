import { createApp, nextTick } from "vue";
import { waitForStartupReads } from "../services/startupService.js";
import App from "./App.vue";
import { router } from "./router.js";
import "../assets/css/login.css";
import "../assets/css/dashboard.css";
import "../assets/css/negocio.css";
import "../assets/css/experiencia.css";
import "../assets/css/pwa.css";
import "./style.css";

const app = createApp(App).use(router);
router.isReady().then(async () => {
    app.mount("#app");
    await nextTick();
    // La vista se monta detrás de la animación y utiliza sus peticiones normales.
    // El límite permite mostrar sus mensajes/reintentos si el servidor tarda.
    const resources = Promise.allSettled([
        document.fonts?.ready,
        ...Array.from(document.querySelectorAll('#app img, #startup img'))
            .filter(image => typeof image.decode === 'function')
            .map(image => image.decode())
    ]);
    let resourceTimer;
    await Promise.all([
        waitForStartupReads(),
        Promise.race([resources, new Promise(resolve => { resourceTimer = setTimeout(resolve, 3000); })])
    ]);
    clearTimeout(resourceTimer);
    await nextTick();
    window.dispatchEvent(new Event("staycontrol:ready"));
});

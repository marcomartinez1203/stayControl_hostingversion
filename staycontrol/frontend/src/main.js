import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router.js";
import "../assets/css/login.css";
import "../assets/css/dashboard.css";
import "../assets/css/negocio.css";
import "../assets/css/experiencia.css";
import "../assets/css/pwa.css";
import "./style.css";

const app = createApp(App).use(router);
router.isReady().then(() => app.mount("#app"));

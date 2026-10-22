import { ref } from "vue";
import { consultarSesion } from "../services/authService.js";
export const usuario = ref(null);
export async function refrescarSesion() {
    usuario.value = await consultarSesion();
    return usuario.value;
}

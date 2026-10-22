<script setup>
import { computed, reactive, ref } from "vue";
import { RouterLink, useRouter, useRoute } from "vue-router";
import { iniciarSesion, registrarUsuario } from "../../services/authService.js";
import { refrescarSesion } from "../session.js";
import { claveNueva, useBorrador, errorTexto, hayOperacion } from "../lifecycle.js";
const props = defineProps({ modo: String });
const registro = computed(() => props.modo === "registro"), recuperacion = computed(() => props.modo === "recuperar-contrasena");
const datos = reactive({ usuario: "", nombreHotel: "", email: "", password: "", confirmacion: "" });
const ocupado = ref(false), mostrar = ref(false), exito = ref(false), error = ref(false), mensaje = ref("");
const borrador = useBorrador(datos, ocupado);
const route = useRoute(), router = useRouter();
let clave, payloadAnterior;
async function enviar() {
    if (hayOperacion()) return;
    error.value = false;
    if (registro.value && datos.password !== datos.confirmacion) { mensaje.value = "Las contraseñas no coinciden."; error.value = true; return; }
    ocupado.value = true;
    try {
        if (registro.value) {
            const payload = { usuario: datos.usuario.trim(), nombreHotel: datos.nombreHotel.trim(), email: datos.email.trim(), password: datos.password };
            const serializado = JSON.stringify(payload);
            if (serializado !== payloadAnterior) { clave = claveNueva(); payloadAnterior = serializado; }
            borrador.enviado(); await registrarUsuario(payload, clave);
            Object.keys(datos).forEach(k => { datos[k] = ""; }); payloadAnterior = null; clave = null;
            borrador.guardado(); exito.value = true; mensaje.value = "Cuenta creada. Inicia sesión para administrar tu hotel.";
        } else {
            await iniciarSesion(datos.email.trim(), datos.password); await refrescarSesion();
            datos.password = ""; borrador.guardado(); ocupado.value = false;
            const siguiente = typeof route.query.siguiente === "string" && /^\/(dashboard|habitaciones|reservas|limpieza|finanzas)(\/|\?|$)/.test(route.query.siguiente) ? route.query.siguiente : "/dashboard";
            await router.replace(siguiente);
        }
    } catch (e) { error.value = true; mensaje.value = e.status === 401 ? "Correo o contraseña incorrectos." : errorTexto(e); }
    finally { ocupado.value = false; }
}
</script>
<template>
  <section class="login" :class="{ registro, recuperacion }">
    <div class="top-area"><header class="brand-section">
      <img class="logo-box" src="/assets/icons/pwa-192.png" alt="" width="64" height="64">
      <div class="brand-text"><p v-if="registro || recuperacion" class="brand-name">StayControl</p><h1>{{ registro ? 'Crear cuenta' : recuperacion ? 'Recuperar contraseña' : 'StayControl' }}</h1>
      <p>{{ registro ? 'Registra tu hotel para comenzar' : recuperacion ? 'Recupera el acceso a tu cuenta' : 'Gestión inteligente de arriendos' }}</p></div>
    </header>
    <p v-if="recuperacion" class="view-notice">La recuperación de contraseña está pendiente de habilitar. Todavía no se envían correos.</p>
    <form v-else id="accesoForm" class="form-section" @submit.prevent="enviar" :aria-busy="ocupado">
      <fieldset :disabled="ocupado || exito" class="vue-fields">
        <label v-if="registro" class="field">Nombre de usuario<div class="input-container"><input v-model="datos.usuario" required maxlength="120" autocomplete="nickname"></div></label>
        <label v-if="registro" class="field">Nombre del hotel<div class="input-container"><input v-model="datos.nombreHotel" required maxlength="120" autocomplete="organization"></div></label>
        <label class="field">Correo electrónico<div class="input-container"><input v-model="datos.email" type="email" required maxlength="254" autocomplete="username" placeholder="tu-correo@ejemplo.com"></div></label>
        <label class="field">Contraseña<div class="input-container"><input v-model="datos.password" :type="mostrar ? 'text' : 'password'" required :minlength="registro ? 12 : undefined" maxlength="128" :autocomplete="registro ? 'new-password' : 'current-password'"><button type="button" class="eye-button" :aria-label="mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña'" @click="mostrar = !mostrar">{{ mostrar ? '◉' : '◎' }}</button></div></label>
        <label v-if="registro" class="field">Confirmar contraseña<div class="input-container"><input v-model="datos.confirmacion" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></div><small>Entre 12 y 128 caracteres.</small></label>
      </fieldset>
      <RouterLink v-if="!registro" to="/recuperar-contrasena" class="forgot-password">¿Olvidaste tu contraseña?</RouterLink>
    </form></div>
    <div class="bottom-area">
      <p role="status" class="api-aviso" :data-error="error">{{ mensaje || route.query.aviso }}</p>
      <button v-if="!recuperacion" type="submit" form="accesoForm" class="login-button" :disabled="ocupado || exito">{{ ocupado ? 'Procesando…' : registro ? 'Crear cuenta' : 'Iniciar sesión' }}</button>
      <div class="register-footer"><template v-if="!registro && !recuperacion"><span>¿No tienes una cuenta?</span><RouterLink to="/registro">Regístrate</RouterLink></template><RouterLink v-else to="/login">Volver a iniciar sesión</RouterLink></div>
    </div>
  </section>
</template>

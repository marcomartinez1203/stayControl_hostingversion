<script setup>
import { computed, ref, reactive, watch } from "vue";
import { useRoute, useRouter, RouterView, RouterLink } from "vue-router";
import { usuario } from "./session.js";
import { cerrarSesion, actualizarHotel } from "../services/authService.js";
import { useBorrador, useProteccionVentana, confirmarSalida, autorizarSalidaDocumento, errorTexto, hayOperacion } from "./lifecycle.js";
import Icon from "./components/Icon.vue";
import PwaControls from "./components/PwaControls.vue";
const route = useRoute(), router = useRouter();
const publica = computed(() => route.meta.publica);
const seccion = computed(() => route.path.split("/")[1]);
const etiquetas = { dashboard: "Inicio", habitaciones: "Habitaciones", reservas: "Reservas", finanzas: "Finanzas", limpieza: "Limpieza" };
const cuenta = reactive({ nombreHotel: "" });
const ocupado = ref(false), mensaje = ref("");
const borrador = useBorrador(cuenta, ocupado);
watch(() => usuario.value?.email, () => { cuenta.nombreHotel = usuario.value?.nombreHotel || ""; borrador.guardado(); mensaje.value = ""; }, { immediate: true });
useProteccionVentana();
async function guardarHotel() {
    if (hayOperacion()) return;
    ocupado.value = true; borrador.enviado(); mensaje.value = "Guardando…";
    try {
        const resultado = await actualizarHotel(cuenta.nombreHotel.trim());
        cuenta.nombreHotel = resultado.nombreHotel; usuario.value.nombreHotel = resultado.nombreHotel;
        borrador.guardado(); mensaje.value = "Nombre actualizado.";
    } catch (e) { mensaje.value = errorTexto(e); }
    finally { ocupado.value = false; }
}
async function salir() {
    if (!confirmarSalida()) return;
    ocupado.value = true;
    try {
        await cerrarSesion(); usuario.value = null;
        // Un documento nuevo elimina también datos/borradores que permanezcan en memoria.
        autorizarSalidaDocumento(); location.replace("/index.html#/login");
    } catch (e) { mensaje.value = errorTexto(e); }
    finally { ocupado.value = false; }
}
</script>
<template>
  <div :class="publica ? 'vue-auth-shell' : 'app dashboard vue-shell'">
    <header v-if="!publica" class="header">
      <div><p v-if="seccion === 'dashboard'" class="hello">Bienvenido</p><h1>{{ seccion === 'dashboard' ? usuario?.nombreHotel || 'Mi hotel' : etiquetas[seccion] }}</h1></div>
      <details class="api-menu"><summary>Cuenta</summary><div class="api-cuenta-panel">
        <p>{{ usuario?.email }}</p>
        <form class="api-hotel-form" @submit.prevent="guardarHotel" :aria-busy="ocupado">
          <label>Nombre del hotel<input v-model="cuenta.nombreHotel" required maxlength="120" :disabled="ocupado"></label>
          <button :disabled="ocupado">Guardar hotel</button>
        </form><p role="status">{{ mensaje }}</p><button type="button" :disabled="ocupado" @click="salir">Cerrar sesión</button>
      </div></details>
    </header>
    <main :class="publica ? 'vue-auth-content' : 'content vue-main'">
      <RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" /></RouterView>
    </main>
    <PwaControls />
    <nav v-if="!publica" class="bottom-nav" aria-label="Navegación principal">
      <RouterLink v-for="(etiqueta, ruta) in etiquetas" :key="ruta" :to="`/${ruta}`" class="nav-item" :class="{ active: seccion === ruta }" :aria-current="seccion === ruta ? 'page' : undefined">
        <Icon :nombre="ruta === 'dashboard' ? 'inicio' : ruta"/><span>{{ etiqueta }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

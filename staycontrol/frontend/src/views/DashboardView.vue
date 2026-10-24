<script setup>
import { ref, onMounted } from "vue";
import { RouterLink } from "vue-router";
import { requestJson } from "../../services/apiService.js";
import { useLectura, dinero, errorTexto } from "../lifecycle.js";
import Icon from "../components/Icon.vue";
const moneda = ref("COP"), datos = ref(null), mensaje = ref(""), cargando = ref(false);
const lectura = useLectura();
async function cargar() {
  const signal = lectura(); cargando.value = true; mensaje.value = "";
  try { const respuesta = await requestJson(`/api/v1/dashboard?${new URLSearchParams({ moneda: moneda.value.toUpperCase() })}`, { signal }); if (!signal.aborted) datos.value = respuesta; }
  catch(e) { if (!signal.aborted) { datos.value = null; mensaje.value = errorTexto(e); } }
  finally { if (!signal.aborted) cargando.value = false; }
}
onMounted(cargar);
</script>
<template>
  <section class="vue-stack" :aria-busy="cargando">
    <details class="vue-tools"><summary>Gestionar / actualizar</summary><form class="vue-inline" @submit.prevent="cargar"><label>Moneda<input v-model="moneda" required pattern="[A-Za-z]{3}" maxlength="3"></label><button :disabled="cargando">Actualizar</button></form></details>
    <p v-if="mensaje || cargando" role="status" class="view-notice">{{ cargando ? 'Cargando resumen…' : mensaje }}</p>
    <template v-if="datos">
      <div class="card occupation-card"><div class="card-title"><strong>Ocupación de Habitaciones</strong><span>{{ datos.habitacionesActuales.ocupadas }} / {{ datos.habitacionesActuales.total }}</span></div><div class="progress" role="progressbar" aria-label="Ocupación" :aria-valuenow="datos.habitacionesActuales.porcentajeOcupacion" aria-valuemin="0" aria-valuemax="100"><div class="progress-fill" :style="{ width: `${datos.habitacionesActuales.porcentajeOcupacion}%` }"></div></div><div class="legend"><span class="legend-item">{{ datos.habitacionesActuales.ocupadas }} Ocupadas</span><span class="legend-item">{{ datos.habitacionesActuales.disponibles }} Disponibles</span></div><small>{{ datos.habitacionesActuales.enLimpieza }} en limpieza · {{ datos.habitacionesActuales.enMantenimiento }} en mantenimiento</small></div>
      <div class="finance-grid"><div class="card finance-card"><span class="finance-title"><Icon nombre="ingreso"/>Ingresos</span><strong class="income">{{ dinero(datos.finanzasMes.ingresos, datos.finanzasMes.moneda) }}</strong><small>Este mes</small></div><div class="card finance-card"><span class="finance-title"><Icon nombre="gasto"/>Gastos</span><strong class="expense">{{ dinero(datos.finanzasMes.egresos, datos.finanzasMes.moneda) }}</strong><small>Este mes</small></div></div>
      <RouterLink to="/limpieza" class="alert"><div class="info-icon"><Icon nombre="info"/></div><div><strong>Limpieza pendiente</strong><p>{{ datos.limpieza.habitacionesConTareas }} habitaciones con tareas abiertas.</p></div></RouterLink>
      <p class="view-notice">Hoy: {{ datos.reservasDelDia.entradasPrevistas }} entradas · {{ datos.reservasDelDia.salidasPrevistas }} salidas · {{ datos.reservasDelDia.estanciasProgramadas }} estancias previstas.</p>
      <section class="reservations"><div class="section-title"><h2>Próximas reservas</h2><RouterLink to="/reservas">Ver todas</RouterLink></div><p v-if="!datos.proximasReservas.length" class="vue-empty">No hay próximas reservas.</p><RouterLink v-for="r in datos.proximasReservas" :key="r.id" :to="{path:'/reservas',query:{habitacionId:r.habitacionId}}" class="reservation"><span class="room">{{ r.numeroHabitacion }}</span><div class="reservation-info"><strong>{{ r.huesped }}</strong><span>{{ r.fechaEntrada }} → {{ r.fechaSalida }}</span><span>{{ r.personas }} personas</span></div></RouterLink></section>
    </template>
  </section>
</template>

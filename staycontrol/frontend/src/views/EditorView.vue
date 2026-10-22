<script setup>
import { reactive, ref, computed, watch, onMounted } from "vue";
import { useRoute, useRouter, RouterLink } from "vue-router";
import RoomSelector from "../components/RoomSelector.vue";
import { guardar, obtenerHabitacion } from "../../services/negocioService.js";
import { requestJson } from "../../services/apiService.js";
import { useBorrador, useLectura, claveNueva, errorTexto, fechaLocal, dinero, hayOperacion } from "../lifecycle.js";
const props = defineProps({ modulo: String });
const route = useRoute(), router = useRouter(), id = route.params.id;
const datos = reactive(props.modulo === 'habitaciones' ? {numero:'',tipo:'',capacidad:1,precioNoche:'',estado:'DISPONIBLE'} : props.modulo === 'reservas' ? {habitacionId:String(route.query.habitacionId || ''),huesped:'',personas:1,fechaEntrada:fechaLocal(),fechaSalida:''} : props.modulo === 'limpieza' ? {habitacionId:String(route.query.habitacionId || ''),descripcion:'',responsable:'',fechaProgramada:fechaLocal()} : {tipo:'INGRESO',concepto:'',importe:'',moneda:'COP',fecha:fechaLocal(),metodoPago:'EFECTIVO'});
const ocupado = ref(false), cargando = ref(Boolean(id)), mensaje = ref(''), cotizacion = ref(null), consultando = ref(false), errorCarga = ref(false);
const borrador = useBorrador(datos, ocupado), lectura = useLectura(), disponibilidad = useLectura();
const titulo = computed(() => id ? 'Editar habitación' : ({habitaciones:'Nueva habitación',reservas:'Nueva reserva',limpieza:'Programar limpieza',finanzas:'Nuevo movimiento'}[props.modulo]));
let clave, anterior;
watch(() => [datos.habitacionId, datos.personas, datos.fechaEntrada, datos.fechaSalida], () => { disponibilidad(); cotizacion.value = null; consultando.value = false; });
onMounted(async () => {
  if (!id) return;
  const signal = lectura();
  try { const h = await obtenerHabitacion(id, {signal}); if (!signal.aborted) { Object.keys(datos).forEach(k => { datos[k] = k === 'precioNoche' ? String(h[k]) : h[k]; }); borrador.guardado(); } }
  catch(e) { if (!signal.aborted) { mensaje.value = errorTexto(e); errorCarga.value = true; } }
  finally { if (!signal.aborted) cargando.value = false; }
});
async function cotizar() {
  if (!datos.habitacionId || !datos.fechaSalida || datos.fechaSalida <= datos.fechaEntrada) { mensaje.value = 'Selecciona habitación y fechas válidas.'; return; }
  const signal = disponibilidad(); consultando.value = true; mensaje.value = ''; cotizacion.value = null;
  try { const respuesta = await requestJson(`/api/v1/reservas/disponibilidad?${new URLSearchParams({habitacionId:datos.habitacionId,personas:datos.personas,fechaEntrada:datos.fechaEntrada,fechaSalida:datos.fechaSalida})}`, {signal}); if (!signal.aborted) cotizacion.value = respuesta; }
  catch(e) { if (!signal.aborted) mensaje.value = errorTexto(e); }
  finally { if (!signal.aborted) consultando.value = false; }
}
async function enviar() {
  if (hayOperacion() || cargando.value || errorCarga.value) return;
  if (props.modulo === 'reservas' && datos.fechaSalida <= datos.fechaEntrada) { mensaje.value = 'La salida debe ser posterior a la entrada.'; return; }
  const payload = Object.fromEntries(Object.entries(datos).map(([k,v]) => [k, typeof v === 'string' ? v.trim() : v]));
  for (const k of ['capacidad','personas']) if (k in payload) payload[k] = Number(payload[k]);
  if ('moneda' in payload) payload.moneda = payload.moneda.toUpperCase();
  const serial = JSON.stringify(payload); if (serial !== anterior) { clave = claveNueva(); anterior = serial; }
  ocupado.value = true; borrador.enviado(); mensaje.value = 'Guardando…';
  try { await guardar(props.modulo, payload, clave, id); borrador.guardado(); ocupado.value = false; await router.push({path:`/${props.modulo}`,query:{aviso:'Guardado correctamente.'}}); }
  catch(e) { mensaje.value = errorTexto(e); }
  finally { ocupado.value = false; }
}
</script>
<template>
  <section class="vue-stack"><div class="section-title"><h2>{{ titulo }}</h2><RouterLink :to="`/${modulo}`">Volver</RouterLink></div><p role="status" class="view-notice">{{ cargando ? 'Cargando…' : mensaje }}</p>
  <form class="vue-editor card" @submit.prevent="enviar" :aria-busy="ocupado"><fieldset class="vue-fields" :disabled="ocupado || cargando || errorCarga">
    <template v-if="modulo === 'habitaciones'"><label>Número<input v-model="datos.numero" required maxlength="20" :readonly="Boolean(id)"></label><label>Tipo<input v-model="datos.tipo" required maxlength="60"></label><label>Capacidad<input v-model="datos.capacidad" type="number" min="1" max="20" step="1" required></label><label>Precio por noche<input v-model="datos.precioNoche" type="text" inputmode="decimal" pattern="[0-9]{1,9}(\.[0-9]{1,2})?" title="Importe mayor a cero; usa punto para decimales" required></label><label>Estado<select v-model="datos.estado"><option v-for="e in ['DISPONIBLE','OCUPADA','LIMPIEZA','MANTENIMIENTO']" :key="e">{{ e }}</option></select></label></template>
    <RoomSelector v-if="['reservas','limpieza'].includes(modulo)" v-model="datos.habitacionId" :disabled="ocupado"/>
    <template v-if="modulo === 'reservas'"><label>Huésped<input v-model="datos.huesped" required maxlength="120"></label><label>Personas<input v-model="datos.personas" type="number" min="1" max="20" step="1" required></label><label>Entrada<input v-model="datos.fechaEntrada" type="date" min="2000-01-01" max="2100-12-31" required></label><label>Salida<input v-model="datos.fechaSalida" type="date" :min="datos.fechaEntrada || '2000-01-01'" max="2100-12-31" required></label><button type="button" class="secondary" :disabled="consultando" @click="cotizar">{{ consultando ? 'Consultando…' : 'Consultar disponibilidad' }}</button><p v-if="cotizacion" class="view-notice" role="status">{{ cotizacion.disponible ? `Disponible · ${cotizacion.noches} noches · ${dinero(cotizacion.total)}` : cotizacion.motivo }}. La disponibilidad se confirma al guardar.</p></template>
    <template v-if="modulo === 'limpieza'"><label>Descripción<textarea v-model="datos.descripcion" required maxlength="500"></textarea></label><label>Responsable<input v-model="datos.responsable" maxlength="120"></label><label>Fecha programada<input v-model="datos.fechaProgramada" type="date" min="2000-01-01" max="2100-12-31" required></label></template>
    <template v-if="modulo === 'finanzas'"><label>Tipo<select v-model="datos.tipo"><option value="INGRESO">Ingreso</option><option value="EGRESO">Gasto</option></select></label><label>Concepto<input v-model="datos.concepto" required maxlength="200"></label><label>Importe<input v-model="datos.importe" required type="text" inputmode="decimal" pattern="[0-9]{1,12}(\.[0-9]{1,4})?" title="Importe mayor a cero; usa punto para decimales"></label><label>Moneda<input v-model="datos.moneda" required pattern="[A-Za-z]{3}" maxlength="3"></label><label>Fecha<input v-model="datos.fecha" type="date" min="2000-01-01" max="2100-12-31" required></label><label>Método de pago<select v-model="datos.metodoPago"><option v-for="m in ['EFECTIVO','TRANSFERENCIA','TARJETA','OTRO']" :key="m">{{ m }}</option></select></label></template>
    <button type="submit">{{ ocupado ? 'Guardando…' : 'Guardar' }}</button>
  </fieldset></form></section>
</template>

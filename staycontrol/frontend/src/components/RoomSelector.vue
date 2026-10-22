<script setup>
import { ref, watch, computed } from "vue";
import { consultar, obtenerHabitacion } from "../../services/negocioService.js";
import { useLectura, errorTexto } from "../lifecycle.js";
const props = defineProps({ modelValue: String, disabled: Boolean });
const emit = defineEmits(["update:modelValue"]);
const buscar = ref(""), pagina = ref(0), items = ref([]), total = ref(0), seleccionado = ref(null), mensaje = ref(""), cargando = ref(false);
const lectura = useLectura(), seleccionLectura = useLectura();
const opciones = computed(() => seleccionado.value && !items.value.some(h => h.id === seleccionado.value.id) ? [seleccionado.value, ...items.value] : items.value);
async function cargar() {
  const signal = lectura(); cargando.value = true; mensaje.value = "";
  try { const resultado = await consultar("habitaciones", { buscar: buscar.value.trim(), pagina: pagina.value, tamano: 20 }, { signal }); if (!signal.aborted) { items.value = resultado.items; total.value = resultado.total; } }
  catch (e) { if (!signal.aborted) mensaje.value = errorTexto(e); }
  finally { if (!signal.aborted) cargando.value = false; }
}
watch(pagina, cargar);
watch(() => props.modelValue, async id => {
  const signal = seleccionLectura(); seleccionado.value = null;
  if (!id) return;
  try { const h = await obtenerHabitacion(id, { signal }); if (!signal.aborted) seleccionado.value = h; }
  catch (e) { if (!signal.aborted) mensaje.value = errorTexto(e); }
}, { immediate: true });
function buscarAhora() { if (pagina.value) pagina.value = 0; else cargar(); }
cargar();
</script>
<template>
  <div class="room-selector">
    <label>Buscar habitación<input v-model="buscar" :disabled="disabled" maxlength="60" placeholder="Número o tipo" @keydown.enter.prevent="buscarAhora"></label>
    <button type="button" class="secondary" :disabled="disabled || cargando" @click="buscarAhora">Buscar</button>
    <label>Habitación<select required :value="modelValue" :disabled="disabled" @change="emit('update:modelValue', $event.target.value)"><option value="">Selecciona una habitación</option><option v-for="h in opciones" :key="h.id" :value="h.id">Hab. {{ h.numero }} · {{ h.tipo }} · {{ h.capacidad }} personas · {{ h.estado }}</option></select></label>
    <div class="vue-pagination"><button type="button" :disabled="disabled || cargando || pagina === 0" @click="pagina--">Anterior</button><small>{{ pagina + 1 }} · {{ total }} habitaciones</small><button type="button" :disabled="disabled || cargando || (pagina + 1) * 20 >= total" @click="pagina++">Siguiente</button></div>
    <p role="status">{{ cargando ? 'Cargando habitaciones…' : mensaje }}</p>
  </div>
</template>

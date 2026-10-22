<script setup>
import { reactive, ref, computed, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { consultar, obtenerHabitacion, eliminar, cambiarEstado, anularMovimiento } from "../../services/negocioService.js";
import { requestJson } from "../../services/apiService.js";
import { useLectura, useBorrador, fechaLocal, dinero, errorTexto, hayOperacion } from "../lifecycle.js";
import Icon from "../components/Icon.vue";
const props = defineProps({ modulo: String });
const route = useRoute(), router = useRouter();
const estados = {habitaciones:['DISPONIBLE','OCUPADA','LIMPIEZA','MANTENIMIENTO'],reservas:['CONFIRMADA','CANCELADA','FINALIZADA'],limpieza:['PENDIENTE','EN_PROCESO','COMPLETADA','CANCELADA'],finanzas:['REGISTRADO','ANULADO']};
const etiquetas = {DISPONIBLE:'Disponibles',OCUPADA:'Ocupadas',LIMPIEZA:'Limpieza',MANTENIMIENTO:'Mantenimiento',CONFIRMADA:'Confirmada',CANCELADA:'Cancelada',FINALIZADA:'Finalizada',PENDIENTE:'Pendiente',EN_PROCESO:'En proceso',COMPLETADA:'Completada',REGISTRADO:'Registrado',ANULADO:'Anulado'};
const filtros = reactive({estado:'',buscar:'',moneda:'COP',fecha:'',desde:'',hasta:'',tipo:'INGRESO'});
const items = ref([]), total = ref(0), cargando = ref(false), ocupado = ref(false), mensaje = ref(''), resumen = ref(null), meses = ref([]), habitaciones = ref({});
const lectura = useLectura();
const accion = reactive({id:'',motivo:''}); const borrador = useBorrador(accion, ocupado);
const pagina = computed(() => Math.max(0, Number(route.query.pagina) || 0));
const semana = ref(fechaLocal());
const dias = computed(() => Array.from({length:6}, (_,i) => {const d = new Date(`${semana.value}T12:00:00`); d.setDate(d.getDate()+i); return {fecha:fechaLocal(d),nombre:d.toLocaleDateString('es-CO',{weekday:'short'}),numero:d.getDate()};}));
const maxGrafico = computed(() => Math.max(1,...meses.value.flatMap(m => [Number(m.ingresos),Number(m.egresos)])));
const montoMoneda = computed(() => String(route.query.moneda || 'COP').toUpperCase());
function parametros() {
  const permitidos = props.modulo === 'habitaciones' ? ['estado','buscar'] : props.modulo === 'reservas' ? ['estado','buscar','fecha','habitacionId'] : props.modulo === 'limpieza' ? ['estado','fecha','habitacionId'] : ['estado','moneda','desde','hasta','tipo'];
  return Object.fromEntries(permitidos.map(k => [k, String(route.query[k] || (k === 'moneda' ? 'COP' : k === 'tipo' ? 'INGRESO' : ''))]).filter(([,v])=>v));
}
async function navegar(cambios, conservar = true) {
  if (hayOperacion()) return;
  const query = {...(conservar ? route.query : {}),...cambios}; delete query.aviso;
  Object.keys(query).forEach(k => {if (query[k] === '' || query[k] == null) delete query[k];});
  if (JSON.stringify(query) === JSON.stringify(route.query)) await cargar();
  else await router.push({path:`/${props.modulo}`,query});
}
function aplicar() {
  if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) { mensaje.value = 'La fecha final no puede ser anterior a la inicial.'; return; }
  const llaves = props.modulo === 'habitaciones' ? ['estado','buscar'] : props.modulo === 'reservas' ? ['estado','buscar','fecha'] : props.modulo === 'limpieza' ? ['estado','fecha'] : ['estado','moneda','desde','hasta','tipo'];
  navegar({...Object.fromEntries(llaves.map(k=>[k,filtros[k].trim()])),pagina:0});
}
function moverSemana(n) {const d = new Date(`${semana.value}T12:00:00`);d.setDate(d.getDate()+n);semana.value = fechaLocal(d);}
function esteMes() {const d = new Date();filtros.desde=fechaLocal(new Date(d.getFullYear(),d.getMonth(),1));filtros.hasta=fechaLocal(new Date(d.getFullYear(),d.getMonth()+1,0));aplicar();}
async function cargar() {
  const signal = lectura(); cargando.value = true; mensaje.value = String(route.query.aviso || ''); items.value=[]; resumen.value=null; meses.value=[];
  try {
    const parametrosActuales = parametros();
    const resultado = await consultar(props.modulo,{...parametrosActuales,pagina:pagina.value,tamano:20},{signal});
    if (signal.aborted) return;
    if (pagina.value > 0 && !resultado.items.length) { await navegar({pagina:Math.max(0,Math.ceil(resultado.total/20)-1)}); return; }
    items.value = resultado.items; total.value = resultado.total;
    const ids = [...new Set([...resultado.items.map(i=>i.habitacionId),route.query.habitacionId].filter(Boolean))];
    await Promise.all(ids.map(async id => {try {const h = await obtenerHabitacion(id,{signal});if (!signal.aborted) habitaciones.value[id] = h.numero;}catch(e){if (e.status===401) throw e;}}));
    if (props.modulo === 'finanzas') {
      const {moneda,desde,hasta} = parametrosActuales;
      const base = {moneda,...(desde?{desde}:{}),...(hasta?{hasta}:{})};
      const r = await requestJson(`/api/v1/finanzas/resumen?${new URLSearchParams(base)}`,{signal});
      if (signal.aborted) return; resumen.value=r;
      const ancla = new Date(`${hasta || fechaLocal()}T12:00:00`);
      const columnas = await Promise.all(Array.from({length:6},async (_,i)=>{
        const inicio = fechaLocal(new Date(ancla.getFullYear(),ancla.getMonth()-5+i,1));
        const fin = fechaLocal(new Date(ancla.getFullYear(),ancla.getMonth()-4+i,0));
        const a = desde && desde > inicio ? desde : inicio, b = hasta && hasta < fin ? hasta : fin;
        const valores = a > b ? {ingresos:0,egresos:0} : await requestJson(`/api/v1/finanzas/resumen?${new URLSearchParams({moneda,desde:a,hasta:b})}`,{signal});
        return {...valores,mes:inicio.slice(0,7),etiqueta:new Date(`${inicio}T12:00:00`).toLocaleDateString('es-CO',{month:'short'})};
      }));
      if (!signal.aborted) meses.value=columnas;
    }
  } catch(e) { if (!signal.aborted) mensaje.value=errorTexto(e); }
  finally {if (!signal.aborted) cargando.value=false;}
}
watch(() => route.fullPath, () => {Object.keys(filtros).forEach(k=>{filtros[k]=String(route.query[k] || (k==='moneda'?'COP':k==='tipo'?'INGRESO':''));}); if (route.query.fecha) semana.value=String(route.query.fecha); cargar();},{immediate:true});
async function ejecutar(item, operacion, estado) {
  if (hayOperacion() || !window.confirm(`${operacion === 'eliminar' ? (props.modulo === 'habitaciones' ? 'Eliminar habitación' : 'Cancelar registro') : 'Cambiar estado a '+etiquetas[estado]}?`)) return;
  ocupado.value=true; borrador.enviado();
  try {if (operacion==='eliminar') await eliminar(props.modulo,item.id); else await cambiarEstado(props.modulo,item.id,estado); borrador.guardado(); await cargar(); mensaje.value='Operación completada.';}
  catch(e){mensaje.value=errorTexto(e);}
  finally{ocupado.value=false;}
}
async function anular() {
  if (hayOperacion() || !accion.motivo.trim()) return;
  ocupado.value=true;borrador.enviado();
  try {await anularMovimiento(accion.id,accion.motivo.trim());accion.id='';accion.motivo='';borrador.guardado();await cargar();mensaje.value='Movimiento anulado.';}
  catch(e){mensaje.value=errorTexto(e);}
  finally{ocupado.value=false;}
}
function cerrarAnulacion(){if (!accion.motivo || window.confirm('¿Descartar el motivo escrito?')){accion.id='';accion.motivo='';borrador.guardado();}}
function exportar() {
  if (!resumen.value || cargando.value) return;
  const blob=new Blob([JSON.stringify({resumen:resumen.value,analisisMensual:meses.value},null,2)],{type:'application/json'});
  const url=URL.createObjectURL(blob), enlace=document.createElement('a');enlace.href=url;enlace.download=`staycontrol-finanzas-${fechaLocal()}.json`;enlace.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
const numero = item => habitaciones.value[item.habitacionId] || 'No disponible';
const vencida = item => ['PENDIENTE','EN_PROCESO'].includes(item.estado) && item.fechaProgramada < fechaLocal();
</script>
<template>
 <section class="vue-stack" :aria-busy="cargando || ocupado">
  <div class="vue-toolbar"><RouterLink class="vue-button" :to="{path:`/${modulo}/nuevo`,query:route.query.habitacionId?{habitacionId:route.query.habitacionId}:{}}"><Icon nombre="agregar"/>{{ {habitaciones:'Nueva habitación',reservas:'Nueva reserva',limpieza:'Programar limpieza',finanzas:'Nuevo movimiento'}[modulo] }}</RouterLink><button v-if="modulo==='finanzas'" type="button" class="secondary" aria-label="Descargar resumen financiero" :disabled="!resumen || cargando" @click="exportar"><Icon nombre="descargar"/></button></div>
  <div v-if="modulo==='habitaciones'" class="vue-filters"><button v-for="e in ['',...estados.habitaciones]" :key="e" :class="{active:filtros.estado===e}" :aria-pressed="filtros.estado===e" :disabled="ocupado" @click="navegar({estado:e,pagina:0})">{{ etiquetas[e] || 'Todas' }}</button></div>
  <details class="vue-tools"><summary>Buscar / filtrar</summary><form class="vue-fields" @submit.prevent="aplicar"><fieldset class="vue-fields" :disabled="ocupado">
    <label>Estado<select v-model="filtros.estado"><option value="">Todos</option><option v-for="e in estados[modulo]" :key="e" :value="e">{{ etiquetas[e] }}</option></select></label>
    <label v-if="['habitaciones','reservas'].includes(modulo)">{{ modulo==='habitaciones'?'Número o tipo':'Huésped' }}<input v-model="filtros.buscar" type="search" :maxlength="modulo==='habitaciones'?60:120"></label>
    <label v-if="['reservas','limpieza'].includes(modulo)">{{ modulo==='reservas'?'Fecha de estancia':'Fecha programada' }}<input v-model="filtros.fecha" type="date" min="2000-01-01" max="2100-12-31"></label>
    <template v-if="modulo==='finanzas'"><label>Moneda<input v-model="filtros.moneda" required pattern="[A-Za-z]{3}" maxlength="3"></label><label>Desde<input v-model="filtros.desde" type="date" min="2000-01-01" max="2100-12-31"></label><label>Hasta<input v-model="filtros.hasta" type="date" min="2000-01-01" max="2100-12-31"></label><button type="button" class="secondary" @click="esteMes">Este mes</button><button type="button" class="secondary" @click="filtros.desde='';filtros.hasta='';aplicar()">Todas las fechas</button></template>
    <button>Aplicar filtros</button><button type="button" class="secondary" @click="navegar({},false)">Limpiar filtros</button>
  </fieldset></form></details>
  <div v-if="route.query.habitacionId" class="view-notice">Historial: Hab. {{ habitaciones[route.query.habitacionId] || 'No disponible' }}<button class="secondary" :disabled="ocupado" @click="navegar({habitacionId:'',pagina:0})">Quitar filtro</button></div>
  <section v-if="modulo==='reservas'" class="vue-calendar"><div class="vue-toolbar"><button class="secondary" aria-label="Seis días anteriores" @click="moverSemana(-6)">‹</button><strong>{{ semana.slice(0,7) }}</strong><button class="secondary" aria-label="Seis días siguientes" @click="moverSemana(6)">›</button></div><div class="vue-days"><button v-for="d in dias" :key="d.fecha" :class="{active:filtros.fecha===d.fecha}" :aria-pressed="filtros.fecha===d.fecha" :disabled="ocupado" @click="navegar({fecha:d.fecha,pagina:0})"><small>{{ d.nombre }}</small><strong>{{ d.numero }}</strong></button></div></section>
  <div v-if="['reservas','limpieza'].includes(modulo)" class="vue-toolbar"><button class="secondary" :disabled="ocupado" @click="navegar({fecha:fechaLocal(),pagina:0})">Hoy</button><button class="secondary" :disabled="ocupado" @click="navegar({fecha:'',pagina:0})">Todas las fechas</button><small>{{ filtros.fecha || 'Todas las fechas' }}</small></div>
  <template v-if="modulo==='finanzas'">
    <div v-if="resumen" class="vue-balance"><small>Balance general estimado</small><h2>{{ dinero(resumen.balance,resumen.moneda) }}</h2><div class="vue-toolbar"><span class="income">Ingresos: {{ dinero(resumen.ingresos,resumen.moneda) }}</span><span class="expense">Gastos: {{ dinero(resumen.egresos,resumen.moneda) }}</span></div><small>{{ resumen.desde || 'Inicio' }} → {{ resumen.hasta || 'Actualidad' }} · No incluye anulados.</small></div>
    <div class="vue-tabs"><button :class="{active:filtros.tipo==='INGRESO'}" :aria-pressed="filtros.tipo==='INGRESO'" :disabled="ocupado" @click="navegar({tipo:'INGRESO',pagina:0})">Ingresos</button><button :class="{active:filtros.tipo==='EGRESO'}" :aria-pressed="filtros.tipo==='EGRESO'" :disabled="ocupado" @click="navegar({tipo:'EGRESO',pagina:0})">Gastos</button></div>
    <section v-if="meses.length"><h2 class="vue-subtitle">Análisis mensual · Ingresos / gastos</h2><div class="vue-chart card"><div v-for="m in meses" :key="m.mes" class="vue-chart-column" :title="`${m.mes}: ingresos ${dinero(m.ingresos,montoMoneda)}, gastos ${dinero(m.egresos,montoMoneda)}`"><div class="vue-bars" aria-hidden="true"><i class="income-bar" :style="{height:`${Number(m.ingresos)/maxGrafico*100}%`}"></i><i class="expense-bar" :style="{height:`${Number(m.egresos)/maxGrafico*100}%`}"></i></div><small>{{ m.etiqueta }}</small></div></div><details class="view-notice"><summary>Ver cifras del gráfico</summary><p v-for="m in meses" :key="m.mes">{{ m.mes }} · Ingresos {{ dinero(m.ingresos,montoMoneda) }} · Gastos {{ dinero(m.egresos,montoMoneda) }}</p></details></section>
  </template>
  <p role="status" class="view-notice">{{ cargando ? 'Cargando datos…' : mensaje }}</p><button v-if="mensaje && !cargando" class="secondary" :disabled="ocupado" @click="cargar">Actualizar</button>
  <p v-if="!cargando && !items.length" class="vue-empty">No hay registros para este filtro.</p>
  <article v-for="item in items" :key="item.id" class="card vue-record">
    <div class="vue-record-title"><h2>{{ modulo==='habitaciones'?'Hab. '+item.numero:modulo==='reservas'?item.huesped:modulo==='limpieza'?'Hab. '+numero(item):item.concepto }}</h2><span class="vue-status" :data-estado="item.estado">{{ etiquetas[item.estado] }}</span></div>
    <template v-if="modulo==='habitaciones'"><p>{{ item.tipo }} · Capacidad: {{ item.capacidad }}</p><strong class="vue-price">{{ dinero(item.precioNoche) }}<small> / noche</small></strong></template>
    <template v-if="modulo==='reservas'"><p>Hab. {{ numero(item) }} · {{ item.personas }} personas</p><p>{{ item.fechaEntrada }} → {{ item.fechaSalida }}</p><strong>{{ dinero(item.total) }}</strong></template>
    <template v-if="modulo==='limpieza'"><p>{{ item.descripcion }}</p><p>{{ item.responsable || 'Sin responsable asignado' }}</p><small :class="{expense:vencida(item)}">{{ item.fechaProgramada }}{{ vencida(item)?' · Atrasada':'' }}</small></template>
    <template v-if="modulo==='finanzas'"><strong :class="item.tipo==='INGRESO'?'income':'expense'">{{ dinero(item.importe,item.moneda) }}</strong><p>{{ item.fecha }} · {{ item.metodoPago }}</p><p v-if="item.anulacion">Motivo: {{ item.anulacion.motivo }}</p></template>
    <details><summary>Detalles / acciones</summary><div class="vue-actions">
      <template v-if="modulo==='habitaciones'"><RouterLink class="vue-button" :to="`/habitaciones/editar/${item.id}`">Editar</RouterLink><button :disabled="ocupado" @click="ejecutar(item,'eliminar')">Eliminar</button><RouterLink class="vue-button" :to="{path:'/reservas/nuevo',query:{habitacionId:item.id}}">Crear reserva</RouterLink><RouterLink class="vue-button" :to="{path:'/limpieza/nuevo',query:{habitacionId:item.id}}">Programar limpieza</RouterLink><RouterLink class="vue-button secondary" :to="{path:'/reservas',query:{habitacionId:item.id}}">Ver reservas</RouterLink><RouterLink class="vue-button secondary" :to="{path:'/limpieza',query:{habitacionId:item.id}}">Ver limpieza</RouterLink></template>
      <template v-if="modulo==='reservas' && item.estado==='CONFIRMADA'"><button :disabled="ocupado" @click="ejecutar(item,'eliminar')">Cancelar reserva</button><button :disabled="ocupado || item.fechaSalida>fechaLocal()" @click="ejecutar(item,'estado','FINALIZADA')">Finalizar estancia</button></template>
      <template v-if="modulo==='limpieza' && ['PENDIENTE','EN_PROCESO'].includes(item.estado)"><button v-if="item.estado==='PENDIENTE'" :disabled="ocupado" @click="ejecutar(item,'estado','EN_PROCESO')">Iniciar</button><button :disabled="ocupado" @click="ejecutar(item,'estado','COMPLETADA')">Completar</button><button :disabled="ocupado" @click="ejecutar(item,'eliminar')">Cancelar tarea</button></template>
      <template v-if="modulo==='finanzas' && item.estado==='REGISTRADO'"><button :disabled="ocupado || Boolean(accion.id)" @click="accion.id=item.id">Anular movimiento</button><form v-if="accion.id===item.id" class="vue-fields" @submit.prevent="anular"><label>Motivo<textarea v-model="accion.motivo" required maxlength="300" :disabled="ocupado"></textarea></label><button :disabled="ocupado">Confirmar anulación</button><button type="button" class="secondary" :disabled="ocupado" @click="cerrarAnulacion">Volver</button><small>Esto registra una anulación; no realiza devoluciones bancarias.</small></form></template>
    </div></details>
  </article>
  <div class="vue-pagination"><button :disabled="cargando || ocupado || pagina===0" @click="navegar({pagina:pagina-1})">Anterior</button><small>Página {{ pagina+1 }} · {{ total }} registros</small><button :disabled="cargando || ocupado || (pagina+1)*20>=total" @click="navegar({pagina:pagina+1})">Siguiente</button></div>
 </section>
</template>

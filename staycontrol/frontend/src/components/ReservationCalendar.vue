<script setup>
import { computed, ref, watch } from 'vue';
import { fechaLocal } from '../lifecycle.js';

const props = defineProps({ value: { type: String, default: '' }, disabled: Boolean });
const emit = defineEmits(['select']);
const month = ref(fechaLocal().slice(0, 7));
const weekdays = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const parse = value => new Date(`${value}T12:00:00`);
const title = computed(() => parse(`${month.value}-01`).toLocaleDateString('es-CO', { month: 'long', year: 'numeric' }));
const selection = computed(() => props.value ? parse(props.value).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }) : 'Todas las fechas');
const days = computed(() => {
  const first = parse(`${month.value}-01`);
  const offset = (first.getDay() + 6) % 7;
  const count = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  return Array.from({ length: Math.ceil((offset + count) / 7) * 7 }, (_, i) => {
    const number = i - offset + 1;
    if (number < 1 || number > count) return null;
    const date = fechaLocal(new Date(first.getFullYear(), first.getMonth(), number));
    return { date, number, today: date === fechaLocal(), label: parse(date).toLocaleDateString('es-CO', { dateStyle: 'full' }) };
  });
});
watch(() => props.value, value => { if (/^\d{4}-\d{2}-\d{2}$/.test(value)) month.value = value.slice(0, 7); }, { immediate: true });
function move(amount) {
  const date = parse(`${month.value}-01`);
  date.setMonth(date.getMonth() + amount);
  month.value = fechaLocal(date).slice(0, 7);
}
function today() {
  month.value = fechaLocal().slice(0, 7);
  emit('select', fechaLocal());
}
</script>

<template>
  <section class="reservation-calendar" aria-label="Calendario de reservas">
    <header class="calendar-heading">
      <div><span class="calendar-caption">Fecha de estancia</span><h2 aria-live="polite">{{ title }}</h2></div>
      <div class="calendar-arrows">
        <button type="button" aria-label="Mes anterior" :disabled="disabled || month <= '2000-01'" @click="move(-1)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m14 6-6 6 6 6"/></svg></button>
        <button type="button" aria-label="Mes siguiente" :disabled="disabled || month >= '2100-12'" @click="move(1)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m10 6 6 6-6 6"/></svg></button>
      </div>
    </header>
    <div class="calendar-weekdays" aria-hidden="true"><span v-for="day in weekdays" :key="day">{{ day }}</span></div>
    <div :key="month" class="calendar-month">
      <template v-for="(day, index) in days" :key="day?.date || index">
        <button v-if="day" type="button" :class="{ selected: value === day.date, today: day.today }" :aria-label="day.label" :aria-pressed="value === day.date" :aria-current="day.today ? 'date' : undefined" :disabled="disabled" @click="emit('select', day.date)"><span>{{ day.number }}</span></button>
        <span v-else aria-hidden="true"></span>
      </template>
    </div>
    <footer class="calendar-footer">
      <button type="button" :disabled="disabled" @click="today">Hoy</button>
      <button type="button" :class="{ 'all-selected': !value }" :aria-pressed="!value" :disabled="disabled" @click="emit('select', '')">Todas las fechas</button>
    </footer>
    <p class="calendar-selection" role="status">{{ selection }}</p>
  </section>
</template>

<style scoped>
.reservation-calendar { background: white; border: 1px solid var(--border); border-radius: 22px; padding: 16px 10px 12px; box-shadow: 0 5px 18px #0f172a05; }
.calendar-heading { display: flex; justify-content: space-between; align-items: center; gap: 4px; padding: 0 6px 16px; }
.calendar-caption { font-size: 14px; color: var(--text); }
.calendar-heading h2 { margin-top: 4px; color: var(--dark); font-size: 20px; font-weight: 600; text-transform: capitalize; }
.calendar-arrows { display: flex; flex-shrink: 0; }
.reservation-calendar .calendar-arrows button { width: 44px; min-height: 44px; padding: 0; border-radius: 50%; background: transparent; color: var(--blue); }
.calendar-arrows svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.calendar-weekdays, .calendar-month { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); text-align: center; }
.calendar-weekdays { padding-bottom: 6px; color: var(--text); font-size: 13px; }
.reservation-calendar .calendar-month button { position: relative; min-width: 0; min-height: 48px; padding: 0; border: 0; background: transparent; color: var(--dark); font-size: 17px; font-weight: 500; border-radius: 50%; }
.calendar-month button span { display: grid; place-items: center; width: min(38px, 100%); height: 38px; border-radius: 50%; transition: background 160ms, transform 160ms; }
.calendar-month button.today span { box-shadow: inset 0 0 0 1px var(--blue); color: var(--blue); }
.calendar-month button.selected span { background: var(--blue); color: white; font-weight: 700; box-shadow: 0 3px 9px #2563eb30; }
.calendar-month button:active span { transform: scale(.9); }
.calendar-footer { display: flex; justify-content: space-between; border-top: 1px solid var(--border); margin-top: 10px; padding-top: 8px; gap: 8px; }
.reservation-calendar .calendar-footer button { background: transparent; color: var(--blue); min-height: 44px; border-radius: 22px; }
.reservation-calendar .calendar-footer .all-selected { background: var(--background); }
.calendar-selection { color: var(--text); text-align: center; font-size: 13px; line-height: 1.5; margin-top: 4px; }
.reservation-calendar button:focus-visible { outline: 2px solid var(--blue); outline-offset: 1px; }
.reservation-calendar button:disabled { opacity: .45; cursor: default; }
.calendar-month { animation: calendar-appear 160ms ease-out; }
@keyframes calendar-appear { from { opacity: .4; } to { opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .calendar-month { animation: none; } .calendar-month button span { transition: none; } }
</style>

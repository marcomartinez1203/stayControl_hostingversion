<script setup>
defineProps({ nombre: { type: String, required: true } });
</script>
<template><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><use :href="`/assets/icons/sprite.svg#${nombre}`" /></svg></template>

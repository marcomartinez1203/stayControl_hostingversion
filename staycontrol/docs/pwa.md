# StayControl como PWA

La instalación web no cambia Spring Boot, MongoDB ni los contratos de las APIs.
El frontend usa Vue 3 y Vue Router: todas las pantallas comparten una SPA.
No requiere Android SDK ni APK. Consulta [compilación y desarrollo](vue-pwa.md).

## Instalar

1. Compila con `npm run build` desde `frontend`, inicia Spring Boot y abre
   `http://localhost:8080/index.html#/login`.
2. Abre **Aplicación** y pulsa **Instalar StayControl** si el navegador lo ofrece.
   También puedes usar la instalación del menú del navegador.
3. En iPhone/iPad, utiliza **Compartir → Añadir a pantalla de inicio**.

El botón solo se muestra cuando el navegador emite `beforeinstallprompt`;
no aparecerá en todos los navegadores ni necesariamente en la primera visita.
La aplicación instalada usa el mismo servidor: instalarla no instala Java ni MongoDB.
Para usarla desde otro dispositivo hace falta un servidor accesible por HTTPS;
`localhost` en el teléfono apunta al teléfono, no al PC. No expongas MongoDB a la red.

## Sin conexión y privacidad

- Tras una primera visita conectada y el registro del service worker, una navegación
  que no alcanza el servidor muestra una página offline con opción de reintentar.
- Solo se cachean esa página y una lista cerrada de recursos públicos (fuente,
  CSS, controlador PWA e iconos). No se cachean vistas de negocio ni respuestas API.
- El indicador `navigator.onLine` es orientativo; tener red no asegura que Spring
  Boot o MongoDB estén disponibles. Los errores reales los informa la API.
- No hay login offline, datos de hoteles persistidos en Cache Storage, colas de
  operaciones ni sincronización en segundo plano. Las escrituras necesitan red.
- El contenido ya cargado en una pestaña puede seguir visible al perder conexión;
  no equivale a una copia offline persistente.

## Archivos y actualizaciones

- `frontend/manifest.json`: identidad, inicio, alcance e iconos (servido como `application/json`).
- `frontend/sw.js`: worker en la raíz, alcance `/` y estrategia de caché.
- `frontend/src/components/PwaControls.vue`: registro, instalación y avisos en Vue.
- `frontend/controllers/pwaController.js`: reintento y avisos en la página offline.
- `frontend/offline.html` y `assets/css/pwa.css`: estado sin conexión.
- `scripts/Generar-IconosPwa.ps1`: reproduce los PNG a partir de la llave del login.

Vite copia los recursos públicos a `dist` y Maven incluye `dist` en el JAR.
El worker solo se registra en producción, no con `npm run dev`.
El worker se publica en `/sw.js`, no bajo
`/controllers/`, para controlar todas las vistas sin ampliar artificialmente su alcance.
Al publicar cambios en los recursos de la caché, incrementa `VERSION` en `sw.js`.
No hay `skipWaiting` ni recargas automáticas: guarda los formularios y cierra todas
las pestañas/ventanas de StayControl para que una versión en espera se active.
La activación elimina únicamente cachés antiguas con el prefijo de StayControl.

Comprobaciones realizadas: sintaxis JavaScript, empaquetado Maven sin pruebas,
inicio con MongoDB local y disponibilidad HTTP de manifest, worker, iconos y vistas.
Pendiente: verificar instalación, actualización y desconexión en los navegadores
de destino. No se crean cuentas ni datos de prueba ni se instala en el dispositivo
del usuario automáticamente.

Referencias: [instalación PWA](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable),
[service workers](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API/Using_Service_Workers).

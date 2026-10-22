# Vue 3 + PWA

El frontend activo es `frontend/src/`. Utiliza componentes `.vue`, Composition API,
Vue Router 4 y Vite. El backend, MongoDB, las sesiones, CSRF, las claves de
idempotencia y el aislamiento por hotel conservan sus contratos.

## Desarrollo

Primero inicia Spring Boot en 8080. Desde `frontend`:

```powershell
npm ci
npm run dev
```

Abre la dirección que indique Vite (normalmente `http://127.0.0.1:5173`).
El proxy `/api` comunica Vite con Spring Boot: el navegador usa un solo origen
y no necesita CORS abierto. No se incluyen credenciales ni archivos `.env` del
backend en el bundle. No pongas secretos en variables `VITE_*`.

## Usar con Spring Boot Dashboard / empaquetar

```powershell
# Desde frontend
npm run build
# Desde la carpeta del pom.xml del proyecto activo
.\mvnw.cmd -B -o package '-Dmaven.test.skip=true'
```

Reinicia Spring Boot después de cambiar su configuración de recursos estáticos.
Abre `http://localhost:8080/index.html#/login`. Spring sirve `frontend/dist/`
o el frontend empaquetado dentro del JAR. `dist`, `.public` y `node_modules` son
generados e ignorados por Git; `package-lock.json` sí se versiona.
Antes de una publicación limpia usa `clean package` en Maven, después de Vite.

## Rutas y componentes

- `AuthView.vue`: login, registro de usuario/hotel y aviso de recuperación aplazada.
- `DashboardView.vue`: métricas del servidor, próximas reservas y moneda.
- `ListView.vue`: listados por módulo, filtros, calendario, estados, anulación,
  balance, gráfico y exportación del resumen financiero.
- `EditorView.vue`: creación en los cuatro módulos y edición de habitaciones.
- `RoomSelector.vue`: búsqueda/paginación con selección conservada.
- `App.vue`: cuenta, hotel, salida, cabecera y navegación compartidas.
- `PwaControls.vue`: conectividad, instalación y aviso de actualización.

Ejemplos: `#/habitaciones`, `#/habitaciones/nuevo`, `#/habitaciones/editar/<id>`,
`#/reservas/nuevo?habitacionId=<id>`. Los IDs se usan internamente, no se imprimen
en las tarjetas. Las rutas usan hash para recargar/enlazar sin necesitar un
controlador Java de fallback. Las rutas anteriores `/views/*.html` redirigen
a Vue; los HTML originales permanecen en el repositorio, sin ser el frontend activo.

Las lecturas pendientes se cancelan al cambiar de vista/filtro. Las escrituras
no se abortan al navegar: se bloquea la salida mientras se procesa una operación.
Los borradores avisan antes de salir. Un POST conserva la clave al reintentar el
mismo payload en el formulario; recargar/descartar/editar inicia otra operación.
Tras un error de red reintenta sin editar antes de salir: no hay cola offline.
Los importes escritos se envían como texto decimal; Java valida sus límites.

La página offline sigue siendo informativa y no persiste datos de hoteles.
Instalar en un teléfono requiere un servidor alcanzable por HTTPS; usar Vue no
soluciona aislamiento Wi-Fi, direcciones IP incorrectas ni certificados.
La recuperación por correo sigue aplazada y no se añadió plataforma nativa.

## Verificación de esta migración

Se compila Vite y el JAR sin ejecutar pruebas automatizadas ni crear registros.
La revisión HTTP no sustituye comprobar flujos autenticados, instalación y modo
offline en el navegador/teléfono de destino. Las pruebas anteriores en
`frontend/tests` corresponden al frontend anterior, no a los componentes Vue.

Referencias: [ciclo de vida Vue](https://vuejs.org/guide/essentials/lifecycle.html),
[compilación Vite](https://vite.dev/guide/build).

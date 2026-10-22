# StayControl

Aplicación de gestión hotelera con frontend Vue 3 + Vue Router + Vite (PWA) y backend Java 21
con Spring Boot y MongoDB. El proyecto activo es esta carpeta, no el `pom.xml`
del directorio superior.

## Funcionalidad implementada

- Registro con nombre del hotel, login, logout y edición del nombre del hotel.
- Cada cuenta administra su propio hotel con colecciones de negocio aisladas.
- Habitaciones: búsqueda, estados, edición, baja lógica e historial por habitación.
- Todas las pantallas usan [Vue 3 y navegación SPA](docs/vue-pwa.md): acceso,
  registro, dashboard, habitaciones, reservas, limpieza y finanzas sin recargar.
- Reservas: búsqueda por huésped, calendario, consulta de disponibilidad/cotización,
  creación idempotente, cancelación y finalización.
- Limpieza: agenda por fecha/estado, tareas vencidas, creación, inicio, finalización
  y cancelación.
- Finanzas: movimientos manuales, filtros por moneda/fecha/estado, anulación
  con motivo, balance, gráfica y descarga JSON.
- Dashboard: métricas reales de habitaciones, reservas, limpieza y finanzas.
- Protección de formularios pendientes y reintentos con la misma clave de creación.
- [PWA instalable](docs/pwa.md), iconos locales y pantalla segura sin conexión.

Se conserva el diseño original y los iconos SVG locales. No se ha añadido Android,
iOS ni un empaquetador nativo. El frontend necesita servirse por HTTP/HTTPS desde
Spring Boot; no funciona abriendo directamente el HTML como archivo.

## Estructura

```text
frontend/
  src/views/      Pantallas Vue (acceso, dashboard, listados y formularios)
  src/components/ Iconos, selector de habitaciones y controles PWA
  src/router.js   Rutas SPA y protección de sesión
  src/lifecycle.js Borradores, bloqueo de envíos y cancelación de lecturas
  dist/           Resultado generado por npm run build (no editar)
  views/          HTML anterior conservado como referencia; ya no se sirve
  controllers/    Código anterior; solo pwaController se usa en la pantalla offline
  services/       Cliente HTTP, sesión, CSRF y APIs
  assets/         CSS e iconos
  models/         Modelos anteriores; no sustituyen los datos del servidor
src/main/java/com/example/staycontrol/
  controller/api/ Endpoints REST
  controller/     Rutas de las vistas
  service/        Validación y reglas de negocio
  repository/     Persistencia
  model/          Documentos de MongoDB
  dto/            Contratos JSON
  config/         Seguridad, MongoDB, aislamiento y transacciones
docs/             Contratos y límites de la implementación
```

## Ejecutar

1. Configura `.env` en el directorio superior o en esta carpeta, tomando como guía
   `.env.example`. No publiques contraseñas. `MONGODB_URI` contiene la URI local o de Atlas;
   `MONGODB_DATABASE` el nombre de la base.
2. Para MongoDB local, configura primero [el replica set local](docs/mongodb-local.md).
   Si utilizas Atlas, comprueba los permisos del usuario y la IP autorizada.
   Reservas y limpieza requieren un replica set compatible con transacciones.
3. Compila Vue e inicia Spring desde esta carpeta (Node 22.12+ o Node 24):

```powershell
Push-Location frontend
npm ci
npm run build
Pop-Location
.\mvnw.cmd spring-boot:run
```

Abre `http://localhost:8080/registro`, crea tu cuenta y entra por `/login`.
Los nuevos usuarios no necesitan ser elevados a ADMIN para gestionar su hotel.
No hay credenciales predeterminadas. El aprovisionamiento `AUTH_BOOTSTRAP_*`
es opcional para instalaciones administradas, no un requisito del registro.

También puedes iniciar `StaycontrolApplication` con Spring Boot Dashboard después
de compilar Vue. Entrada directa: `http://localhost:8080/index.html#/login`.
Para editar con recarga rápida, consulta [desarrollo Vue](docs/vue-pwa.md).

## Datos de la versión anterior

Lee [multi-hotel](docs/multi-hotel.md) antes de usar datos existentes.
`LEGACY_HOTEL_OWNER_EMAIL` asigna el acceso a las colecciones históricas a una
sola cuenta existente y confirmada. Vacío significa que esos datos se conservan,
pero no se muestran a ninguna cuenta. No se borran ni se copian automáticamente.

El nombre del hotel es descriptivo; no decide la propiedad de los datos. Cada
cuenta tiene un hotel independiente. Aún no hay invitaciones de empleados.

## Compilar y empaquetar sin ejecutar pruebas

```powershell
Push-Location frontend
npm run build
Pop-Location
.\mvnw.cmd -B -o package '-Dmaven.test.skip=true'
java -jar target/staycontrol-0.0.1-SNAPSHOT.jar
```

`-o` usa las dependencias ya descargadas. En una instalación nueva, quítalo.
Maven incluye el frontend dentro del JAR. Durante desarrollo, Spring también
lee `frontend/dist/` desde el directorio de ejecución; no edites las copias en `target/`.
Ejecuta `npm run build` después de modificar componentes y antes de empaquetar.
En una compilación de publicación usa `clean package` para no incluir recursos
generados de versiones anteriores.

## Vistas y APIs

| Módulo | Vista | Base de API |
| --- | --- | --- |
| Acceso | `/login`, `/registro` | `/api/v1/auth` |
| Dashboard | `/dashboard` | `/api/v1/dashboard` |
| Habitaciones | `/habitaciones` | `/api/v1/habitaciones` |
| Reservas | `/reservas` | `/api/v1/reservas` |
| Limpieza | `/limpieza` | `/api/v1/limpieza` |
| Finanzas | `/finanzas` | `/api/v1/finanzas` |

Las APIs requieren sesión; las escrituras también CSRF. Los POST de creación
requieren `Idempotency-Key`. Los errores no se sustituyen por datos ficticios.
Consulta [integración del frontend](docs/frontend-integracion.md) y los documentos
`docs/api-*.md` para contratos, transiciones y ejemplos.

## Límites y publicación

- Recuperación de contraseña aplazada expresamente; su vista no envía correos.
- Sin verificación de correo, MFA, cobros bancarios ni facturación fiscal.
- Los estados físicos de habitaciones se administran manualmente. Reservas y
  limpieza no cambian automáticamente esos estados; finanzas no registra cobros
  automáticamente a partir de una reserva.
- Las sesiones se pierden al reiniciar. El limitador de login es local por instancia.
- El servidor escucha en `127.0.0.1` por defecto. Para publicar hacen falta hosting,
  HTTPS, cookies Secure, control de red y copias de seguridad. No habilites CORS abierto.
- La PWA se instala desde navegadores compatibles; no incluye APK ni publicación en tiendas.
- El modo sin conexión muestra una pantalla informativa: no guarda datos del hotel ni encola operaciones.

No confundir compilación o rutas HTTP correctas con validación integral: no se
han ejecutado pruebas automatizadas ni escrituras de prueba en Atlas en esta etapa.
Antes de producción falta comprobar los flujos con dos cuentas y un entorno de
pruebas autorizado, además de confirmar al propietario de los datos históricos.

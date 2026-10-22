# Integración frontend y backend

> El frontend activo fue migrado a Vue 3. Consulta [Vue/PWA](vue-pwa.md) para las
> rutas y componentes actuales. Este documento conserva detalles de la integración
> anterior: los HTML y controladores indicados abajo ya no son la interfaz activa.
> El selector Vue usa número/tipo, no un campo de ID manual; los permisos y la
> disponibilidad definitivos siguen validándose en el backend.

Abrir las rutas desde Spring Boot en `http://localhost:8080`, no como archivos locales.

- Login: correo y contraseña reales, mensajes de error y envío con Enter. No hay credenciales precargadas.
- Registro: crea una cuenta con su hotel independiente; permite administrarlo tras
  iniciar sesión, sin aprobación ADMIN. No inicia sesión automáticamente.
- Habitaciones: listado paginado, búsqueda por número/tipo y filtro de estado
  (incluido mantenimiento), creación, edición y eliminación lógica.
  En «Detalles / acciones», «Crear reserva» y «Programar limpieza» abren la vista
  correspondiente con esa habitación seleccionada. Se puede cambiar la selección
  antes de guardar. Abrir estos accesos no crea registros ni modifica estados.
  «Crear reserva» está deshabilitado en mantenimiento; la vista de destino vuelve
  a consultar la habitación y no abre el formulario si ya no existe o no hay permisos.
  «Ver reservas» y «Ver limpieza» abren el historial filtrado por esa habitación,
  sin formulario de creación. Se combinan con fecha, estado y búsqueda por huésped;
  el servidor filtra antes de paginar. «Quitar filtro de habitación» mantiene
  los demás filtros aplicados y regresa a la primera página.
- Reservas: selector paginado de habitaciones, creación, búsqueda por huésped,
  listado, cancelación y finalización. La búsqueda se combina con fecha/estado
  y se procesa en el servidor antes de paginar.
  El formulario consulta disponibilidad y cotización antes de guardar, sin crear
  registros ni bloquear fechas; el servidor vuelve a validar al confirmar la reserva.
- Calendario de reservas: selección de día, bloques anteriores/siguientes, «Hoy»,
  selector de fecha y «Todas». Filtra estancias en el servidor antes de paginar,
  combinando fecha con estado. Incluye entrada y excluye salida; «Hoy» usa la fecha
  del dispositivo. No es un registro de check-in/check-out.
- Limpieza: creación por habitación, listado filtrado por fecha/estado, accesos
  «Hoy»/«Todas las fechas», inicio, finalización y cancelación. Las tareas abiertas
  anteriores al día del dispositivo se señalan como vencidas sin cambiar su estado.
- Los formularios de Reservas y Limpieza permiten buscar habitaciones por número
  o tipo, con resultados paginados y capacidad visible. La selección se conserva
  al cambiar de página o búsqueda; también sigue disponible introducir el ID.
  Enter en el buscador solo busca, no guarda el formulario. Buscar no cambia la
  clave de idempotencia; elegir otra habitación sí invalida la clave y la cotización.
  El selector muestra habitaciones existentes, no garantiza disponibilidad por
  fechas: esa comprobación se realiza en Reservas y al guardar en el servidor.
- Finanzas: ingresos/egresos manuales, filtros por moneda/estado y fechas inclusivas,
  accesos «Este mes»/«Todas las fechas», anulación con motivo y balance del período.
  La descarga JSON conserva el resumen mostrado. La gráfica abarca como máximo
  seis meses dentro del período; el balance incluye todo el rango y excluye anulados.
- Dashboard: ocupación actual, reservas del día, limpieza y finanzas del mes completo.
- Todas las vistas de negocio incluyen navegación y cierre de sesión.

Los accesos desde Habitaciones usan `/views/reservas.html` o
`/views/limpieza.html` con `crear=1&habitacionId=<id>`. Solo aceptan IDs con el
formato de la API y consultan los datos actuales, sin confiar en nombres o estados
de la URL. Tras abrir el formulario se retiran esos parámetros para no reabrirlo
al recargar. Si ya se abrió otro formulario mientras cargaba, no se reemplaza.

Para consultar el historial se usa solo `habitacionId=<id>`, sin `crear=1`.
Este parámetro permanece al recargar y no cambia al crear/cancelar/finalizar
registros. Un ID inválido bloquea la consulta hasta quitar el filtro; no se
muestran silenciosamente registros de otras habitaciones. El historial de una
habitación eliminada sigue siendo consultable por ID aunque ya no se pueda
resolver su número. Abrir el historial no crea registros ni cambia estados.
- «Cuenta» permite editar el nombre del hotel y actualizar el encabezado del dashboard
  tras guardarlo. No requiere crear otra cuenta ni cambia roles.

El backend exige sesión y limita cada cuenta a su hotel (ver `multi-hotel.md`).
No concede acceso a datos de otros hoteles, independientemente del rol histórico.
Una respuesta 401 redirige al login; 403 indica rechazo de la solicitud; otros errores no se
sustituyen por datos de ejemplo. El servidor valida las reglas definitivas:
solapamientos, bloqueos de habitaciones, transiciones irreversibles y fechas.

Los POST conservan la clave de idempotencia durante el reintento del formulario
sin editar. Al editar, descartar o recargar se inicia otra operación. Ante errores
de red, reintentar el mismo formulario antes de cerrarlo; no hay cola offline.
PUT/DELETE usan las operaciones idempotentes del backend. Los importes de entrada
viajan como texto decimal para no redondearlos antes de llegar a Java.

Los formularios de negocio y «Cuenta» avisan antes de descartar cambios o salir
de la página. Si una escritura falla, el aviso se mantiene porque su resultado
podría ser incierto. Durante una operación se bloquea la navegación interna.
Buscar habitaciones no marca cambios en los datos que se guardarán. Los borradores
solo existen en memoria; el aviso nativo al cerrar/recargar depende del navegador
y no garantiza recuperación tras cerrar la aplicación o perder energía.

Los estados de habitación se administran manualmente: una reserva o tarea no los
cambia automáticamente. Finanzas no procesa pagos ni contabiliza reservas de
forma automática. No hay verificación del correo ni recuperación de contraseña;
esta última se dejó pendiente expresamente.

Verificación de esta integración: sintaxis JavaScript, compilación Java y acceso
HTTP de lectura. No se creó información de negocio en Atlas. El recorrido completo
con escrituras requiere una sesión válida y datos de prueba autorizados.

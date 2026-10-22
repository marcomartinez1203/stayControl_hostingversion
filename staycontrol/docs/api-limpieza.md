# API de limpieza

Base `/api/v1/limpieza`. Requiere sesión autenticada del hotel y CSRF en escrituras.
La vista consume esta API para crear, listar y cambiar el estado de las tareas.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/` | Listado paginado |
| GET | `/{id}` | Consulta individual; `404` si no existe |
| POST | `/` | Creación `201` con `Idempotency-Key` obligatoria |
| PUT | `/{id}/estado` | Cambio de estado `200` |
| DELETE | `/{id}` | Cancelación lógica `204`; conserva historial |

Listado: filtros opcionales `estado`, `habitacionId`, `fecha=2026-12-10`.
`pagina`: 0–10000; `tamano`: 1–100, por defecto 20. Respuesta
`{items, pagina, tamano, total}`, ordenada por fecha e id. Página y total son
lecturas separadas, no una instantánea conjunta.

La vista permite aplicar fecha programada y estado desde «Gestionar / actualizar».
«Hoy» usa la fecha local del dispositivo; «Todas las fechas» elimina solo el filtro
de fecha. Al paginar o completar una tarea se conservan los filtros aplicados,
sin tomar cambios pendientes de pulsar «Actualizar». El encabezado muestra el
período elegido; inicialmente se consultan todas las fechas.

Las tareas PENDIENTE/EN_PROCESO anteriores a hoy muestran «Vencida», usando el
estilo de prioridad existente. Es un aviso visual, no un nuevo estado ni un cambio
automático en MongoDB. El formulario de creación propone el día filtrado o, sin
filtro, el día actual; puede cambiarse antes de guardar.

## Crear

```json
{
  "habitacionId": "<id de 64 caracteres devuelto por habitaciones>",
  "descripcion": "Limpieza general y cambio de sábanas",
  "responsable": "Ana Pérez",
  "fechaProgramada": "2026-12-10"
}
```

Habitación existente y no eliminada; descripción de 1–500 caracteres;
responsable opcional de hasta 120 caracteres. Se recortan espacios exteriores
y se rechazan controles. Responsable vacío equivale a `null`: es texto, no un
usuario ni una asignación de permisos. Fecha ISO entre 2000 y 2100; se permiten
tareas históricas y futuras y se almacena el día civil sin zona horaria.

Respuesta: `id`, los campos anteriores y estado inicial PENDIENTE.
POST incluye `Location` e `Idempotency-Replayed`. Misma clave y datos normalizados:
misma respuesta `201`, con replay true. Misma clave y datos diferentes: `409`.
Ámbito `POST:/api/v1/limpieza`, separado de los demás módulos; sin caducidad.
Un replay tras cerrar la tarea devuelve la creación PENDIENTE, pero no la reabre.
GET muestra el estado actual. Claves distintas permiten varias tareas por habitación/día.

## Estados

PUT `/{id}/estado` recibe `{"estado":"COMPLETADA"}`:

- PENDIENTE → EN_PROCESO, COMPLETADA o CANCELADA.
- EN_PROCESO → COMPLETADA o CANCELADA; no vuelve a PENDIENTE.
- Repetir el estado actual devuelve `200`, sin cambios adicionales.
- COMPLETADA/CANCELADA son terminales; otro estado da `409 TAREA_CERRADA`.

DELETE equivale a CANCELADA. Repetirlo sobre una tarea cancelada da `204`;
una completada da `409` y una inexistente `404`. No exige Idempotency-Key.
No hay edición de datos ni reapertura: para corregir, cancelar y crear con otra clave.

## Integridad y límites

La creación inserta la tarea e incrementa `tareasLimpiezaActivas` de la habitación
en una transacción. Completar/cancelar cambia el estado y descuenta el contador
en otra; los reintentos no descuentan de nuevo. Se comparte el gestor MongoDB
existente, con lectura snapshot y confirmación majority: requiere replica set
o clúster compatible con transacciones.

Una habitación con tareas abiertas no puede eliminarse (`409 HABITACION_CON_LIMPIEZA`).
La condición se comprueba en la escritura de baja y se coordina con la creación
concurrente. Se permite editar sus datos si no lo impiden reservas confirmadas.
No se cambia automáticamente su estado físico ni se bloquean fechas de reservas;
pueden programarse tareas para habitaciones ocupadas o en mantenimiento.

El índice de agenda se prepara antes de la primera creación, fuera de la transacción.
Errores transitorios/commit incierto: `503`, `Retry-After: 1`; reintentar con la misma
clave y cuerpo o el mismo estado deseado. No hay reintentos automáticos.
No modificar manualmente contadores ni borrar documentos si se requiere conservar
la integridad y el historial de idempotencia.

Las casillas del frontend solicitan confirmación y completan la tarea mediante
la API; las tareas cerradas no pueden desmarcarse ni reabrirse.
No incluye notificaciones, reasignación ni gestión de personal. Verificación: solo compilación
(`mvnw.cmd -B -o compile -Dmaven.test.skip=true`), sin crear/ejecutar pruebas ni
realizar operaciones contra Atlas.

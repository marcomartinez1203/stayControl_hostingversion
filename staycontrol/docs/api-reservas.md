# API de reservas

Base: `/api/v1/reservas`. Requiere sesión autenticada del hotel y CSRF para escrituras, igual que
[habitaciones](api-habitaciones.md). La vista de reservas consume datos reales de esta API.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/` | Listado paginado: `items`, `pagina`, `tamano`, `total` |
| GET | `/{id}` | Consulta individual; `404` si no existe |
| GET | `/disponibilidad` | Disponibilidad orientativa y cotización de una habitación |
| POST | `/` | Creación `201`; exige `Idempotency-Key` |
| PUT | `/{id}/estado` | Cancelar o finalizar, `200` |
| DELETE | `/{id}` | Cancelación lógica, `204`; nunca borra el historial |

El listado admite `estado=CONFIRMADA`, `CANCELADA` o `FINALIZADA`, `habitacionId`, `fecha=AAAA-MM-DD`, `buscar`,
`pagina` (0–10000) y `tamano` (1–100, por defecto 20). Se ordena por fecha de entrada
e id. La página y su total son lecturas separadas, no una instantánea conjunta.

`buscar` encuentra parte del nombre del huésped, sin distinguir mayúsculas y
minúsculas; conserva diferencias de acentos. Se recortan espacios exteriores y
se admiten hasta 120 caracteres sin controles. Vacío equivale a no filtrar.
Los caracteres especiales se buscan literalmente, no se interpretan como una
expresión regular proporcionada por el cliente. El filtro se combina con fecha,
estado y habitación; listado y total usan los mismos criterios antes de paginar.

Ejemplo: `GET /api/v1/reservas?buscar=Ana&estado=CONFIRMADA&pagina=0&tamano=20`.
En la vista, abrir «Gestionar / actualizar», escribir en «Buscar por huésped»
y pulsar «Buscar / actualizar» o Enter. Cambiar el estado también aplica los
campos de búsqueda; «Limpiar búsqueda» conserva fecha y estado. Cambiar el día
del calendario o la página conserva el texto ya aplicado, sin tomar lo escrito
posteriormente hasta aplicar de nuevo. Es una consulta sin escrituras ni cambios
en la idempotencia de creación.

`fecha` es opcional, entre 2000 y 2100. Incluye reservas cuya entrada <= fecha y
salida > fecha: noches de estancia, no las salidas de ese día. Sin `estado`, también
incluye canceladas/finalizadas para consultar historial; combina `estado=CONFIRMADA`
para estancias programadas activas. Sin fecha conserva el listado completo anterior.
Formato o rango inválidos: 400. El filtro y el total se calculan en MongoDB antes
de la paginación; no dependen de los registros cargados en la vista.

Ejemplo: `GET /api/v1/reservas?fecha=2026-10-04&estado=CONFIRMADA&pagina=0&tamano=20`.
Es una consulta de solo lectura, sin CSRF ni `Idempotency-Key`; sí requiere sesión autenticada.
La vista permite elegir un día, navegar entre bloques y quitar la fecha con «Todas».
«Hoy» usa la fecha local del dispositivo y no cambia el estado de las reservas.

## Crear

Antes de crear se puede consultar:

`GET /api/v1/reservas/disponibilidad?habitacionId=<id>&personas=2&fechaEntrada=2026-12-10&fechaSalida=2026-12-12`

Requiere sesión autenticada. Parámetros obligatorios, mismas reglas de capacidad y fechas que
la creación. Devuelve `habitacionId`, `numeroHabitacion`, `fechaEntrada`,
`fechaSalida`, `personas`, `noches`, `disponible`, `motivo`, `precioNoche` y `total`.
Mantenimiento, capacidad excedida o cruce de reservas dan `200` con
`disponible: false` y motivo; datos inválidos dan `400`, habitación inexistente
o eliminada da `404`. Los importes los calcula Java con BigDecimal.

La consulta no escribe ni bloquea fechas, no requiere CSRF/Idempotency-Key y
su respuesta no se almacena en caché. Las lecturas pueden cambiar por operaciones
concurrentes: la creación vuelve a validar disponibilidad y tarifa dentro de su
transacción. No comprueba que el estado físico sea DISPONIBLE, igual que la
creación: solo MANTENIMIENTO impide reservar por estado físico.

El formulario ofrece «Consultar disponibilidad y precio» sin exigir aún el
nombre del huésped. Editar los campos invalida el resultado mostrado; respuestas
anteriores no reemplazan una consulta nueva. Consultar no envía el formulario
ni registra movimientos financieros.

```json
{
  "habitacionId": "<id de 64 caracteres devuelto por habitaciones>",
  "huesped": "María Pérez",
  "personas": 2,
  "fechaEntrada": "2026-12-10",
  "fechaSalida": "2026-12-12"
}
```

Todos los campos son obligatorios. Huésped de 1–120 caracteres; personas de 1–20
y no superiores a la capacidad de la habitación. Fechas ISO entre 2000 y 2100,
con duración de 1–365 noches. Se permite ingresar reservas históricas para uso
administrativo. Las fechas se almacenan como días civiles ISO, sin zona horaria.

La habitación debe existir, no estar eliminada ni en mantenimiento. Los intervalos
son `[entrada, salida)`: una salida y una nueva entrada el mismo día son compatibles.
Solo CONFIRMADA bloquea fechas. Una colisión devuelve `409 FECHAS_OCUPADAS`.

El servidor fija `precioNoche` desde la habitación y calcula `total = precioNoche × noches`
con BigDecimal/Decimal128. El cliente no elige importe ni estado inicial. Siempre
se crea CONFIRMADA. Estos importes son una cotización, no un cobro ni un asiento contable.

La respuesta incluye `id`, los campos del cuerpo, `estado`, `precioNoche` y `total`.
POST devuelve `Location` e `Idempotency-Replayed`.

## Idempotencia y concurrencia

La misma clave con los mismos datos normalizados devuelve la creación original,
incluidos precio y estado CONFIRMADA, incluso tras cancelar/finalizar. No restaura
la reserva: usa GET para conocer su estado actual. La misma clave con otros datos
devuelve `409 IDEMPOTENCY_KEY_REUTILIZADA`. El id deriva de la clave y del ámbito
`POST:/api/v1/reservas`; no se guarda la clave en texto claro. El historial no caduca.

La creación usa una transacción MongoDB que escribe en la habitación, comprueba
solapamientos e inserta la reserva. La escritura en el contador `reservasActivas`
de la habitación serializa las operaciones concurrentes, incluidas claves distintas.
Si algo falla, se revierte tanto el contador como la reserva. No se comprueba la
disponibilidad y se inserta mediante dos operaciones independientes.

Requiere MongoDB con transacciones (replica set o clúster compatible, como Atlas).
Se usa lectura snapshot y confirmación majority. El índice de disponibilidad se
prepara fuera de la transacción, antes de la primera creación. No se modifica Atlas
al compilar. Se necesitan permisos para índices y escrituras en ambas colecciones.
Referencia: [transacciones de Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html).

Un conflicto transitorio o un commit de resultado incierto devuelve `503` con
`Retry-After: 1`. Reintenta con la misma clave y el mismo cuerpo; no se reintenta
automáticamente. No hay garantía de respuesta inmediata de éxito bajo contención,
pero la reserva y el contador se confirman o revierten juntos.

## Cancelar o finalizar

PUT `/{id}/estado`:

```json
{ "estado": "CANCELADA" }
```

También acepta FINALIZADA, solo a partir de la fecha de salida según
`app.hotel.zone` (por defecto America/Bogota). Se permiten únicamente las transiciones
CONFIRMADA → CANCELADA o CONFIRMADA → FINALIZADA. No se reabren reservas cerradas.
Repetir el mismo estado devuelve el estado actual sin descontar el contador otra vez.
Solicitar otro estado tras el cierre devuelve `409 RESERVA_CERRADA`.

DELETE equivale a cancelar: repetirlo sobre una reserva cancelada devuelve `204`.
Una reserva inexistente da `404`, y una finalizada da `409`. Cancelación/finalización
actualizan estado y contador en una transacción; no requieren Idempotency-Key.

Mientras una habitación tenga reservas CONFIRMADAS, su PUT y DELETE devuelven
`409 HABITACION_CON_RESERVAS`. Esto también aplica a reservas históricas que aún
no se hayan finalizado. No se cambia automáticamente el estado físico de la habitación
(OCUPADA, LIMPIEZA, etc.). No edites manualmente `reservasActivas` ni borres reservas:
el contador forma parte de la integridad entre colecciones.

Esta etapa no incluye reprogramación, cambio de huésped/habitación, check-in
ni cobros. Para corregir una reserva, cancela y crea otra
con una clave nueva. No es una operación conjunta de reprogramación.

## Verificación

Solo compilación, sin crear ni ejecutar pruebas automatizadas, por solicitud del usuario:

```powershell
.\mvnw.cmd -B -o compile '-Dmaven.test.skip=true'
```

No se ha verificado esta API mediante operaciones reales contra Atlas.

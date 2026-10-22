# API de finanzas

Base `/api/v1/finanzas`. Requiere sesión autenticada del hotel y CSRF en escrituras.
Registro manual de ingresos/egresos: no procesa pagos, facturas, impuestos ni
contabilidad oficial. La vista consume esta API para movimientos y resumen por moneda.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/movimientos` | Listado paginado |
| GET | `/movimientos/{id}` | Detalle; `404` si no existe |
| POST | `/movimientos` | Creación `201`; exige `Idempotency-Key` |
| PUT | `/movimientos/{id}/anulacion` | Anulación lógica `200` con motivo obligatorio |
| GET | `/resumen?moneda=COP` | Ingresos, egresos y balance de una sola moneda |

## Crear

```json
{
  "tipo": "INGRESO",
  "concepto": "Pago de alojamiento",
  "importe": 150000.00,
  "moneda": "COP",
  "fecha": "2026-12-10",
  "metodoPago": "EFECTIVO"
}
```

Tipo INGRESO o EGRESO. Concepto de 1–200 caracteres, recortado y sin controles.
Importe positivo hasta `999999999999.9999`; no se envían egresos negativos.
Moneda ISO 4217 admitida por Java, con 0–4 decimales definidos para esa moneda.
Se normaliza a mayúsculas; se rechazan importes que necesiten redondeo para ajustarse
a su escala. Se usa BigDecimal y BSON Decimal128, no double.
Fecha ISO entre 2000 y 2100; se permiten registros históricos y futuros.
Método EFECTIVO, TRANSFERENCIA, TARJETA u OTRO; omitirlo equivale a OTRO.

Respuesta: `id`, los campos del cuerpo, estado REGISTRADO, `creadoPor`, `creadoEn`
y `anulacion: null`. El operador viene de la sesión, no del cuerpo. Las marcas de
auditoría son UTC con precisión de milisegundos; la fecha del movimiento es civil.

## Idempotencia y anulación

Misma clave y mismos datos normalizados: mismo `201`, cuerpo y `Location`, con
`Idempotency-Replayed: true`. Misma clave y otros datos: `409`.
El ámbito es `POST:/api/v1/finanzas/movimientos`; la clave deriva el `_id` único.
Los datos originales y la auditoría de creación se conservan; no hay TTL ni borrado físico.
Repetir POST tras anular reproduce la creación REGISTRADO, pero no reactiva el
movimiento. GET muestra su estado actual. Claves distintas pueden crear movimientos
con los mismos datos: el cliente debe conservar la clave de cada operación.

PUT `/movimientos/{id}/anulacion`:

```json
{ "motivo": "Movimiento registrado por error" }
```

Motivo recortado de 1–300 caracteres, sin controles. Se guardan estado ANULADO,
motivo, operador y fecha en una única actualización condicional. Repetir el mismo
motivo devuelve el resultado original sin cambiar auditoría; otro motivo da `409`.
No requiere Idempotency-Key. No hay DELETE, edición del importe ni reactivación.
Para corregir: anular y crear otro movimiento con una clave nueva; no son una
operación conjunta y no equivalen a un reembolso bancario.

La unicidad de `_id` protege la creación concurrente, y cada cambio es una escritura
atómica de un solo documento. No se incrementa un saldo guardado por separado.
Errores transitorios o resultado incierto: `503` y `Retry-After: 1`; reintentar con
la misma clave/cuerpo, o el mismo motivo si se estaba anulando.

## Consultas

Listado: filtros opcionales `tipo`, `estado=REGISTRADO|ANULADO`, `moneda`, `desde`
y `hasta` (fechas ISO inclusivas). Sin estado se incluye el historial anulado.
`pagina`: 0–10000; `tamano`: 1–100, por defecto 20. Respuesta
`{items, pagina, tamano, total}`, ordenada por fecha e id descendentes.
Página y total son lecturas separadas y pueden variar con escrituras concurrentes.

Resumen: moneda obligatoria; desde/hasta opcionales, nunca desde posterior a hasta.
Solo incluye REGISTRADO. Devuelve `{moneda, desde, hasta, ingresos, egresos, balance,
movimientos}`; balance = ingresos − egresos. Sin datos devuelve ceros.
La suma se realiza en MongoDB, sin cargar todos los movimientos en Java ni mezclar
monedas. No hay conversiones de divisa, saldo inicial ni proyección de reservas.
Es un resumen operativo de los registros y filtros, no una certificación contable.

En la vista Finanzas, «Gestionar / actualizar» permite aplicar moneda, estado y
fechas inclusivas; también ofrece «Este mes» y «Todas las fechas». Las fechas
se aplican tanto al listado como al balance. Cambiar de pestaña o página conserva
los filtros aplicados, sin tomar campos editados pendientes de «Actualizar».
El balance siempre suma ingresos y egresos REGISTRADO, incluso si el listado
muestra solo anulados o una sola pestaña. La descarga JSON contiene exactamente
el resumen mostrado y su período; permanece deshabilitada durante la carga o si
falla el resumen, sin exportar datos anteriores.

La gráfica muestra como máximo seis meses hasta el mes de «Hasta», recortando
cada consulta al período elegido. Sin «Hasta», termina en el mes actual del
dispositivo (o el mes de «Desde» si es futuro). No representa todo un período
de más de seis meses; el balance y la descarga sí. Los meses fuera del período
se marcan con un guion y no se consultan.

El índice de periodo se prepara antes de la primera creación. No se escriben
registros automáticamente desde reservas. Las operaciones se limitan a la colección
`movimientos_financieros`, aparte de las sesiones de autenticación existentes.

Verificación: solo compilación, sin crear/ejecutar pruebas automatizadas ni realizar
operaciones reales contra Atlas.

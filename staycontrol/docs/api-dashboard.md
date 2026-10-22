# API v1: dashboard

`GET /api/v1/dashboard?moneda=COP`

Requiere sesión autenticada del hotel mediante la [API de autenticación](api-autenticacion.md).
No requiere CSRF ni `Idempotency-Key`: es una consulta sin escrituras, creación
de índices ni resúmenes persistentes. Repetirla no cambia los datos; la respuesta
puede variar si otros usuarios modifican los registros.

## Parámetros

- `moneda`: obligatoria, código ISO 4217 admitido por finanzas, por ejemplo `COP`.
- `fecha`: opcional, `AAAA-MM-DD`, años 2000 a 2100. Por defecto, hoy según
  `HOTEL_ZONE` (`America/Bogota` por defecto).
- `limiteReservas`: opcional, entero de 1 a 20; por defecto 5.

Ejemplo: `/api/v1/dashboard?moneda=COP&fecha=2026-09-10&limiteReservas=5`.

## Respuesta 200

- `fecha`, `zonaHoraria`: fecha de referencia y zona configurada.
- `habitacionesActuales`: `total`, `disponibles`, `ocupadas`, `enLimpieza`,
  `enMantenimiento`, `porcentajeOcupacion`. Excluye habitaciones eliminadas.
  El porcentaje es ocupadas / total × 100, redondeado a dos decimales.
- `reservasDelDia`: `entradasPrevistas`, `salidasPrevistas`, `estanciasProgramadas`.
  Solo reservas actualmente `CONFIRMADA`. Una estancia incluye la entrada y
  excluye la salida: entrada <= fecha < salida.
- `limpieza`: `pendientes`, `enProceso`, `habitacionesConTareas`. Solo tareas
  abiertas programadas hasta la fecha de referencia, incluidas las atrasadas.
  El último valor cuenta habitaciones distintas, no tareas.
- `finanzasMes`: `moneda`, `desde`, `hasta`, `ingresos`, `egresos`, `balance`,
  `movimientos`. Reutiliza el [resumen financiero](api-finanzas.md) para todo el
  mes calendario de la fecha, solo movimientos `REGISTRADO` de la moneda elegida.
- `proximasReservas`: lista acotada de confirmadas con entrada >= fecha,
  ordenadas por entrada e identificador. Cada elemento contiene `id`,
  `habitacionId`, `numeroHabitacion`, `huesped`, `personas`, `fechaEntrada`,
  `fechaSalida`. Si falta la habitación referenciada, su número será `null`.

Sin registros coincidentes, los contadores e importes son cero y la lista está vacía.

## Alcance y errores

La ocupación refleja el estado actual registrado manualmente de las habitaciones,
incluso si se consulta una fecha pasada o futura. No reconstruye ocupación histórica
ni convierte reservas en check-in/check-out reales. Las reservas finalizadas o
canceladas no aparecen en estos contadores, tampoco en consultas de fechas pasadas.
El resumen financiero incluye todo el mes, incluso movimientos fechados después
del día de referencia; no representa cobros automáticos de reservas.

Las métricas se leen en consultas separadas, sin una instantánea transaccional
común. Con cambios concurrentes pueden reflejar momentos ligeramente distintos.
Esta consulta no cambia estados de habitaciones, reservas ni tareas.

Parámetros ausentes o inválidos: 400. Sin sesión: 401. Sin autorización: 403.
Fallos de acceso a MongoDB: 503, sin exponer credenciales ni detalles de conexión.

La vista frontend consume esta API. Implementación backend verificada por
compilación; no se añadieron ni ejecutaron pruebas automatizadas ni se verificó
este endpoint contra Atlas.

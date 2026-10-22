# API v1: habitaciones e idempotencia

Primera API de negocio. URL base local: `http://127.0.0.1:8080/api/v1`.
La vista de habitaciones consume estos endpoints; requiere una sesión autenticada del hotel.
Las APIs de [reservas](api-reservas.md) y [limpieza](api-limpieza.md) ya existen;
las APIs de [finanzas](api-finanzas.md) y [dashboard](api-dashboard.md) también están disponibles.

## Seguridad y arranque

Esta versión requiere una sesión autenticada del hotel y CSRF en las escrituras. Consulta la
[API de autenticación](api-autenticacion.md) para aprovisionar el operador e iniciar
sesión. El servidor escucha en `127.0.0.1` por defecto. Antes de exponerlo en una red
pública se requiere HTTPS y cookies Secure. No se habilita CORS abierto.

Desde la carpeta que contiene este documento, sube un nivel y ejecuta:

```powershell
.\mvnw.cmd spring-boot:run
```

La conexión usa `.env`. Antes de la primera creación de cada hotel se prepara
su colección y el índice único `uk_habitaciones_numero` sobre `datos.numero`.
Se necesitan permisos de escritura y creación de índices. Si falla el índice,
no se permite la creación; no se borran datos ni se ignora el problema.
No se insertan habitaciones de ejemplo automáticamente.

## Endpoints

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/habitaciones?estado=DISPONIBLE&pagina=0&tamano=20` | `200`, listado paginado; estado opcional |
| GET | `/habitaciones/{id}` | `200`, habitación activa; `404` si no existe o está eliminada |
| POST | `/habitaciones` | `201`, creación o reproducción; exige `Idempotency-Key` |
| PUT | `/habitaciones/{id}` | `200`, sustituye los datos editables; cuerpo completo |
| DELETE | `/habitaciones/{id}` | `204`, baja lógica; repetir o usar un id inexistente también da `204` |

`id` es el identificador de 64 caracteres hexadecimales devuelto por el POST,
no el número de habitación. Un id con formato inválido devuelve `400`.
GET, PUT y DELETE no requieren `Idempotency-Key`.
PUT y DELETE devuelven `409 HABITACION_CON_RESERVAS` si hay reservas CONFIRMADAS;
primero deben cancelarse o finalizarse. Este control también evita conflictos con
la creación concurrente de reservas.
DELETE también devuelve `409 HABITACION_CON_LIMPIEZA` si existen tareas pendientes
o en proceso; primero deben completarse o cancelarse.

POST y PUT reciben `Content-Type: application/json` y este cuerpo:

```json
{
  "numero": "101",
  "tipo": "Individual",
  "capacidad": 1,
  "precioNoche": 25.00,
  "estado": "DISPONIBLE"
}
```

Todos los campos son obligatorios. No se aceptan campos desconocidos.

- `numero`: de 1 a 20 letras ASCII, dígitos o guiones; comienza con letra o dígito.
  Se recortan espacios exteriores y se convierte a mayúsculas. Es único e inmutable.
- `tipo`: entre 1 y 60 caracteres después de recortar espacios exteriores; sin controles.
- `capacidad`: entero entre 1 y 20; no admite números fraccionarios.
- `precioNoche`: decimal mayor que cero, hasta `999999999.99`, con un máximo de
  dos decimales significativos. Se usa `BigDecimal` y BSON `Decimal128`, no `double`.
  Expresa el importe en la moneda del establecimiento; esta etapa no convierte monedas.
- `estado`: `DISPONIBLE`, `OCUPADA`, `LIMPIEZA` o `MANTENIMIENTO`, como texto.
  La coordinación con reservas y sus reglas de ocupación aún está pendiente.

La respuesta individual contiene `id` y esos cinco campos, sin metadatos internos.
La lista contiene `items`, `pagina`, `tamano` y `total`. Se ordena por número
lexicográficamente. `pagina` empieza en cero (hasta 10000) y `tamano` va de 1 a 100.
El total y la página son lecturas separadas: pueden variar si hay escrituras concurrentes.

### Búsqueda por número o tipo

`GET /api/v1/habitaciones?buscar=Suite&estado=DISPONIBLE&pagina=0&tamano=20`

`buscar` es opcional. Admite hasta 60 caracteres sin controles, recorta espacios
exteriores y busca coincidencias parciales en `numero` o `tipo`, sin distinguir
mayúsculas/minúsculas. Los acentos no se eliminan. Los caracteres como `.` o `*`
son literales, no expresiones regulares. Un texto vacío mantiene el listado completo.
El filtro se combina con `estado`, excluye eliminadas y se aplica antes de paginar;
el total usa los mismos criterios. Texto inválido: 400. Requiere sesión autenticada.

La lupa de la vista abre el campo; Enter o «Buscar / actualizar» aplica la búsqueda.
«Limpiar búsqueda» elimina el texto conservando el estado. Los filtros de estado
incluyen Mantenimiento y se mantienen sincronizados con el selector.
La búsqueda parcial puede recorrer documentos; no añade un índice de texto ni
garantiza rendimiento para inventarios masivos.

## Contrato de idempotencia

1. Genera una clave nueva por **operación de creación**, preferiblemente un UUID.
   Se permiten de 1 a 128 letras ASCII, dígitos y los caracteres `.`, `_`, `:`, `-`.
2. Conserva la clave y el cuerpo en el cliente hasta conocer el resultado. Si se
   corta la conexión, reutilízalos: no generes otra clave dentro del reintento.
3. Una creación confirmada devuelve `201`, `Location` e `Idempotency-Replayed: false`.
4. Misma clave y mismos datos normalizados: mismo `201`, cuerpo y `Location`,
   pero `Idempotency-Replayed: true`. `25`, `25.0` y `25.00` son el mismo importe.
5. Misma clave con otros datos válidos: `409 IDEMPOTENCY_KEY_REUTILIZADA`.
6. Distinta clave para un número ya utilizado: `409 NUMERO_DUPLICADO`.
7. Ante `503` o un error de red, el resultado puede ser incierto. Espera al menos
   `Retry-After` cuando esté presente y reintenta con **la misma clave y el mismo cuerpo**.

Solo una creación guardada fija la clave. Los errores de validación o los conflictos
de número sin inserción no se almacenan como respuestas de idempotencia.
Una petición inválida recibe `400`, incluso si incluía una clave utilizada antes.

### Persistencia, concurrencia y borrado

La clave se combina con `POST:/api/v1/habitaciones` y se deriva un SHA-256 usado
como `_id`. No se almacena ni registra la clave en texto claro. La habitación y
la copia inmutable de sus datos de creación se insertan **en un único documento**.
No hay una caché en memoria ni dos escrituras separadas que puedan quedar a medias.

La unicidad de `_id` resuelve solicitudes simultáneas con la misma clave. El índice
único de número resuelve claves distintas para la misma habitación. Se usa `insert`,
no `save` ni `upsert`. Después de una colisión se consulta el documento ganador;
si aún no es visible se devuelve `503` para permitir un reintento seguro.
El cliente MongoDB usa lectura del primario y confirmaciones de lectura/escritura
`majority` para reducir resultados perdidos ante cambios de primario.

Este diseño se apoya en las [escrituras atómicas por documento de MongoDB](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/)
y sus [índices únicos](https://www.mongodb.com/docs/manual/core/index-unique/).

PUT utiliza `$set` de los datos completos, sin incrementos ni toggles. Dos PUT
iguales dejan el mismo estado. No modifica la creación original ni permite cambiar
el número. Las actualizaciones concurrentes diferentes siguen la regla de última
escritura; el control de versiones con `If-Match` queda pendiente.

DELETE usa `$set: {eliminada: true}`, nunca borra físicamente el documento.
El número y la clave quedan reservados, sin caducidad automática. GET/listado
ocultan las bajas y PUT no puede restaurarlas. Un POST original repetido después
de un PUT o DELETE reproduce la **respuesta de creación original**, no el estado
actual, y no modifica ni resucita la habitación. Para el estado actual se usa GET.
No borres documentos manualmente si necesitas conservar estas garantías.

Cada cuenta tiene colecciones de negocio aisladas mediante `HotelMongoTemplate`.
Las claves y los números de habitación se resuelven dentro de ese hotel, no en
las colecciones de otras cuentas. Ver [aislamiento multi-hotel](multi-hotel.md).
Reservas y limpieza coordinan sus cambios con habitaciones mediante transacciones.

## Probar desde PowerShell

Este ejemplo **sí crea una habitación** en la base configurada al ejecutarlo.
Usa un número que no exista. No lo ejecutes automáticamente contra datos reales.

```powershell
$apiBase = "http://127.0.0.1:8080/api/v1/habitaciones"
$authBase = "http://127.0.0.1:8080/api/v1/auth"
$sesionApi = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri "$authBase/csrf" -WebSession $sesionApi
$credencialesApi = Get-Credential -Message "Credenciales del operador StayControl"
$loginJson = @{
    email = $credencialesApi.UserName
    password = $credencialesApi.GetNetworkCredential().Password
} | ConvertTo-Json
$cabecerasLogin = @{}
$cabecerasLogin[$csrf.headerName] = $csrf.token
Invoke-RestMethod -Uri "$authBase/login" -Method Post -WebSession $sesionApi `
    -Headers $cabecerasLogin -ContentType "application/json" -Body $loginJson
Remove-Variable loginJson, credencialesApi
# El token cambia después del login.
$csrf = Invoke-RestMethod -Uri "$authBase/csrf" -WebSession $sesionApi
$claveOperacion = [guid]::NewGuid().ToString()
$cabecerasApi = @{ "Idempotency-Key" = $claveOperacion }
$cabecerasApi[$csrf.headerName] = $csrf.token
$cuerpoHabitacion = @{
    numero = "101"
    tipo = "Individual"
    capacidad = 1
    precioNoche = 25.00
    estado = "DISPONIBLE"
} | ConvertTo-Json

$primeraRespuesta = Invoke-WebRequest -Uri $apiBase -Method Post `
    -WebSession $sesionApi -Headers $cabecerasApi -ContentType "application/json" -Body $cuerpoHabitacion
$repeticion = Invoke-WebRequest -Uri $apiBase -Method Post `
    -WebSession $sesionApi -Headers $cabecerasApi -ContentType "application/json" -Body $cuerpoHabitacion

$primeraRespuesta.StatusCode                 # 201
$repeticion.StatusCode                       # 201
$repeticion.Headers["Idempotency-Replayed"]  # true
$habitacionCreada = $primeraRespuesta.Content | ConvertFrom-Json
Invoke-RestMethod -Uri "$apiBase/$($habitacionCreada.id)" -WebSession $sesionApi
Invoke-RestMethod -Uri "${apiBase}?estado=DISPONIBLE&pagina=0&tamano=20" -WebSession $sesionApi
```

Para verificar el `409`, reutiliza `$cabecerasApi` cambiando el cuerpo. Para una
operación nueva utiliza una clave nueva y otro número.

## Errores

```json
{
  "codigo": "IDEMPOTENCY_KEY_REUTILIZADA",
  "mensaje": "La Idempotency-Key ya se utilizó con otros datos."
}
```

Los errores de negocio/validación usan `400`, `404` o `409`. Los problemas de MongoDB
usan `503 BASE_NO_DISPONIBLE` y `Retry-After: 1`, sin URI, documentos ni detalles internos.
Los errores propios de protocolo HTTP (por ejemplo, método o Content-Type no admitidos)
mantienen el manejo estándar de Spring MVC.

## Pruebas

Desde el proyecto activo:

```powershell
.\mvnw.cmd test
node --test --test-isolation=none frontend/tests/*.test.js
```

Las pruebas cubren validación, normalización, reintentos, carreras simultáneas,
conflictos, conservación del original tras PUT/DELETE, serialización BSON, índices,
consultas atómicas y contratos HTTP. MongoDB se simula; estas pruebas no insertan
registros en Atlas ni certifican índices, permisos o failover en un clúster real.

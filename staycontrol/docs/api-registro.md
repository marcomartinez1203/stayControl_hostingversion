# Registro de usuario

`POST /api/v1/auth/registro`

Obtener primero `GET /api/v1/auth/csrf`, conservar la cookie y enviar el token
con el nombre de cabecera devuelto. Enviar `Content-Type: application/json`
y `Idempotency-Key` (1 a 128 letras, números, puntos, guiones, dos puntos o guiones bajos).

```json
{
  "usuario": "María",
  "nombreHotel": "Hotel Mirador",
  "email": "maria@example.com",
  "password": "una-clave-larga-de-ejemplo"
}
```

Nombre: 1 a 120 caracteres, recortado y sin controles. No es único; el inicio de
sesión usa email normalizado a minúsculas. Contraseña: 12 a 128 caracteres,
no solo espacios; no se recorta. No se acepta rol desde el cliente.

`nombreHotel` es obligatorio: de 1 a 120 caracteres, recortado y sin controles.
Se guarda en el usuario. `GET /api/v1/auth/me` devuelve `{email, usuario, nombreHotel}`
para el encabezado del dashboard. Las cuentas anteriores reciben `nombreHotel: null`
y muestran «Mi hotel»; no se les asigna un nombre inventado. El aislamiento se
basa en la cuenta autenticada, no en el nombre (ver `multi-hotel.md`). El nombre se
puede configurar o editar desde «Cuenta» mediante `PUT /api/v1/auth/me/hotel`.

Devuelve 201 con `{email, usuario, rol: "USER", nombreHotel}` y `Idempotency-Replayed: false`.
La contraseña solo se guarda con hash PBKDF2, nunca en respuesta o logs.
El email es `_id` de MongoDB: las solicitudes concurrentes no duplican cuentas.

La idempotencia está delimitada por correo y operación: misma clave, correo,
nombre de usuario, nombre del hotel y contraseña devuelven 201 y `Idempotency-Replayed: true`. Otro correo tiene
un ámbito distinto. Cuenta existente con otra clave o datos: 409 genérico; no se
sobrescribe. El reintento compara el hash actual: al implementar cambio de contraseña
habrá que definir la política de reintentos posterior. También compara el nombre de
hotel actual: tras editarlo, repetir el registro original con el nombre anterior
devuelve 409 y no revierte el cambio. Para reintentar una edición usa su PUT, no registro.

El registro no inicia sesión ni verifica propiedad del correo. USER puede iniciar
sesión y administrar exclusivamente su hotel. No necesita ser promovido a ADMIN.
No existe endpoint público para elevar roles ni obtener acceso a otros hoteles.

400: datos o clave inválidos. 403: CSRF inválido. 409: registro no disponible.
429: límite compartido con login, 10 intentos por IP/minuto por instancia.
503: persistencia no disponible; reintentar con la misma clave.
El límite local no sustituye protección distribuida contra abuso ni verificación de correo.

La vista `/registro` consume este endpoint mediante `registroController.js` y
`authService.js`. Valida confirmación de contraseña, bloquea envíos simultáneos y
conserva la clave en memoria para reintentos sin editar los datos. Recargar la página
pierde esa clave; el email único sigue evitando duplicados. No guarda contraseñas
en localStorage ni sessionStorage. Tras crear la cuenta, muestra confirmación y
permite volver al login; no inicia sesión automáticamente.

Recuperación sigue siendo solo visual: sin envío de correos ni restablecimiento.
Integración frontend revisada por sintaxis, sin crear usuarios reales ni verificar
el flujo completo contra Atlas.

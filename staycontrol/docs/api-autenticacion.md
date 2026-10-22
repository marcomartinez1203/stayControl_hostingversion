# Autenticación: API v1

Autenticación con Spring Security, usuarios en MongoDB y sesiones HTTP.
La vista de login llama al backend; no guarda contraseñas ni tokens en localStorage
y no registra contraseñas en consola. Las vistas de negocio consumen sus APIs con sesión autenticada del hotel.

## Endpoints

| Método | Ruta | Respuesta |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | `200`, `{headerName, token}`; público, crea sesión anónima si hace falta |
| POST | `/api/v1/auth/login` | `200`, `{email}`; requiere JSON y CSRF |
| POST | `/api/v1/auth/registro` | `201`, `{email, usuario, rol, nombreHotel}`; público con CSRF e Idempotency-Key |
| GET | `/api/v1/auth/me` | `200`, `{email, usuario, nombreHotel}`; `401` si no hay sesión válida |
| PUT | `/api/v1/auth/me/hotel` | `200`, `{email, usuario, nombreHotel}`; sesión USER o ADMIN y CSRF |
| POST | `/api/v1/auth/logout` | `204`, invalida sesión y elimina cookie; requiere CSRF |

El logout lo procesa el filtro de Spring Security, no un método MVC.
Login recibe `{"email":"operador@example.test","password":"tu contraseña"}`.
El email se recorta y convierte a minúsculas; la contraseña **no se recorta**.
No se admiten campos adicionales, como roles, ni se reciben roles desde el cliente.
Todas las rutas `/api/**`, salvo login/logout/CSRF/registro en los métodos indicados
y GET `/auth/me` y PUT `/auth/me/hotel`, requieren sesión autenticada. Cada cuenta
accede exclusivamente a sus colecciones de hotel; ver `multi-hotel.md`.
HTML, CSS y JS siguen siendo públicos:
la protección de datos está en las APIs, no en ocultar pantallas.

## Editar el nombre del hotel

Desde «Cuenta» en cualquier vista de negocio, escribe el nombre y pulsa «Guardar hotel».
También funciona para cuentas anteriores al campo `nombreHotel`.

`PUT /api/v1/auth/me/hotel` recibe `{"nombreHotel":"Hotel Mirador"}`: 1 a 120
caracteres, sin controles y recortado. Solo cambia el campo de la cuenta autenticada;
no acepta un email destino, no crea usuarios, ni cambia contraseña o rol.
Es idempotente: repetir el mismo nombre no añade registros ni requiere `Idempotency-Key`.
Actualizaciones concurrentes de nombres distintos conservan el último guardado.
Requiere CSRF, incluso con sesión iniciada. Validación inválida: 400; sin sesión/cuenta: 401;
CSRF inválido: 403; error de MongoDB: 503.

El nombre es descriptivo; la identidad del hotel se deriva de la cuenta autenticada.

## Crear el operador inicial

No existe usuario predeterminado ni contraseña predeterminada. El [registro público](api-registro.md)
crea USER, nunca ADMIN. USER administra su propio hotel sin acceso a otros hoteles.
Los documentos antiguos sin rol conservan ADMIN porque se aprovisionaron antes
del registro público. Los nuevos registros guardan explícitamente USER.

En tu `.env` local agrega estas variables con valores propios (no las publiques):

```dotenv
AUTH_BOOTSTRAP_ENABLED=true
AUTH_ADMIN_EMAIL=tu-correo@example.com
AUTH_ADMIN_PASSWORD=<elige-una-contrasena-aleatoria>
```

1. Usa una contraseña nueva de 12 a 128 caracteres; no reutilices la de MongoDB.
2. Arranca el proyecto Maven **staycontrol** desde Spring Boot Dashboard.
3. El arranque crea un documento en `usuarios` con email normalizado como `_id`
   y contraseña con hash PBKDF2-HMAC-SHA256 con sal aleatoria (600000 iteraciones).
   El hash incluye un identificador de algoritmo para futuras migraciones.
4. Desactiva `AUTH_BOOTSTRAP_ENABLED=false`, retira `AUTH_ADMIN_PASSWORD` de `.env`
   y reinicia. El usuario permanece en MongoDB.
5. Abre `/login` e inicia sesión con esas credenciales.

El aprovisionamiento es idempotente por email: los reinicios y arranques simultáneos
no duplican el usuario ni sobrescriben su contraseña. Cambiar la variable no cambia
la contraseña de un usuario existente. El aprovisionamiento queda desactivado por defecto.
No se ha creado ninguna cuenta real durante las pruebas.

La contraseña de la aplicación se escribe sin codificación URI. `.env` se lee como
archivo de propiedades Java: para caracteres que necesiten escapes (por ejemplo,
una barra inversa) usa los escapes de propiedades o variables de entorno del proceso.
Nunca compartas estas credenciales en el chat ni las copies a `.env.example`.

## Sesión, CSRF e idempotencia

- Cookie `JSESSIONID`, HttpOnly y SameSite=Lax. Solo cookies, no identificadores en URLs.
- Sesión con 30 minutos de inactividad; se pierde al reiniciar el servidor.
- Primer login: rotación del identificador de sesión y del token CSRF.
- Antes de POST/PUT/PATCH/DELETE: obtener `/auth/csrf` y enviar el token con el
  nombre de cabecera devuelto. Mantener la cookie en todas las peticiones.
- Después de login/logout debe obtenerse un token nuevo. `apiService.js` lo hace
  antes de cada escritura a `/api/`, usando cookies del mismo origen.
- Login valida las credenciales en cada intento. Repetirlo con la misma sesión
  autenticada devuelve el mismo email sin rotar otra vez el identificador.
- Logout repetido deja al cliente sin autenticar y devuelve `204` con un CSRF
  vigente, incluso desde una sesión anónima. Un token antiguo devuelve `403`.
- No se almacena `Idempotency-Key` ni se cachean respuestas de login que contengan
  credenciales. No se promete una sola sesión si se pierde la cookie de la primera
  respuesta y el cliente vuelve a iniciar sesión desde otra sesión anónima.
- Las creaciones de habitaciones mantienen su `Idempotency-Key`, ahora además
  con sesión autenticada y CSRF, dentro del hotel. El cliente no genera otra clave al reintentar.

Diseño basado en [sesiones de Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html),
[protección CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
y [almacenamiento de contraseñas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
El coste PBKDF2 sigue el [mínimo de OWASP para HMAC-SHA256](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2).

## Errores y límites

`400` JSON o credenciales con formato inválido; `401` credenciales incorrectas
o sesión ausente; `403` CSRF inválido o permisos insuficientes; `429` demasiados
intentos; `503` servicio de autenticación no disponible. Email inexistente y
contraseña incorrecta devuelven el mismo mensaje, sin revelar detalles de MongoDB.

Se permiten 10 intentos de login por IP por minuto, incluidos los exitosos.
El límite tiene hasta 10000 IPs en memoria y devuelve `Retry-After: 60` cuando
se supera. No confía en cabeceras de IP suministradas por el cliente.
Para múltiples instancias se necesita un limitador compartido y sesiones compartidas.

Solo desarrollo local por defecto (`127.0.0.1`). Antes de desplegar: HTTPS obligatorio,
`SESSION_COOKIE_SECURE=true`, configurar proxy de confianza, sesiones persistentes
si hay varias instancias y revisar el límite de intentos por IP. No habilitar CORS
abierto. Esta versión no incluye MFA, recuperación/cambio de contraseña, gestión
de usuarios, registro de huéspedes, JWT ni roles diferenciados. Un cliente híbrido
debe conservar cookies; para un origen distinto se necesita un diseño CORS/CSRF
explícito, no desactivar CSRF.

## Pruebas

`mvnw.cmd test`: login real a través del filtro de seguridad y del encoder, con
repositorio MongoDB simulado; persistencia y rotación de sesión, CSRF, logout,
errores uniformes, autorización, límites y aprovisionamiento idempotente.

`node --test --test-isolation=none frontend/tests/*.test.js`: cliente de autenticación,
cookies, tokens frescos, conservación de contraseñas y claves de idempotencia,
propagación de errores y regresiones del frontend. Sin escrituras en Atlas.

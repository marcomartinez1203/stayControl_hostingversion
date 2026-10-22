# Render con MongoDB local mediante Tailscale

Estado: archivos preparados, sin desplegar ni comprobar la conexion remota.
No se modifico el servicio MongoDB, el firewall, la cuenta Tailscale ni el proyecto
original. Esta configuracion necesita esos pasos adicionales antes de funcionar.

## 1. Restringir el acceso en Tailscale

El PC mostrado tiene IP `100.96.225.102`. Comprueba que sigue siendo su direccion.
En Access controls define una etiqueta `tag:staycontrol-render` cuyo propietario
sea un administrador. Autoriza esa etiqueta solamente hacia la IP del PC en
TCP 27017. Ejemplo de fragmentos para integrar en la politica existente:

```json
{
  "tagOwners": {"tag:staycontrol-render": ["autogroup:admin"]},
  "grants": [{
    "src": ["tag:staycontrol-render"],
    "dst": ["100.96.225.102"],
    "ip": ["tcp:27017"]
  }]
}
```

No reemplaces toda la politica sin revisar los permisos de otros dispositivos.
Las reglas son aditivas: una regla existente que permita todo anularia el efecto
restrictivo de este ejemplo. Revisa y limita cualquier permiso amplio aplicable
a la etiqueta, y valida en el editor de Tailscale antes de guardar.

En Settings > Keys crea una auth key etiquetada `tag:staycontrol-render`,
ephemeral y reusable (el contenedor no conserva estado entre reinicios).
Si tu red exige aprobacion de dispositivos, apruebalo o usa una clave preaprobada.
Guarda la clave solo en `TS_AUTHKEY` en Render; no la envies por chat ni Git.
Configura una caducidad y renuevala antes de vencer: los nuevos arranques la necesitan.

## 2. Preparar MongoDB en Windows (requiere autorizacion adicional)

Estas operaciones afectan el servicio compartido con el proyecto original.
No las ejecuta el Dockerfile. Antes de cambiarlas, respalda su configuracion.

- Confirma que `rs0` es un replica set de UN miembro y que ese miembro es PRIMARY.
- Crea un usuario dedicado con `readWrite` sobre `staycontrol`, sin rol de administrador.
- Activa autenticacion correctamente para un replica set, incluyendo la autenticacion
  interna requerida (keyFile o equivalente). No basta con agregar authorization
  sin configurar el replica set. El usuario debe existir antes de bloquear el acceso.
- Configura `net.bindIp` para incluir `127.0.0.1` y `100.96.225.102`, no `0.0.0.0`.
- Limita el firewall a la interfaz/direccion Tailscale y al cliente de Render.
  Los nodos efimeros pueden cambiar de IP; revisa la regla si se registra uno nuevo.
- No abras 27017 en el router. No uses Tailscale Funnel para publicar MongoDB.
- Reinicia MongoDB solo despues de revisar la configuracion y sus credenciales.

Activar autenticacion puede exigir actualizar las credenciales de las aplicaciones
locales: por eso este paso no se realiza automaticamente con permiso limitado a
la carpeta de hosting.

La URI de ejemplo usa `directConnection=true` para conectar al unico miembro
sin descubrir su direccion anunciada (que podria ser localhost). No elimina el
requisito de replica set ni las transacciones. No usar ese ejemplo para un cluster
de varios miembros; en ese caso todos sus nombres deben ser accesibles desde Render.

## 3. Render

Sigue `../RENDER.md`. Configura las variables de `render.env.example` en el panel,
reemplazando usuario y contrasena. El ejemplo supone un usuario creado en `admin`;
cambia `authSource` si lo creaste en otra base. Codifica caracteres especiales.

El script inicia Tailscale sin privilegios en modo userspace y un proxy SOCKS5
en `127.0.0.1:1055` dentro del contenedor. El controlador Java usa ese proxy con
las opciones de la URI; no se necesita cambiar las APIs ni el frontend.
Se espera a que Tailscale autentique antes de iniciar Java. Si uno de los procesos
termina, se detiene el otro. No se guardan claves en la imagen.

## 4. Comprobacion tras el despliegue

1. Debe aparecer `staycontrol-render` conectado en Machines.
2. Los logs de Spring deben confirmar el inicio y la comprobacion de MongoDB.
3. Abre la URL HTTPS de Render e inicia sesion con una cuenta existente.
4. Consulta habitaciones: esto verifica una lectura real, no solo el HTML.

Si falla: revisa autorizacion de la etiqueta, firewall, bindIp, credenciales,
estado PRIMARY del replica set y que el PC no este suspendido. No publiques
logs que contengan credenciales. Un ping de Tailscale por si solo no demuestra
que MongoDB acepte conexiones.

El PC debe permanecer encendido y Tailscale conectado. Render Free se suspende
por inactividad y comparte su memoria limitada entre Java y Tailscale; la viabilidad
con esa memoria debe confirmarse al desplegar. No es alta disponibilidad.

Referencias:
- https://tailscale.com/docs/concepts/userspace-networking
- https://tailscale.com/docs/reference/tailscale-cli
- https://www.mongodb.com/docs/drivers/java/sync/current/security/socks/
- https://www.mongodb.com/docs/manual/reference/connection-string-options/

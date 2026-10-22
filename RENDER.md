# Render con MongoDB Atlas

Esta copia compila Vue y Spring Boot juntos mediante Docker. No necesita
Tailscale ni MongoDB local.

1. En Render selecciona el repositorio de hosting, rama main, lenguaje Docker.
2. Root Directory vacio, Dockerfile Path ./Dockerfile, contexto . y plan Free.
3. En Environment define MONGODB_URI y MONGODB_DATABASE=staycontrol.
   Usa deploy/render.env.example y sustituye <db_password> por la contrasena
   del usuario de base de datos Atlas (codifica caracteres especiales en la URI).
4. En Atlas Network Access autoriza las direcciones de salida del servicio Render.
5. Elimina de Render TAILSCALE_ENABLED, TS_HOSTNAME y TS_AUTHKEY si las agregaste.
   Revoca la clave Tailscale que ya no usaras.
6. Despliega y abre la URL HTTPS en /index.html#/login.

El .env local no se publica ni se incluye en Docker. Configuralo por separado.
El contenedor usa PORT (10000 por defecto), escucha en 0.0.0.0 y exige cookies
seguras. No realiza pruebas ni crea usuarios predeterminados.

Render Free se suspende por inactividad; su memoria es limitada. La imagen aun
requiere validacion de compilacion y conexion real en Render o con Docker local.

No se borraron los datos de la instancia local de preparacion.

Referencia: https://render.com/docs/docker

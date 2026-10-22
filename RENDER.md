# Despliegue de StayControl en Render

Esta copia incluye un Dockerfile que compila Vue con Node 24, empaqueta el
frontend dentro de Spring Boot con Java 21 y ejecuta ambos en un solo servicio.
No necesita Vercel ni una segunda URL para la API. No ejecuta pruebas.

## Configuracion

1. Sube esta carpeta como raiz de un repositorio, sin `.env`, copias de seguridad
   ni `.git-copia-original`. Revisa los archivos antes de hacer commit.
2. En Render: New > Web Service > selecciona ese repositorio.
3. Language: Docker. Plan: Free.
4. Root Directory: vacio, si esta carpeta es la raiz del repositorio.
5. Dockerfile Path: `./Dockerfile`. Docker Build Context: `.`.
6. Deja Docker Command vacio: el Dockerfile ya define el arranque.
7. Configura estas variables en Environment, no dentro del Dockerfile:

| Variable | Valor |
| --- | --- |
| `TAILSCALE_ENABLED` | `true` |
| `TS_HOSTNAME` | `staycontrol-render` |
| `TS_AUTHKEY` | Clave secreta de incorporacion de Tailscale |
| `MONGODB_URI` | URI privada indicada en `deploy/render.env.example`, con credenciales reales |
| `MONGODB_DATABASE` | `staycontrol` |

El contenedor ya define `SERVER_ADDRESS=0.0.0.0`,
`SESSION_COOKIE_SECURE=true` y `AUTH_BOOTSTRAP_ENABLED=false`.
Usa el puerto `PORT` de Render, con 10000 como alternativa.
La URI debe codificar los caracteres especiales de la contrasena cuando sea necesario.
El archivo `.env` local no se incluye en la imagen ni se carga en Render.

Antes de desplegar completa `deploy/TAILSCALE.md`: autorizacion privada,
usuario de MongoDB, interfaz de escucha y firewall del PC. La direccion
`100.96.225.102` procede de la captura del usuario; confirma que siga vigente.
No expongas contrasenas en GitHub ni abras MongoDB directamente a Internet.
El `.env` sigue apuntando a localhost para ejecutar esta copia en el PC;
Render recibe una URI distinta mediante sus variables de entorno.

Abre la URL HTTPS de Render en `/index.html#/login`. Las cookies requieren HTTPS.
El servicio Free se suspende tras 15 minutos sin trafico; al volver puede tardar
aproximadamente un minuto. La memoria disponible es limitada: si el proceso se
queda sin memoria, revisa los logs antes de considerar un plan de pago.

## Compilacion manual (requiere Docker)

Desde la carpeta que contiene el Dockerfile:

```sh
docker build -t staycontrol-host .
```

No se ha publicado ningun servicio automaticamente. La validacion de la imagen
requiere Docker o un primer build en Render.

Referencias: https://render.com/docs/docker y https://render.com/docs/free

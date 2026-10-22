# MongoDB local (Windows)

Compass es el cliente para consultar datos. StayControl se conecta directamente
al servicio MongoDB, no a Compass. Reservas y limpieza utilizan transacciones,
por lo que un servidor standalone no es suficiente.

## Configurar una vez

Para la instalación detectada en este equipo (MongoDB 9.0, servicio `MongoDB`),
abre PowerShell **como administrador** y ejecuta desde la raíz del workspace:

```powershell
.\staycontrol\scripts\Configurar-MongoLocal.ps1
```

El script comprueba que sea el archivo utilizado por el servicio y que MongoDB
solo escuche en `127.0.0.1`. Conserva una copia de `mongod.cfg`, mantiene las
rutas de datos/logs, agrega `replication.replSetName: rs0`, reinicia el servicio
e inicializa un replica set de un miembro si no existe. No borra datos ni
modifica Atlas. Requiere Java y los drivers descargados por Maven (5.8.1).
Ejecutarlo nuevamente no reinicializa ni reconfigura un replica set existente.
Si usas otra instalación, indica `-ConfigPath` con el archivo de su servicio.

Es una configuración de desarrollo local, no alta disponibilidad ni una
configuración segura para exponer MongoDB a otros equipos.

## Conexión de la aplicación

En `.env`:

```properties
MONGODB_URI=mongodb://localhost:27017/staycontrol?replicaSet=rs0
MONGODB_DATABASE=staycontrol
```

Una variable `MONGODB_URI` definida en el proceso o en la configuración de
ejecución del IDE tiene prioridad sobre `.env`: retira cualquier valor antiguo
de Atlas allí. Reinicia Spring Boot después de configurar rs0.
Busca `Conexión a MongoDB verificada correctamente (ping OK)` en su consola.

En Compass utiliza `mongodb://localhost:27017/?replicaSet=rs0`.
La base y sus colecciones aparecen conforme la aplicación guarda datos;
no necesitas crearlas manualmente. MongoDB local y Atlas son bases diferentes:
no se han copiado usuarios ni datos de Atlas. Registra una cuenta local desde
`http://localhost:8080/registro` si todavía no tienes una en esta base.

Si necesitas recuperar la conexión anterior, la copia `.env.before-local-20261007.bak`
queda en la raíz, excluida de Git. Contiene configuración privada: no compartir.

Referencia: [convertir standalone a replica set](https://www.mongodb.com/docs/manual/tutorial/convert-standalone-to-replica-set/).

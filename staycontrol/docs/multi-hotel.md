# Aislamiento multi-hotel

Cada cuenta registrada administra un hotel independiente. Puede entrar y usar
habitaciones, reservas, limpieza, finanzas y dashboard sin que alguien la eleve
a ADMIN. No hay invitaciones de empleados ni varias cuentas para el mismo hotel.

`HotelMongoTemplate` resuelve las colecciones de negocio a partir del email
normalizado de la sesión autenticada, nunca de un parámetro enviado por el cliente.
El prefijo es `hotel_<SHA-256>_`; dentro de él están `habitaciones`, `reservas`,
`tareas_limpieza` y `movimientos_financieros`. `usuarios` permanece global para
autenticar y asegurar unicidad del correo. No se permite cambiar el email.

El nombre visible del hotel puede editarse sin mover registros ni cambiar su
identidad. Dos hoteles pueden tener el mismo nombre y el mismo número de habitación.
IDs, claves de idempotencia, índices únicos, búsquedas, agregaciones del dashboard
y transacciones se resuelven dentro de las colecciones de la cuenta actual.
Los endpoints siguen requiriendo sesión; las escrituras también requieren CSRF.

Los repositorios usan las sobrecargas de MongoTemplate basadas en las clases del
modelo. Nuevos repositorios deben seguir ese patrón: no usar nombres de colección
globales, acceso directo al driver, un MongoTemplate alternativo o `withSession`
que sustituya la plantilla. Las transacciones comparten la misma MongoDatabaseFactory.
No se crean índices de negocio al arrancar, sino antes de la primera escritura
en cada colección del hotel. Un fallo de índice impide la escritura.

## Datos anteriores

Las colecciones anteriores no se borran ni se copian automáticamente. Por defecto
quedan fuera del alcance de las cuentas. Para conservar su acceso exclusivamente
desde la cuenta existente que sea su propietaria, configura en `.env`:

```dotenv
LEGACY_HOTEL_OWNER_EMAIL=propietario-existente@example.com
```

Usa únicamente una cuenta existente cuya propiedad hayas confirmado. No uses un
correo libre que otra persona pueda registrar. No hay verificación del correo.
El servidor no decide por su cuenta quién es dueño de registros históricos.
Después de configurarlo, reinicia. No cambies esta variable para intercambiar
hoteles: reasignaría el acceso al historial. Cambiarla después de crear registros
en colecciones nuevas tampoco los fusiona con el historial anterior.

## Alcance de verificación

Se revisó el uso de la plantilla por todos los repositorios y se compiló el JAR.
No se ejecutaron pruebas automatizadas ni se crearon hoteles de prueba en Atlas.
Antes de publicar deben comprobarse dos cuentas separadas, lecturas/escrituras
cruzadas, concurrencia e idempotencia en un entorno de pruebas autorizado.
El registro público no verifica propiedad del correo y la recuperación sigue aplazada.

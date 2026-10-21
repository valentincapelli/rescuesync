# Sistema Nacional de Gestión de Recursos y Riesgos (API)

API en el Cloud que consume Bonita (Entrega 3). Es un servicio aparte del backend de RescueSync, con su **propia base PostgreSQL**. No guarda emergencias, lotes ni ofertas: solo las referencia por id (`emergencia_ref`, `oferta_ref`).

Stack: Java 21 + Spring Boot 3.5.6 + Spring Data JPA + PostgreSQL, igual que `rescuesync-backend`.

## Cómo levantarla

```bash
cd rescuesync-sistema-nacional
docker compose up -d db          # PostgreSQL en localhost:5434
mvn spring-boot:run              # API en http://localhost:8082
```

O todo en Docker, con la misma imagen y las mismas variables que en Render:

```bash
docker compose up -d --build     # PostgreSQL + API
```

| URL | Para qué |
|---|---|
| http://localhost:8082/api/info | Verificar que la API está arriba |
| http://localhost:8082/actuator/health | Health check (lo usa Render) |
| http://localhost:8082/swagger-ui.html | Swagger UI |

Puertos: 8080 = Bonita, 8081 = backend de RescueSync, 8082 = esta API. La base usa el 5434 para no chocar con la de RescueSync (5433).

## Tests

```bash
mvn test
```

Corren contra H2 en memoria (no hace falta PostgreSQL). `ModeloDatosTests` verifica que las restricciones de E3-01 existan de verdad en la base: CUIT único, una certificación por ONG y tipo, un recurso por ONG/tipo/unidad, el CHECK de stock, la idempotencia de compromisos y el bloqueo `FOR UPDATE`.

## Variables de entorno

Todas tienen valor por defecto en `application.yml`, pensado para desarrollo local (ver `.env.example`). En producción las marcadas como obligatorias **tienen** que venir del entorno.

| Variable | Default (local) | En Render | Nota |
|---|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5434` / `sistema_nacional` | **Obligatorias** (`fromDatabase`) | Con ellas se arma `jdbc:postgresql://HOST:PORT/NAME`. Render no da una URL JDBC sino `postgres://...` o los datos sueltos |
| `DB_USER` / `DB_PASSWORD` | `sistema_nacional` | **Obligatorias** (`fromDatabase`) | |
| `DB_SSLMODE` | `prefer` | Opcional | `prefer` usa SSL si el servidor lo ofrece. Por la red interna de Render no hace falta; poner `require` si se conecta a una base externa |
| `DB_URL` | (vacía) | No se usa | Si está, pisa a `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_SSLMODE` |
| `JWT_SECRET` | valor de desarrollo escrito en el repo | **Obligatoria** (`generateValue`) | Mínimo 32 caracteres. El default es público: nunca usarlo fuera de local |
| `COOKIE_SECURE` | `false` | **`true`** | Cookie `access_token` con flag `Secure` (solo viaja por HTTPS). En local va `false` porque se usa http |
| `JWT_EXPIRATION` | `86400000` (24 h) | Opcional | En milisegundos |
| `SERVER_PORT` | `8082` | No se usa | Si no está, se usa `PORT`, que inyecta Render |
| `JPA_DDL_AUTO` | `update` | `update` | Deuda: pasar a `validate` + migraciones cuando el modelo se estabilice |
| `CORS_ORIGINS` | `http://localhost:5173` | Opcional | Solo si un frontend llama a la API desde el navegador. Bonita llama servidor a servidor y no lo necesita |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` (en la imagen) | Opcional | Flags de la JVM; se pisan sin reconstruir la imagen |

## Despliegue en Render

La API se despliega como **web service Docker** con una **base PostgreSQL** de Render, ambos en plan free, definidos en el Blueprint [`render.yaml`](render.yaml).

### 1. Crear el Blueprint

1. En Render: **New > Blueprint** y elegir el repo `rescuesync`.
2. En **Blueprint Path** poner `rescuesync-sistema-nacional/render.yaml`. El archivo no está en la raíz del repo: si se deja el valor por defecto, Render dice que no lo encuentra.
3. Elegir la rama a desplegar (por defecto, la principal del repo).
4. Render pide un valor para `CORS_ORIGINS`: dejarlo vacío salvo que algún frontend desplegado llame a la API (en ese caso, su URL, sin `/` final).
5. **Apply**. Render crea la base `sistema-nacional-db` y el servicio `rescuesync-sistema-nacional`, genera `JWT_SECRET` y conecta todo por la red interna.

Las rutas del Blueprint (`dockerfilePath`, `dockerContext`) son relativas a la raíz del repo, por eso apuntan a `./rescuesync-sistema-nacional/...`. El `buildFilter` hace que solo se redespliegue cuando cambia esta carpeta.

### 2. Verificar el deploy

Con la URL pública del servicio (`https://<servicio>.onrender.com`):

| URL | Esperado |
|---|---|
| `/actuator/health` | `200` con `{"status":"UP"}` (es el health check que usa Render) |
| `/api/info` | `200` con la versión y `"status":"UP"` |
| `/swagger-ui.html` | Swagger UI. Loguearse con `POST /api/auth/login`: la cookie llega con `Secure` y Swagger la reenvía sola |

### 3. Cargar el seed de demo

El seed **no** se ejecuta al arrancar; se carga a mano una sola vez, sobre la base vacía, **después** del primer deploy (con `JPA_DDL_AUTO=update` es la API la que crea las tablas).

1. En el panel de la base (`sistema-nacional-db`) > **Connect**, copiar la **External Database URL** (`postgresql://usuario:clave@host.virginia-postgres.render.com/sistema_nacional`).
2. Desde la raíz del repo:

   ```bash
   psql "<External Database URL>" -f scripts/seed-sistema-nacional-demo.sql
   ```

   Sin `psql` instalado, se puede usar el de Docker:

   ```bash
   docker run --rm -i postgres:16-alpine psql "<External Database URL>" < scripts/seed-sistema-nacional-demo.sql
   ```

3. La consulta final del script lista los 5 usuarios creados.

Las credenciales del seed (contraseña `123456` para todos, incluido el ADMIN) son **solo para la demo** de la cátedra: no usarlas con datos reales.

### Plan free: tener en cuenta

- **El servicio se duerme** tras ~15 minutos sin tráfico. El primer request después tarda **~30-60 s** mientras arranca: el conector de Bonita tiene que tener un timeout mayor (o hacer un request de "despertar" antes, por ejemplo a `/actuator/health`).
- La base free de Render **vence a los 30 días** de creada; después hay que recrearla (y volver a cargar el seed) o pasarla a un plan pago.
- 512 MB de RAM: la JVM usa como máximo el 75 % para el heap (`-XX:MaxRAMPercentage=75`). Si Render reinicia el servicio por memoria, bajarlo con `JAVA_TOOL_OPTIONS`.

### Deuda: esquema sin migraciones

Hoy el esquema lo crea Hibernate con `JPA_DDL_AUTO=update`. Sirve para el primer despliegue, pero `update` no borra columnas ni restricciones viejas, no renombra y no deja historial. Cuando el modelo se estabilice: agregar migraciones (Flyway) y pasar a `validate`.

## Estructura

```
src/main/java/com/grupo21/sistemanacional/
├── config/       JPA auditing, Swagger, CORS
├── controller/   Info, Auth, Ong, Certificacion y Recurso
├── dto/          ApiError (mismo formato de error que rescuesync-backend), InfoResponse
├── exception/    GlobalExceptionHandler, BusinessException (409), ResourceNotFoundException (404),
│                 StockInsuficienteException (409 con lo disponible)
├── model/        Entidades de las 5 tablas + enums
└── repository/   Repositorios Spring Data, con las consultas de bloqueo y disponibilidad
```

## Modelo de datos (E3-01)

```
ongs 1─N usuarios
ongs 1─N certificaciones
ongs 1─N recursos 1─N compromisos
```

| Tabla | Restricciones en la base |
|---|---|
| `usuarios` | `username` único; `ck_usuarios_ong_obligatoria`: rol `ONG` exige `ong_id` |
| `ongs` | `cuit` único |
| `certificaciones` | `uk_certificaciones_ong_tipo` (ong_id, tipo_recurso) |
| `recursos` | `uk_recursos_ong_tipo_unidad` (ong_id, tipo_recurso, unidad_medida); `ck_recursos_stock`: `0 <= cantidad_comprometida <= cantidad_total` |
| `compromisos` | `uk_compromisos_recurso_oferta` (recurso_id, oferta_ref) para la idempotencia; `ck_compromisos_cantidad`: `cantidad > 0` |

`TipoRecurso` y `NivelGravedad` tienen los mismos valores que en `rescuesync-backend`. Si se agrega un valor en un lado, hay que agregarlo en el otro.

### Locking

`RecursoRepository.findAllByIdForUpdate(ids)` hace `SELECT ... FOR UPDATE` ordenado por id, así dos compromisos sobre los mismos recursos los bloquean en el mismo orden y no hay deadlock. Se tiene que llamar dentro de un método `@Transactional`. Después, `Recurso.comprometer(n)` / `Recurso.liberar(n)` cambian el stock sobre la fila ya bloqueada; `cantidadComprometida` no tiene setter a propósito.

## Gestión de ONGs, certificaciones y recursos

Todos los endpoints (salvo el registro) requieren sesión: `POST /api/auth/login` deja el JWT en una cookie, que Swagger UI reenvía sola.

| Método y ruta | Quién | Qué hace |
|---|---|---|
| `POST /api/ongs/registro` | público | Crea la ONG (queda `HABILITADA`) y su usuario representante en un solo paso |
| `GET /api/ongs` | cualquier usuario autenticado | ONGs habilitadas |
| `GET /api/ongs/{id}` | ADMIN, SERVICIO; una ONG solo la suya | Detalle de una ONG |
| `GET /api/ongs/{ongId}/certificaciones` | ADMIN, SERVICIO; una ONG solo las suyas | Certificaciones, con `vigente` calculado |
| `POST /api/ongs/{ongId}/certificaciones` | ADMIN | Certifica a la ONG para un tipo de recurso |
| `PUT /api/ongs/{ongId}/certificaciones/{id}` | ADMIN | Cambia nivel, riesgo máximo o vencimiento |
| `DELETE /api/ongs/{ongId}/certificaciones/{id}` | ADMIN | Elimina la certificación |
| `POST /api/certificaciones/validar` | ADMIN, SERVICIO | Capacidades certificadas vigentes por CUIT y tipo de recurso (E3-05) |
| `GET /api/ongs/{ongId}/recursos` | ADMIN, SERVICIO; una ONG solo el suyo | Inventario con total, comprometido y disponible |
| `POST /api/ongs/{ongId}/recursos` | representante de esa ONG | Agrega un recurso |
| `PUT /api/ongs/{ongId}/recursos/{id}` | representante de esa ONG | Cambia el stock total |
| `DELETE /api/ongs/{ongId}/recursos/{id}` | representante de esa ONG | Elimina el recurso |
| `GET /api/recursos/disponibilidad?tipoRecurso=` | cualquier usuario autenticado | Disponibilidad global de ONGs habilitadas |

Reglas:

- Una ONG nunca ve ni modifica datos de otra (403). Las certificaciones las carga solo el ADMIN: si las cargara la ONG se autocertificaría.
- `unidadMedida` es texto libre, pero se guarda en minúsculas y sin espacios de más (`" KG "` y `kg` son la misma unidad). Unificarla con RescueSync sigue pendiente.
- Una certificación vencida no se borra: se informa con `vigente: false`. La fecha de vencimiento, si se manda, no puede ser pasada.
- Un recurso no puede quedar con total menor a lo comprometido, ni borrarse con unidades comprometidas o con historial de compromisos (409).
- Las modificaciones de recursos toman `SELECT ... FOR UPDATE` sobre la fila, así no se pisan con un compromiso en curso.

Para probar a mano en Swagger: registrar una ONG, loguearse con ese usuario y cargar recursos; luego loguearse como `admin@sistemanacional.test` (ver el seed) para certificarla.

## Validación de certificaciones (E3-05)

`POST /api/certificaciones/validar` permite consultar el perfil certificado de una ONG
por su CUIT (11 dígitos, sin guiones) y el tipo de recurso de la oferta. Requiere
sesión de `SERVICIO` (Bonita) o `ADMIN`, con el mismo login JWT que el resto de la API.

```json
{
  "cuit": "30700000001",
  "tipoRecurso": "ALIMENTOS"
}
```

Devuelve `200 OK` con las capacidades habilitadas para ese tipo de recurso:

```json
{
  "cuit": "30700000001",
  "tipoRecurso": "ALIMENTOS",
  "estadoHabilitacion": "HABILITADA",
  "capacidadesHabilitadas": [
    {
      "tipoRecurso": "ALIMENTOS",
      "nivelCompetencia": "INTERMEDIO",
      "nivelRiesgoMax": "MEDIO",
      "fechaVencimiento": null
    }
  ],
  "observaciones": "Se informan el nivel de competencia y el riesgo máximo certificados para el tipo de recurso solicitado"
}
```

- Se informan los niveles certificados (`BASICO`, `INTERMEDIO` o `AVANZADO`) y el
  riesgo máximo permitido, para que la selección pueda considerar tareas principales
  o de apoyo. Este servicio no decide la adjudicación de una oferta.
- Solo se informa capacidad si la ONG está `HABILITADA` y la certificación está
  vigente. La fecha se evalúa en `America/Argentina/Buenos_Aires`: vence al terminar
  el día indicado; `null` significa que no vence.
- Si falta certificación vigente para el tipo solicitado, la ONG no está habilitada
  o el CUIT no está registrado, responde `200` con `capacidadesHabilitadas: []` y el
  motivo en `observaciones`. Para un CUIT no registrado, `estadoHabilitacion` es `null`.
- Los datos de entrada inválidos devuelven `400` en formato `ApiError`; sin sesión,
  `401`; un representante de ONG no tiene acceso a esta operación (`403`).
- Es una consulta de solo lectura: no modifica certificaciones, ofertas ni stock.
  El compromiso/liberación de recursos y los conectores de Bonita corresponden a
  E3-06/07 y E3-09/10.

**Pruebas:** `mvn test` incluye `ValidacionCertificacionApiTests`, con niveles de
competencia y riesgo, vencimiento, ONGs no habilitadas o inexistentes, aislamiento
por ONG y tipo de recurso, permisos, datos inválidos y ausencia de cambios en el stock.

## Liberación de recursos (E3-07)

`POST /api/compromisos/liberar` (ADMIN o SERVICIO) con `{ "emergenciaRef": 12, "ofertaRef": 34 }`. Los ids son los de RescueSync, los mismos que se mandaron al comprometer.

- Con `ofertaRef` libera esa oferta (todas las ONGs de un consorcio). Sin `ofertaRef`, todos los compromisos de la emergencia: es el cierre del proyecto.
- Todo en una transacción: si un compromiso no se puede liberar, no se libera ninguno (409).
- Idempotente: un reintento responde 200 con `liberados: 0` y los `yaLiberados`, sin devolver stock dos veces. Si no hay ningún compromiso para esos ids, 404.
- Concurrencia: primero se bloquean los recursos (`FOR UPDATE`, ordenados por id, igual que al comprometer) y recién después se leen los compromisos, así una liberación simultánea no se cuenta dos veces.

## Compromiso de recursos (E3-06)

`POST /api/compromisos` (SERVICIO) recibe `{ "recursoId": 5, "emergenciaRef": 12, "ofertaRef": 34, "cantidad": 2 }`. Los identificadores de emergencia y oferta son los de RescueSync, y deben coincidir con los enviados luego a liberación.

- Reserva el stock disponible dentro de una transacción y bloquea el recurso con `FOR UPDATE`; si no alcanza el stock, responde 409 sin crear el compromiso.
- Es idempotente por recurso y oferta: si ya existe esa combinación, devuelve el compromiso existente sin reservar stock otra vez, aunque el payload del reintento difiera.
- La liberación por oferta o emergencia consume estos mismos identificadores y devuelve el stock una sola vez.

## Pendiente

- Unidades de medida: hoy texto libre, igual que en los lotes de RescueSync. Ver punto abierto de E3-01.

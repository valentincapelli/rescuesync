# Sistema Nacional de Gestión de Recursos y Riesgos (API)

API en el Cloud que consume Bonita (Entrega 3). Es un servicio aparte del backend de RescueSync, con su **propia base PostgreSQL**. No guarda emergencias, lotes ni ofertas: solo las referencia por id (`emergencia_ref`, `oferta_ref`).

Stack: Java 21 + Spring Boot 3.5.6 + Spring Data JPA + PostgreSQL, igual que `rescuesync-backend`.

## Cómo levantarla

```bash
cd rescuesync-sistema-nacional
docker compose up -d db          # PostgreSQL en localhost:5434
mvn spring-boot:run              # API en http://localhost:8082
```

O todo en Docker, con la misma imagen que se despliega en Render:

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

Todas tienen valor por defecto en `application.yml` (ver `.env.example`).

| Variable | Default | Nota |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5434/sistema_nacional` | En Render, armar la URL JDBC a partir de los datos de la base (Render da `postgres://...`, no JDBC) |
| `DB_USER` / `DB_PASSWORD` | `sistema_nacional` | |
| `SERVER_PORT` | `8082` | Si no está, se usa `PORT` (lo inyecta Render) |
| `JPA_DDL_AUTO` | `update` | Pasar a `validate` + migraciones cuando el modelo se estabilice |
| `CORS_ORIGINS` | `http://localhost:5173` | Solo si el onboarding de ONGs se hace desde un frontend |

## Estructura

```
src/main/java/com/grupo21/sistemanacional/
├── config/       JPA auditing, Swagger, CORS
├── controller/   InfoController (los demás llegan con E3-03 en adelante)
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

## Pendiente

- Services y controllers de catálogo, validación, compromiso y liberación.
- Unidades de medida: hoy texto libre, igual que en los lotes de RescueSync. Ver punto abierto de E3-01.

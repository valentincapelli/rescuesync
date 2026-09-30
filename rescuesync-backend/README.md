# RescueSync – Backend

Backend de la aplicación web de RescueSync (DSSD 2026 – Grupo 21).

**Stack:** Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 16 · Spring Data JPA · springdoc (Swagger)

## Cómo levantarlo

1. **Base de datos** (desde la carpeta `DSSD/`, requiere Docker Desktop):

   ```bash
   docker compose up -d
   ```

   Crea la base `rescuesync` en `localhost:5433` (usuario y contraseña: `rescuesync`).
   Si no usan Docker, creen esa base y ese usuario a mano en su PostgreSQL local.

2. **Backend** (desde `DSSD/rescuesync-backend/`):

   ```bash
   mvn spring-boot:run
   ```

   O desde el IDE: abrir la carpeta `rescuesync-backend` como proyecto Maven y ejecutar `RescueSyncApplication`.

3. **Verificar:**

   | URL | Qué es |
   |---|---|
   | http://localhost:8081/api/info | Ping del backend (el front lo puede usar para chequear conexión) |
   | http://localhost:8081/swagger-ui.html | Documentación Swagger |
   | http://localhost:8081/actuator/health | Health check (incluye estado de la BD) |

**Tests:** `mvn test` (usan H2 en memoria, no necesitan PostgreSQL).

## Puertos

| Servicio | Puerto |
|---|---|
| Bonita Studio / Bonita Runtime | 8080 |
| **Backend (este proyecto)** | **8081** |
| Frontend React (Vite) | 5173 |
| PostgreSQL | 5433 |

## Configuración

Todo está en `src/main/resources/application.yml` y se puede pisar con variables de entorno (ver `.env.example`):
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, `CORS_ORIGINS`, `BONITA_URL`, `BONITA_USER`, `BONITA_PASSWORD`, `BONITA_PROCESS`, `JPA_DDL_AUTO`, `JPA_SHOW_SQL`.

## Estructura (por capas)

```
src/main/java/com/grupo21/rescuesync/
├── RescueSyncApplication.java
├── config/       Configuración: CORS, Swagger, JPA auditing, RestClient, properties de Bonita
├── controller/   Endpoints REST (@RestController). Todas las rutas bajo /api/...
├── service/      Lógica de negocio. Un service por entidad/caso de uso
├── repository/   Interfaces Spring Data JPA
├── model/        Entidades JPA y enums. Todas extienden BaseEntity (id + createdAt/updatedAt)
├── dto/          Records de entrada/salida (XxxRequest / XxxResponse) + ApiError
├── exception/    Excepciones propias + GlobalExceptionHandler
└── client/       Clientes HTTP a sistemas externos (por ahora, Bonita)
```

### Convenciones

- El flujo es siempre **controller → service → repository / client**. Los controllers no acceden a repositorios.
- **Regla de negocio:** el backend nunca se conecta directamente a la API del Sistema Nacional. Bonita actúa como intermediario ante ese sistema; el backend solo integra con Bonita.
- Nunca se devuelven entidades JPA desde un controller: siempre DTOs (`record`).
- Validaciones de entrada con Bean Validation (`@Valid`, `@NotBlank`, `@Positive`, …) en los DTOs de request.
- Errores: lanzar `ResourceNotFoundException` (404), `BusinessException` (409) o `BonitaIntegrationException` (502). El `GlobalExceptionHandler` los convierte a JSON:

  ```json
  { "timestamp": "...", "status": 404, "error": "Not Found", "message": "Emergencia con id 7 no encontrado", "path": "/api/emergencias/7" }
  ```

- Rutas REST en plural y en castellano: `/api/emergencias`, `/api/emergencias/{id}/lotes`, `/api/ofertas`.

## Modelo de datos (E2-03)

Entidades JPA en `model/` (todas extienden `BaseEntity`: `id` + `createdAt`/`updatedAt` auditados):

- **Emergencia**: tipo de desastre, nivel de gravedad, zona afectada, descripción, municipio, estado y `bonitaCaseId` (se completa cuando se inicia la instancia en Bonita, E2-11/12). Tiene muchos `Lote`.
- **Lote**: pertenece a una `Emergencia`. Tipo de recurso, descripción, cantidad requerida, unidad de medida y estado (`PUBLICADO`, `CUBIERTO`, `PARCIALMENTE_CUBIERTO`, `REFORMULADO`, `CERRADO`). Tiene muchas `Oferta`.
- **Oferta**: pertenece a un `Lote`. ONG (por nombre, hasta que exista login de ONGs en E2-10), cantidad ofrecida, observaciones y estado (`PENDIENTE`, `EVALUADA`, `SELECCIONADA`, `RECHAZADA`, `COMPROMETIDA`, `FINALIZADA`). Una ONG que cubre varios lotes carga una oferta por lote.

Enums de dominio también en `model/`: `TipoDesastre`, `NivelGravedad`, `EstadoEmergencia`, `TipoRecurso`, `EstadoLote`, `EstadoOferta` (mapeados como `VARCHAR` con `@Enumerated(STRING)`, no como índice numérico, para que la base sea legible y estable ante cambios de orden).

Repositorios Spring Data en `repository/`: `EmergenciaRepository`, `LoteRepository`, `OfertaRepository`, con query methods básicos (`findByEstado`, `findByEmergenciaId`, `findByLoteId`, etc.).

Con `spring.jpa.hibernate.ddl-auto=update` (default en `application.yml`), al levantar el backend contra el PostgreSQL de `docker-compose.yml` Hibernate crea solo las tablas `emergencias`, `lotes`, `ofertas` con sus FKs. Para verificar:

```bash
docker compose up -d          # desde DSSD/
cd rescuesync-backend
mvn spring-boot:run
# en otra terminal:
docker exec -it rescuesync-postgres psql -U rescuesync -d rescuesync -c "\dt"
```

Cuando el modelo se estabilice conviene pasar a `ddl-auto=validate` + migraciones (Flyway), pero por ahora no hace falta para el alcance de la Entrega 2.

## Alta de emergencias (E2-04)

`POST /api/emergencias` recibe JSON y devuelve `201 Created`, un `EmergenciaResponse`
y el header `Location` con la ruta de consulta de la emergencia creada.
El listado y la consulta por id también devuelven JSON.

```bash
curl -i -X POST http://localhost:8081/api/emergencias \
  -H 'Content-Type: application/json' \
  -d '{
    "tipoDesastre": "INUNDACION",
    "nivelGravedad": "ALTO",
    "zonaAfectada": "Tolosa",
    "descripcion": "Calles anegadas y familias que necesitan asistencia.",
    "municipio": "La Plata"
  }'
```

Los cinco campos son obligatorios. `zonaAfectada` admite hasta 200 caracteres y
`municipio`, hasta 150. Los enums se envían por nombre (`TipoDesastre` y `NivelGravedad`),
nunca por índice numérico; esta validación se aplica también al resto de los endpoints.
El backend asigna el id, las fechas de auditoría y el estado inicial `REGISTRADA`.
`bonitaCaseId` queda en `null` hasta implementar la integración con Bonita.

Los errores mantienen el formato `ApiError`: `400` para datos inválidos (con `fieldErrors`
cuando falla Bean Validation), `404` para una emergencia inexistente, `415` para un
`Content-Type` no soportado y `406` cuando `Accept` no admite JSON.
Los formatos incompatibles se rechazan antes de guardar la emergencia.

**Pruebas:** `mvn test` incluye alta, lectura directa de datos confirmados en H2, consulta,
listado, auditoría, validaciones, protección de campos asignados por el servidor,
errores HTTP y Swagger. También verifica que una emergencia nueva permita crear lotes.

## Integración con Bonita (E2-10)

`client/BonitaClient` se loguea contra el engine de Bonita (`POST /loginservice`)
usando `bonita.username`/`bonita.password` (ver `.env.example`, por defecto el
usuario demo `walter.bates`/`bpm`), y cachea la sesión (cookie `JSESSIONID` +
token anti-CSRF `X-Bonita-API-Token`). Si una llamada devuelve `401` porque la
sesión venció, se reautentica una vez sola y reintenta — no hay que loguearse
en cada request.

Para probar que el backend puede hablar con Bonita (con Bonita Portal/Studio
corriendo en `:8080` y el proceso `RescueSync` desplegado y habilitado):

```bash
curl -i http://localhost:8081/api/bonita/estado
```

- `200` con `{"procesoId": "...", "procesoNombre": "RescueSync"}` si encontró el
  proceso habilitado.
- `502` (`ApiError`) si no pudo autenticarse, o si el proceso no existe/no está
  habilitado — el `message` explica cuál de las dos cosas pasó.

Esto todavía **no** inicia instancias del proceso ni setea variables: eso es
E2-11 (usar el `procesoId` resuelto acá para `POST /API/bpm/process/{id}/instantiation`)
y E2-12 (variables iniciales en el mismo body de esa llamada).

## Próximas tareas que se apoyan en esta base

- **E2-04** ✅ Alta de emergencias: `POST /api/emergencias` (+ `GET /api/emergencias`, `GET /api/emergencias/{id}`) en `EmergenciaController` / `EmergenciaService`, usados por el formulario del frontend.
- **E2-06** ✅ Alta de lotes: `POST /api/emergencias/{emergenciaId}/lotes` en `LoteController` / `LoteService`.
- **E2-08** Ofertas: falta `controller/` + `service/` + `dto/` sobre `Oferta` y `OfertaRepository` (ya existen).
- **E2-10** ✅ Autenticación e integración inicial con Bonita: ver sección arriba (`GET /api/bonita/estado`).
- **E2-11 / E2-12** Iniciar instancia del proceso al crear una emergencia + setear variables iniciales, usando `BonitaClient.buscarIdProceso` y guardando el resultado en `Emergencia.bonitaCaseId`.

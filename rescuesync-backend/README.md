# RescueSync – Backend

Backend de la aplicación web de RescueSync (DSSD 2026 – Grupo 21).

**Stack:** Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 16 · Spring Data JPA · springdoc (Swagger)

## Cómo levantarlo

1. **Base de datos** (desde la carpeta `DSSD/`, requiere Docker Desktop):

   ```bash
   docker compose up -d
   ```

   Crea la base `rescuesync` en `localhost:5432` (usuario y contraseña: `rescuesync`).
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
| PostgreSQL | 5432 |

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

## Próximas tareas que se apoyan en esta base

- **E2-03** Modelo de datos: entidades en `model/` extendiendo `BaseEntity`, repos en `repository/`. Hoy `ddl-auto=update` crea las tablas solo.
- **E2-04 / E2-06 / E2-08** Emergencias, lotes y ofertas: `controller/` + `service/` + `dto/`.
- **E2-10 a E2-12** Integración con Bonita: `client/BonitaClient` usando el bean `bonitaRestClient` y `BonitaProperties`.

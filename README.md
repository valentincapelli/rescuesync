# RescueSync – DSSD 2026 – Grupo 21

| Carpeta | Contenido |
|---|---|
| `rescuesync-backend/` | API REST en Java 21 + Spring Boot (ver su README) |
| `rescuesync-frontend/` | App React 18 + TypeScript + Vite (ver su README) |
| `rescuesync-sistema-nacional/` | API del Sistema Nacional (Cloud) que consume Bonita, con su propia base (ver su README) |
| `docker-compose.yml` | PostgreSQL local para desarrollo |

## Cómo levantar todo

```bash
docker compose up -d                              # PostgreSQL
(cd rescuesync-backend && mvn spring-boot:run)     # backend en :8081
(cd rescuesync-frontend && npm install && npm run dev)  # frontend en :5173
(cd rescuesync-sistema-nacional && docker compose up -d db && mvn spring-boot:run)  # Sistema Nacional en :8082
```

Detalle de cada parte en su propio README.

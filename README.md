# RescueSync – DSSD 2026 – Grupo 21

| Carpeta | Contenido |
|---|---|
| `rescuesync-backend/` | API REST en Java 21 + Spring Boot (ver su README) |
| `rescuesync-frontend/` | App React 18 + TypeScript + Vite (ver su README) |
| `rescuesync-sistema-nacional/` | API del Sistema Nacional (Cloud) que consume Bonita, con su propia base (ver su README) |
| `docker-compose.yml` | PostgreSQL local para desarrollo |

## Integración E3-08 / E3-09

La [guía de entrega y continuidad](docs/e3-08-e3-09-continuidad.md) describe el endpoint de ofertas consolidadas, el conector de Bonita, la configuración segura, las pruebas y la exportación actualizada del proceso.

## Dashboard de indicadores (E4)

El [contrato de indicadores](docs/e4-indicadores-contrato.md) define los endpoints `/api/indicadores`, la fórmula de cada métrica, el historial de estados y el reparto de trabajo entre frentes.

## Estado de E4

- [Relevamiento y ajustes pendientes](docs/e4-01-ajustes-pendientes.md): qué falta del proceso, decisiones del equipo y lista de ajustes.
- [Prueba de punta a punta de E3-10](docs/e3-10-prueba-integracion.md): RescueSync 1.2 ejecutado en Bonita con el backend y el Sistema Nacional; resultados y problemas encontrados.

## Cómo levantar todo

```bash
docker compose up -d                              # PostgreSQL
(cd rescuesync-backend && mvn spring-boot:run)     # backend en :8081
(cd rescuesync-frontend && npm install && npm run dev)  # frontend en :5173
(cd rescuesync-sistema-nacional && docker compose up -d db && mvn spring-boot:run)  # Sistema Nacional en :8082
```

Detalle de cada parte en su propio README.

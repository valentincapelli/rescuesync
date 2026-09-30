# Plan – Generación de lotes y publicación de convocatoria (E2-06 / E2-07)

> Grupo 21 · RescueSync · Entrega 2 · actualizado 30/09/2026

## 1. Qué pide la consigna

> "Un Centro Coordinador Regional recibe la emergencia, revisa la información y la desglosa en
> 'Lotes de Necesidades' (ej: 5 Paramédicos, 1000 raciones de alimento). Una vez cuantificadas,
> se publica oficialmente la convocatoria a toda la red."

Esto separa tres momentos distintos, que también aparecen en nuestro BPMN
(`Registrar emergencia` → `Revisar y desglosar emergencia` → `Publicar convocatoria`):

1. **Recibir / revisar**: el CCR toma una emergencia registrada por el Municipio.
2. **Desglosar**: el CCR carga los lotes (tipo de recurso + cantidad + unidad). Mientras
   desglosa, puede corregir o quitar lotes.
3. **Publicar**: un acto explícito que abre la convocatoria. Recién ahí las ONGs pueden ofertar.

## 2. Estado actual del código (rama `development`, commit `d3c7de3`)

| Qué | Estado |
|---|---|
| `POST /api/emergencias` → emergencia en `REGISTRADA` | ✅ |
| `POST /api/emergencias/{id}/lotes` → lote creado directamente en `PUBLICADO` | ✅ (ver brecha 1) |
| `GET /api/emergencias/{id}/lotes` | ✅ **nuevo** (rama `feature/listar-lotes`) |
| `POST/PUT /api/lotes/{loteId}/ofertas` | ✅ |
| Transición de la emergencia a `EN_REVISION` / `CONVOCATORIA_PUBLICADA` | ✅ (E2-07, ver §8) |
| Formulario de lotes en el front (E2-07) | ✅ (ver §8) |

### Brechas respecto de la consigna

1. **No hay "publicación".** Hoy un lote queda `PUBLICADO` en cuanto se crea, así que una ONG
   puede ofertar sobre un lote que el CCR todavía está armando.
2. **La emergencia no cambia de estado.** Queda en `REGISTRADA` para siempre; los estados
   `EN_REVISION` y `CONVOCATORIA_PUBLICADA` existen en el enum pero nadie los usa.
3. **No se valida el estado al crear lotes ni ofertas.** Se pueden agregar lotes a una emergencia
   con la convocatoria ya publicada, y ofertas a lotes de una emergencia sin publicar.
4. **No se pueden corregir lotes** (no hay `PUT`/`DELETE`) durante el desglose.

## 3. Decisiones ya tomadas

| # | Decisión | Dónde |
|---|---|---|
| D1 | Stack: Java 21 + Spring Boot 3.5 + Maven, React 18 + TS + Vite, PostgreSQL 16 en Docker | E2-01/E2-02 |
| D2 | Backend organizado por capas; controller → service → repository; siempre DTOs `record` | E2-02 |
| D3 | El backend **nunca** llama al Sistema Nacional; solo integra con Bonita | README backend |
| D4 | Entidades `Emergencia` 1—N `Lote` 1—N `Oferta`; enums guardados como `VARCHAR` y enviados por nombre | E2-03 |
| D5 | Estados de emergencia: `REGISTRADA → EN_REVISION → CONVOCATORIA_PUBLICADA → …` | `EstadoEmergencia` |
| D6 | Un lote = un tipo de recurso con cantidad y unidad (ej. 5 "personas", 1000 "raciones") | `Lote` |
| D7 | Rutas anidadas: lotes bajo `/emergencias/{id}/lotes`, ofertas bajo `/lotes/{id}/ofertas` | E2-06/E2-08 |
| D8 | Una ONG carga **una oferta por lote** (409 si repite); la ONG se identifica por nombre hasta E2-10 | E2-08 |
| D9 | Las ofertas parciales/consorcios y versiones de ofertas se resuelven en app + BD, no en Bonita | Diagnóstico BPMN |
| D10 | `GET /api/emergencias/{id}/lotes`: lista ordenada por id, `[]` si no hay lotes, 404 si la emergencia no existe | este plan |

## 4. Propuesta de flujo

```
Municipio                CCR                                            ONGs
─────────                ───                                            ────
POST /emergencias  ──►  REGISTRADA
                         │  (1) tomar para revisión
                         ▼
                        EN_REVISION ── POST/PUT/DELETE lotes (BORRADOR)
                         │  (2) POST /emergencias/{id}/convocatoria
                         ▼
                        CONVOCATORIA_PUBLICADA ── lotes pasan a PUBLICADO ──►  POST /lotes/{id}/ofertas
```

Reglas propuestas:

- Se pueden **crear/editar/borrar lotes** solo si la emergencia está en `REGISTRADA` o `EN_REVISION` (409 si no).
- **Publicar** exige al menos un lote; pasa la emergencia a `CONVOCATORIA_PUBLICADA` y todos sus
  lotes a `PUBLICADO` en una sola transacción. Publicar dos veces → 409.
- Se pueden **cargar/editar ofertas** solo sobre lotes `PUBLICADO` (409 si no).

## 5. Decisiones pendientes (a resolver en grupo)

| # | Pregunta | Opciones | Recomendación |
|---|---|---|---|
| P1 | ¿En qué estado nace un lote? | a) agregar `BORRADOR` a `EstadoLote` · b) dejar `PUBLICADO` y controlar solo con el estado de la emergencia | **a**: el estado del lote refleja la realidad y la regla de ofertas queda simple ("solo lotes `PUBLICADO`") |
| P2 | ¿Cómo pasa la emergencia a `EN_REVISION`? | a) endpoint explícito `POST /emergencias/{id}/revision` · b) automático al cargar el primer lote | **b** para esta entrega (menos pantallas); **a** si se quiere mapear 1:1 con la tarea humana de Bonita |
| P3 | ¿Cómo se modela "publicar"? | a) `POST /emergencias/{id}/convocatoria` · b) `PATCH /emergencias/{id}` con `estado` | **a**: es una acción de negocio con validaciones, no un update genérico |
| P4 | ¿Se permite editar/borrar lotes antes de publicar? | sí (`PUT`/`DELETE`) · no | **sí**, la consigna habla de "revisar" y "cuantificar" antes de publicar |
| P5 | ¿Se puede agregar un lote después de publicar? | no · sí, con nueva publicación | **no** en E2; la reformulación (`REFORMULADO`) corresponde a "Resolver cobertura incompleta", etapa posterior |
| P6 | ¿Qué pasa con Bonita al publicar? | a) iniciar el caso al registrar la emergencia y completar la tarea "Publicar convocatoria" al publicar · b) iniciar el caso recién al publicar | Definir junto con E2-10..E2-12; **a** respeta el BPMN (el caso arranca en "Registrar emergencia") |
| P7 | ¿Duración de la convocatoria (Timer)? | fija en Bonita · la carga el CCR al publicar y se envía como variable | Si la carga el CCR, agregar `fechaCierreConvocatoria` a `Emergencia` y enviarla en E2-12 |
| P8 | ¿Quién es "el CCR" en la UI sin login? | pantalla separada · misma app con secciones por rol | Secciones por rol hasta E2-10 |
| P9 | ¿Se valida la unidad de medida? | texto libre (actual) · lista fija por `TipoRecurso` | Texto libre por ahora; lista fija si sobra tiempo |

## 6. Plan de trabajo

| Paso | Tarea | Grooming | Depende de |
|---|---|---|---|
| 1 | `GET /api/emergencias/{id}/lotes` + tests | E2-06 | ✅ hecho |
| 2 | Cerrar P1–P5 en grupo | — | — |
| 3 | Back: `BORRADOR` en `EstadoLote`; lote nace en `BORRADOR`; validar estado de la emergencia al crear lotes; pasar a `EN_REVISION` | E2-06 | P1, P2 |
| 4 | Back: `PUT`/`DELETE /api/emergencias/{id}/lotes/{loteId}` | E2-06 | P4 |
| 5 | Back: `POST /api/emergencias/{id}/convocatoria` (valida ≥1 lote, cambia estados) + tests | E2-06 | P3 |
| 6 | Back: `OfertaService` rechaza lotes no `PUBLICADO`; verificar que `ofertaId` pertenezca a `loteId` en el `PUT` | E2-08 | paso 3 |
| 7 | Back: `GET /api/lotes?estado=PUBLICADO` (o por emergencia) para que el formulario de ONGs elija lote | E2-09 | paso 5 |
| 8 | Front: `types/lote.ts`, `api/lotes.ts` | E2-07 | pasos 1, 3–5 |
| 9 | Front: página "Desglosar emergencia" — elegir emergencia (`GET /emergencias`), tabla de lotes, formulario de alta, editar/borrar, botón **Publicar convocatoria** con confirmación | E2-07 | paso 8 |
| 10 | Conectar "Publicar" con Bonita (completar tarea / setear variables) | E2-11/E2-12 | P6, P7 |
| 11 | Actualizar README backend/frontend y la duración real en el grooming | E2-14 | — |

## 7. Cambios de este commit (`feature/listar-lotes`)

- `LoteRepository.findByEmergenciaIdOrderByIdAsc`
- `LoteService.listarPorEmergencia` (404 si la emergencia no existe, lectura `readOnly`)
- `GET /api/emergencias/{emergenciaId}/lotes` en `LoteController`
- `LoteIntegrationTests`: lista vacía, orden de creación, no mezcla emergencias, 404
- ⚠️ No se pudo correr `mvn test` en la nube (Maven Central bloqueado): **correrlo en local antes de mergear**.

## 8. E2-07 – Interfaz/formulario de lotes (hecho)

Se tomaron las recomendaciones **P1a, P2b, P3a, P4 sí, P5 no, P8 secciones por rol, P9 texto libre**.
P6/P7 (Bonita y duración del timer) quedan para E2-10..E2-12.

**Backend**
- `EstadoLote.BORRADOR`: el lote nace en `BORRADOR`; al crear el primero, la emergencia pasa de `REGISTRADA` a `EN_REVISION`.
- `PUT` y `DELETE /api/emergencias/{id}/lotes/{loteId}` (404 si el lote es de otra emergencia).
- Crear/editar/borrar lotes solo con la emergencia en `REGISTRADA`/`EN_REVISION` (409 si no).
- `POST /api/emergencias/{id}/convocatoria`: exige ≥1 lote, pasa lotes a `PUBLICADO` y la emergencia a `CONVOCATORIA_PUBLICADA` (409 sin lotes o si ya estaba publicada).
- `OfertaService`: solo acepta crear/editar ofertas sobre lotes `PUBLICADO` (409 si no). Se agregó `@Transactional` a `crear`/`editar`.
- `LoteIntegrationTests` (listado, desglose, publicación, ofertas según estado). `EmergenciaIntegrationTests` ahora espera `BORRADOR`.
- ⚠️ **Base local existente**: Hibernate crea un `CHECK` sobre la columna `lotes.estado` con los valores del enum y `ddl-auto=update` no lo actualiza. Si al crear un lote falla por el constraint, recrear la base (`docker compose down -v && docker compose up -d`) o borrar el constraint `lotes_estado_check`.

**Frontend**
- Pestañas por rol en `App.tsx`: *Municipio · Registrar emergencia* / *Centro Coordinador · Lotes*.
- `pages/LotesPage.tsx`: elegir emergencia, resumen, tabla de lotes con editar/borrar, formulario (`components/LoteForm.tsx`) y botón **Publicar convocatoria** con confirmación; después de publicar, vista de solo lectura.
- `types/lote.ts`, `api/lotes.ts`, `apiPut`/`apiDelete` en `api/client.ts`, `listarEmergencias`/`publicarConvocatoria` en `api/emergencias.ts`.

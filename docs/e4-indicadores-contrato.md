# E4 – Dashboard de indicadores: contrato y reparto de trabajo

Desarrollado en `feature/E4-indicadores-base` e integrado en `development` el 18/11/2026. Las decisiones del equipo sobre E4 están en [`e4-01-ajustes-pendientes.md`](e4-01-ajustes-pendientes.md), donde D2 y D3 reflejan esta implementación. Este documento es la referencia para todos los frentes de trabajo (personas o agentes). **Si algo de acá cambia, se cambia primero acá y en los DTOs, y se avisa a los demás frentes.**

## 0. Estado actual

Los frentes A, B y C están implementados y mergeados en `feature/E4-indicadores-base`, junto con la integración: exportación CSV/XLSX, timeouts de Bonita, seed de demo y documentación en `rescuesync-backend/README.md`. Probado de punta a punta el 18/11/2026 con PostgreSQL 16 y Bonita 10.4: los seeds corren y son idempotentes, el dashboard da los valores esperados (sección 6), CSV y XLSX se descargan bien, y los tiempos por etapa salen de Bonita con `archivedFlowNode` (no `archivedActivity`, que trae solo el estado final). El camino de un caso terminado (`archivedCase` con `start` y `end_date`) quedó verificado en la prueba de E3-10 (ver [`e3-10-prueba-integracion.md`](e3-10-prueba-integracion.md)). Con datos reales, los indicadores propios siguen vacíos para lo posterior a la convocatoria hasta que el backend refleje los resultados del proceso (ajuste 8 de E4-01). El resto de este documento es el contrato original; donde algo cambió, está marcado.

## 1. Piezas y ubicación

| Pieza | Ubicación |
|---|---|
| Rol `DIRECTIVO` con permiso `CONSULTAR_INDICADORES` | `model/Rol.java`, `model/Permiso.java` |
| Script para bases existentes | `scripts/usuarios_rol_directivo.sql` (incluye todos los roles) |
| Usuario demo `direccion@test.com` / `123456` | `scripts/seed-backend-demo.sql` |
| Historial de estados | `model/HistorialEstado.java`, `repository/HistorialEstadoRepository.java`, `service/HistorialEstadoService.java` |
| Etapas de la emergencia ↔ tareas de Bonita | `model/EtapaEmergencia.java` |
| DTOs del contrato | `dto/indicadores/*.java` |
| Endpoints | `controller/IndicadorController.java` |
| Fachada que combina todo | `service/indicadores/IndicadorService.java` |
| Indicadores propios (frente A) | `service/indicadores/IndicadoresPropiosService.java`, `repository/IndicadoresQueryRepository.java`, tests en `IndicadoresPropiosServiceTests` |
| Tiempos de Bonita (frente B) | `service/indicadores/TiemposBonitaService.java`, `client/BonitaClient.java` (lecturas), `client/FechasBonita.java`, tests en `TiemposBonitaServiceTests` y `BonitaClientLecturasTests` |
| Exportación CSV/XLSX | `service/indicadores/ReporteTabular.java`, `service/indicadores/ExportadorReporte.java`, tests en `ExportadorReporteTests` |
| Consulta de casos de Bonita por filtro | `EmergenciaRepository.buscarCasosBonita(...)` |
| Front (frente C) | `rescuesync-frontend/src/pages/IndicadoresPage.tsx`, `src/components/indicadores/`, `src/mocks/indicadores.ts` |
| Tipos y cliente del front | `rescuesync-frontend/src/types/indicadores.ts`, `src/api/indicadores.ts` |
| Tests del contrato | `IndicadoresContratoIntegrationTests` (16 tests) |
| Seed de demo | `scripts/seed-indicadores-demo.sql` |

Rutas Java relativas a `rescuesync-backend/src/main/java/com/grupo21/rescuesync/`.

## 2. Endpoints

Todos `GET`, bajo `/api/indicadores`, requieren sesión con `CONSULTAR_INDICADORES` (401 sin sesión, 403 con otro rol). Responden JSON en el formato de los records de `dto/indicadores`; los errores, en `ApiError`.

| Ruta | Respuesta | Fuente |
|---|---|---|
| `/resumen` | `ResumenIndicadoresResponse` | propios + Bonita |
| `/suministros` | `SuministrosMovilizadosResponse` | propios |
| `/consorcios` | `ResolucionConsorciosResponse` | propios |
| `/proyectos` | `EstadoProyectosResponse` | propios |
| `/tiempos-etapas` | `TiemposEtapasResponse` | Bonita |
| `/reporte?formato=json\|csv\|xlsx` | `ReporteIndicadoresResponse` (json) o archivo | todo |

`csv` y `xlsx` se descargan como adjunto `indicadores_<desde>_<hasta>.<ext>`. El CSV está pensado para Excel en configuración regional de Argentina: separador `;`, coma decimal, UTF-8 con BOM, CRLF, una sección por bloque (`# Título`, encabezado, filas, línea vacía). El XLSX trae una hoja por bloque, con números como celdas numéricas. Los textos que empiezan con `= + - @` se prefijan con `'` para que Excel no los tome como fórmulas. Formato desconocido: 400.

### Filtros (query params, todos opcionales) – `FiltroIndicadores`

| Param | Formato | Default | Aplica a |
|---|---|---|---|
| `desde` | `yyyy-MM-dd`, inclusive | 1er día del mes, 11 meses antes de `hasta` | todo |
| `hasta` | `yyyy-MM-dd`, inclusive | hoy (Argentina) | todo |
| `municipioId` | número | sin filtro | todo |
| `tipoDesastre` | enum `TipoDesastre` | sin filtro | todo |
| `tipoRecurso` | enum `TipoRecurso` | sin filtro | **solo** suministros y consorcios |

- Días calendario en `America/Argentina/Buenos_Aires`. Los servicios usan `filtro.inicio()` (inclusive) y `filtro.finExclusivo()` como `Instant`.
- `desde > hasta`, fecha mal formada o enum inexistente: 400.
- Los servicios siempre reciben el filtro **ya normalizado** (`desde`/`hasta` nunca null). La normalización la hace `IndicadorService`.

### Convenciones de valores

- Porcentajes en escala **0–100**, redondeados a 1 decimal. `null` si el denominador es 0 (no es lo mismo que 0 %).
- Duraciones en **horas** decimales, redondeadas a 1 decimal.
- `Instant` se serializa en ISO-8601; `LocalDate` como `yyyy-MM-dd`; mes como `yyyy-MM`.
- Listas que el contrato dice "siempre completas" (`porEstado`: 8 estados; `etapas`: 6 etapas) van en orden del enum, aunque estén en 0.

## 3. Definición de cada indicador

### 3.1 Suministros movilizados (`/suministros`)

- **Oferta movilizada:** su estado actual es `COMPROMETIDA` o `FINALIZADA`, **y** tiene en `historial_estados` un cambio a `COMPROMETIDA` (tipo `OFERTA`) con `fecha_cambio` dentro del período. Si hay varios, cuenta el primero.
- **Mes:** el de ese cambio a `COMPROMETIDA`, en hora de Argentina.
- `cantidad` = suma de `cantidadOfrecida`; `ofertas` = cantidad de ofertas.
- Se agrupa por `(mes, lote.tipoRecurso, lote.unidadMedida)`. Las unidades no se suman entre sí. Si una unidad difiere solo en mayúsculas o espacios, se normaliza con `trim().toLowerCase()`.
- Filtros: `municipioId` y `tipoDesastre` de la emergencia; `tipoRecurso` del lote.
- `series` sin filas en cero, ordenadas por mes y luego por tipo (orden del enum). `totales` agrupa todo el período por `(tipoRecurso, unidadMedida)`.

### 3.2 Resolución mediante consorcios (`/consorcios`)

- **Lote resuelto:** tiene un cambio a `CUBIERTO` (tipo `LOTE`) en `historial_estados` dentro del período, su estado actual es `CUBIERTO` o `CERRADO`, y tiene al menos una oferta en estado `SELECCIONADA`, `COMPROMETIDA` o `FINALIZADA` (**oferta adjudicada**).
- **Por consorcio:** las ofertas adjudicadas del lote son de **2 o más ONGs distintas**. Si es 1 sola, cuenta como `lotesPorOngUnica`.
- `porcentajeConsorcio` = `lotesPorConsorcio / lotesResueltos * 100`.
- `promedioOngsPorConsorcio` = promedio de ONGs distintas en los lotes resueltos por consorcio.
- `porTipoRecurso`: solo tipos con al menos un lote resuelto.
- Filtros: igual que suministros.

### 3.3 Estado de proyectos (`/proyectos`)

- **Proyecto** = emergencia con `createdAt` dentro del período (más `municipioId` y `tipoDesastre`; `tipoRecurso` se ignora).
- **Finalizado** = estado actual `CERRADA`. `enCurso` = `total - finalizados`.
- `porEstado`: los 8 valores de `EstadoEmergencia`, incluso en 0.
- `proyectos`: una fila por emergencia, por `fechaRegistro` descendente.
  - `fechaCierre`: el primer cambio a `CERRADA` en `historial_estados`; null si no está cerrada.
  - `porcentajeCobertura` = Σ min(adjudicado del lote, requerido del lote) / Σ requerido * 100, donde "adjudicado" es la suma de `cantidadOfrecida` de sus ofertas adjudicadas. El tope se aplica **por lote**, para que un lote sobrecubierto no tape a otro sin cubrir. null si no tiene lotes.

### 3.4 Tiempos por etapa (`/tiempos-etapas`, Bonita)

- **Casos:** los que devuelve `EmergenciaRepository.buscarCasosBonita(...)`. La fachada se los pasa a `TiemposBonitaService.tiemposEtapas(casos)`.
- **Duración de una tarea:** del momento en que la tarea quedó disponible al momento en que se completó, según los registros archivados de Bonita.
- **Duración de una etapa en un caso:** suma de las duraciones de sus tareas completadas (agrupadas con `EtapaEmergencia.deTarea(nombre)`). Las tareas que no están en ninguna etapa se ignoran.
- Por etapa: `muestras` (casos con al menos una tarea completada de esa etapa), y promedio, mediana, mínimo y máximo en horas.
- `casosFinalizados`: casos archivados (terminados). `duracionPromedioTotalHoras`: promedio de `end_date - start` de esos casos.
- **Bonita caído o con error:** **no** se lanza excepción. Se devuelve `TiemposBonitaService.sinDatos(EstadoFuenteBonita.noDisponible(motivo), casos.size())`. El resto del dashboard sigue funcionando.
- `mensaje` para el usuario, sin detalles técnicos ni credenciales.

### 3.5 Resumen (`/resumen`)

Ya implementado en `IndicadorService.armarResumen`: toma `total`, `enCurso`, `finalizados` y `porcentajeFinalizados` de proyectos; `lotesResueltos` y `porcentajeConsorcio` de consorcios; `duracionPromedioTotalHoras` y `bonita` de tiempos.

## 4. Historial de estados – regla para todo el equipo

Los indicadores 3.1, 3.2 y la `fechaCierre` dependen de `historial_estados`. **Nadie llama a `setEstado(...)` en `Emergencia`, `Lote` u `Oferta`** fuera del alta:

- Entidad ya guardada: `historialEstadoService.cambiarEstado(entidad, nuevoEstado)`.
- Alta: `setEstado(inicial)` + `save` + `historialEstadoService.registrarAlta(guardada)`.

El servicio exige una transacción abierta (`Propagation.MANDATORY`). Los datos cargados con SQL directo (seeds) tienen que insertar sus propias filas en `historial_estados`.

Hoy el backend solo genera las transiciones `REGISTRADA`, `EN_REVISION`, `CONVOCATORIA_PUBLICADA`, `BORRADOR`, `PUBLICADO` y `PENDIENTE`. Los estados que usan los indicadores (`CUBIERTO`, `SELECCIONADA`, `COMPROMETIDA`, `FINALIZADA`, `CERRADA`) los tiene que producir el resto del flujo de E3. Hasta entonces, la demo se apoya en el seed de la sección 6.

## 5. Frentes de trabajo y archivos de cada uno

Cada frente trabaja en su propia rama (o worktree) a partir de `feature/E4-indicadores-base`. **Los archivos que no son de un frente no se tocan; si hace falta, se avisa.**

### Frente A – Indicadores propios (backend)

- **Implementa:** `IndicadoresPropiosService` (3.1, 3.2, 3.3) sin cambiar sus firmas públicas.
- **Puede crear:** repositorios o queries nuevas (preferir un `repository/IndicadoresQueryRepository` con `EntityManager`/JPQL), clases auxiliares en `service/indicadores/`, y `src/test/.../IndicadoresPropiosServiceTests.java`.
- **Puede agregar** métodos al final de `OfertaRepository`, `LoteRepository` o `HistorialEstadoRepository`.
- **No toca:** DTOs, controller, `IndicadorService`, `TiemposBonitaService`, `BonitaClient`, `pom.xml`, `application.yml`, front.
- **Tests:** con H2, insertando con `JdbcTemplate` (incluido `historial_estados` con fechas controladas), como en `OfertasConsolidadasIntegrationTests`. Casos borde: lote sin ofertas, consorcio de una sola ONG, mes sin movimientos, cambios fuera del período, unidades con mayúsculas distintas, cada filtro.

### Frente B – Tiempos de Bonita (backend)

- **Implementa:** `TiemposBonitaService.tiemposEtapas(casos)` (3.4) sin cambiar su firma.
- **Agrega a `BonitaClient`** métodos de solo lectura, reutilizando la sesión y el reintento existentes:
  - `GET /API/bpm/archivedCase?f=sourceObjectId={caseId}` → `start`, `end_date`.
  - `GET /API/bpm/case/{caseId}` → `start` del caso activo.
  - `GET /API/bpm/archivedActivity?f=caseId={caseId}&p=0&c=...` (paginado) → `name`, `type`, `state`, `sourceObjectId`, `reached_state_date`, `archivedDate`.
  - **Los nombres de los campos y filtros hay que verificarlos contra Bonita 10.4** (Runtime real o documentación). El código y los tests no pueden suponerlos sin dejarlo indicado.
- **Caché:** por caso, con TTL configurable (`bonita.indicadores.cache-ttl`, default 5 min). Los casos archivados se pueden cachear más tiempo porque no cambian. Si agrega Caffeine, es el **único** frente que toca `pom.xml` y `application.yml`.
- **Puede crear:** DTOs internos de Bonita en `client/`, clases en `service/indicadores/` y `src/test/.../TiemposBonitaServiceTests.java` con `BonitaClient` mockeado (`@MockitoBean` o un test unitario sin Spring).
- **No toca:** DTOs del contrato, controller, `IndicadorService`, `IndicadoresPropiosService`, front.
- **Tests:** caso completo, caso en curso (tareas sin completar), tareas fuera de toda etapa, varios casos (mediana con número par e impar), Bonita caído (`BonitaIntegrationException` o `RestClientException`) que devuelve `disponible=false` y no lanza excepción, lista de casos vacía (sin llamar a Bonita).

### Frente C – Frontend

- **Implementa:** `src/pages/IndicadoresPage.tsx` (+ `.css`) y componentes en `src/components/indicadores/`.
- **Agrega:** `recharts` a `package.json`. Es el **único** frente que toca `package.json` y `package-lock.json`.
- **Integra en `App.tsx`:** sección `{ id: 'direccion', label: 'Dirección · Indicadores', rol: 'DIRECTIVO' }` y caso `DIRECTIVO` en `getSeccionInicial`.
- **Usa** solo `src/api/indicadores.ts` y `src/types/indicadores.ts`. **No cambia los tipos**; si falta algo, lo avisa.
- **Mientras el back está en stub:** `src/mocks/indicadores.ts` con datos realistas que cumplan los tipos, y un interruptor `VITE_INDICADORES_MOCK=true` para usarlos.
- **Contenido:**
  - Filtros: rango de fechas, municipio, tipo de desastre y tipo de recurso. Este último aclara que solo afecta suministros y consorcios.
  - Fila de KPIs del resumen. `null` se muestra como "Sin datos".
  - Barras apiladas de suministros por mes, una serie por tipo de recurso, con selector de unidad porque no se mezclan unidades.
  - Dona o barras de proyectos por estado.
  - Barras de consorcios por tipo de recurso.
  - Barras horizontales de tiempo promedio por etapa, con mínimo y máximo en el tooltip y el número de muestras.
  - Tabla de proyectos ordenable. Hacer clic en un estado del gráfico filtra la tabla.
  - Aviso visible cuando `bonita.disponible === false`, con `mensaje`.
  - Botones Exportar JSON, CSV y Excel con `descargarReporte`. (Ya no hay 501: cualquier error de la descarga se muestra con su mensaje.)
  - Estados de carga, error y vacío.
- **No toca:** nada del backend.
- **Verificación:** `npm run build` sin errores.

### Integración (después de A, B y C)

1. ✅ Merge de A, B y C sobre la base; la suite queda igual que antes más los tests nuevos.
2. ✅ Exportación CSV y XLSX (Apache POI 5.4.1). Se sacó `FuncionalidadPendienteException`.
3. ✅ Timeouts en el `RestClient` de Bonita: `bonita.connect-timeout` (5 s) y `bonita.read-timeout` (15 s).
4. ✅ Seed de demo (sección 6), ejecutado contra PostgreSQL 16.
5. ✅ Documentación en `rescuesync-backend/README.md`.
6. ✅ Prueba de punta a punta con Bonita 10.4, incluido un caso terminado (prueba de E3-10).

## 6. Seed de demo

`scripts/seed-indicadores-demo.sql`, sobre el seed base: 10 emergencias repartidas en los últimos ~6 meses, en todos los tramos del flujo (3 `CERRADA`, 2 `EN_EJECUCION`, 1 `ADJUDICADA`, 1 `COBERTURA_PARCIAL`, 1 `CONVOCATORIA_PUBLICADA`, 1 `EN_REVISION`, 1 `REGISTRADA`). Incluye lotes cubiertos por 1, 2 y 3 ONGs, ofertas comprometidas, finalizadas, seleccionadas, pendientes y rechazadas, y **todas sus filas de `historial_estados`**. Las fechas son relativas al momento de ejecución. Es idempotente: borra solo lo que cargó antes (descripción con prefijo `[DEMO-E4]`). No trae casos de Bonita.

Valores esperados con el filtro por defecto: 10 proyectos con 3 finalizados (30 %), y 11 lotes resueltos de los cuales 5 por consorcio (45,5 %).

## 7. Estado conocido de la suite

`mvn -o test`: **117/117** después de integrar los arreglos de tests de `development` (`775e87a`). De E4: `IndicadoresContratoIntegrationTests` (16), `IndicadoresPropiosServiceTests` (4), `TiemposBonitaServiceTests` (17), `BonitaClientLecturasTests` (5), `BonitaRestClientConfigTests` (2), `ExportadorReporteTests` (3) y `MunicipiosIntegrationTests` (2).

# E4-01 — Relevamiento y ajustes pendientes

Fecha del relevamiento: 2026-11-18. Base: `development` con `feature/bonitaapi` (E3-10) mergeada localmente.

Las columnas **Tarea E4** y **Responsable** quedan para completar en la reunión: el relevamiento no tiene los títulos de E4-02 a E4-10. Las estimaciones son una primera propuesta.

## 1. Terreno

| Punto | Estado |
| --- | --- |
| `development` tiene E3-11 | Sí (`5bf2021`). |
| E3-10 | Estaba solo en `origin/feature/bonitaapi` (`3cf2123`), sin mergear. Merge limpio sin conflictos, hecho **en local**; falta push. |
| Tests del backend | Arreglados y subidos (`775e87a`): 68/68 pasan. |
| Sistema Nacional en Render | **Sin verificar.** La URL pública no figura en el repo. Falta probar `/actuator/health`, `/api/info` y `/swagger-ui.html`. |

## 2. Proceso BPMN contra el código

El diagrama (`RescueSync 1.2`) tiene 4 lanes: Municipio, Centro Coordinador, ONG y Sistema Nacional.

| Actividad | Bonita | Backend | Frontend |
| --- | --- | --- | --- |
| Registrar emergencia | Tarea humana; el backend la completa al registrar | `POST /api/emergencias` | `AltaEmergenciaPage` |
| Revisar y desglosar emergencia | Tarea humana; el backend la completa al publicar | `POST/PUT/DELETE .../lotes` | `LotesPage` |
| Publicar convocatoria | Tarea humana; el backend la completa | `POST /emergencias/{id}/convocatoria` | `LotesPage` |
| Esperar / cargar / editar ofertas | Tarea humana (ONG) | `POST/PUT /lotes/{id}/ofertas` | `OfertasPage` |
| Período convocatorio → Evaluar cobertura de lotes | Timer + conector de ofertas consolidadas (E3-09) | `GET .../ofertas-consolidadas` | No aplica |
| Resolver cobertura incompleta | Tarea humana | **No existe.** `EstadoLote.REFORMULADO` sin uso | **No existe** |
| Evaluar ofertas | Service task + conector SN `EVALUAR` | No guarda el resultado (`EstadoOferta.EVALUADA` sin uso) | No aplica |
| **Seleccionar oferta** | Tarea humana con contrato (`ofertaSeleccionadaIdInput`, `continuarSeleccionandoInput`) | **No existe** (`SELECCIONADA`/`RECHAZADA` sin uso) | **No existe** |
| **Notificar a ONG** | Tarea humana, sin lógica | **No existe** | **No existe** |
| Recibir notificación (ONG) | Tarea humana, sin lógica | **No existe** | **No existe** |
| Comprometer recursos | Service task + conector SN `COMPROMETER` | No actualiza estados (`COMPROMETIDA`, `EN_EJECUCION` sin uso) | No aplica |
| **Monitorear despliegue** | Tarea humana, sin lógica | **No existe** | **No existe** |
| **Finalizar actividad** | Tarea humana, sin lógica | **No existe** (`FINALIZADA`, `CERRADA` sin uso) | **No existe** |
| Liberar recursos | Service task + conector SN `LIBERAR` | No actualiza estados | No aplica |

Hay además un **dashboard de indicadores**, que está en las decisiones abiertas y no existe en ningún lado.

Conclusión: de las 14 actividades solo está de punta a punta lo que va hasta publicar convocatoria y cargar ofertas, más el conector de ofertas consolidadas. Las tres service tasks del Sistema Nacional existen en Bonita con conector, pero nadie las alimenta ni refleja su resultado en el backend. Las 4 tareas en negrita no tienen backend ni frontend. Los estados `EVALUADA`, `SELECCIONADA`, `RECHAZADA`, `COMPROMETIDA`, `FINALIZADA`, `ADJUDICADA`, `EN_EJECUCION` y `CERRADA` están definidos pero nada los usa.

## 3. Integraciones

| Integración | Estado | Ajuste |
| --- | --- | --- |
| Backend → Bonita | Inicia instancia y completa 3 tareas. Si falla la sincronización, solo loguea un warning y la emergencia queda desincronizada. | Decidir si se reintenta o se marca la emergencia como desincronizada. |
| Backend → Bonita: tareas posteriores | No completa ninguna después de "Publicar convocatoria". | Depende de las tareas nuevas (selección, notificación, monitoreo, finalización). |
| Bonita → Backend (ofertas consolidadas) | Funciona con cuenta técnica `SERVICIO_BONITA` y permiso `CONSULTAR_OFERTAS_CONSOLIDADAS`. | Ninguno. |
| Bonita → Sistema Nacional | Conector con login `SERVICIO`, hasta 3 intentos, 429/5xx reintentables. | Ver timeouts abajo. |
| Identificadores | El SN valida por CUIT. `Ong.cuit` ya existe, con 11 dígitos y nullable, y viaja en las ofertas consolidadas. | **Las ONGs existentes sin CUIT hacen fallar la evaluación.** Hacer el CUIT obligatorio en el alta o completar los datos. |
| `ofertaRef` / `emergenciaRef` | El conector los arma desde los IDs de RescueSync. | Confirmar con el equipo del SN que son los IDs locales y que no hay choque entre entornos. |
| Unidades de medida | Texto libre en `Lote.unidadMedida`. El SN normaliza (minúsculas, espacios), pero `kg` ≠ `kilogramos`. | Ver decisiones abiertas. |

**Timeout y reintento ante el arranque en frío de Render.** El timeout por request es de 90 s, que alcanza para un arranque de 30 a 60 s. El timeout de conexión es de 5 s. El backoff entre reintentos es de 200 ms y 400 ms. Si el proxy de Render responde 502/503 mientras levanta, los 3 intentos se gastan en menos de 1 s y el caso queda en incidente.
Propuesta: antes de la primera operación, llamar a `/actuator/health` con timeout largo, o subir el backoff a unos 15–20 s. Estimación: 2 h, más probarlo contra Render.

## 4. Estado técnico

| Punto | Resultado |
| --- | --- |
| Tests del backend | **68/68 pasan** (arreglado en `775e87a`: `jwt.secret`, cookie en vez de Bearer, token del centro coordinador, `plazo-ms`). |
| Tests del conector SN | **20/20 pasan con JDK 17.** Con `JAVA_HOME` en JDK 26 el build falla (`Unsupported class file major version 70` en Groovy). La doc dice JDK 21 y el `pom` usa `release 17`. Alinear README y `JAVA_HOME`. |
| `scripts/configurar_bonita_e3_10.py --check` | **Falla**: `AssertionError: Configuración no idempotente` (Python 3.12, Windows). El `.bos` está intacto (mismo hash que en git). Sin diagnosticar; puede ser una diferencia de codificación del entorno o un problema real del script. |
| Importar `rescue-sync.bos` en otra máquina | **Sin probar.** Sí se pudo extraer el archivo y leer el diagrama. Hace falta Bonita Studio 2025.2 (el doc E3-10 es explícito en esto). |
| PostgreSQL real | **Sin probar.** Docker Desktop no estaba corriendo (no responde el engine). El backend de test usa H2 en modo PostgreSQL. `ddl-auto: update` en el SN: no hay migraciones. |

## 5. Decisiones abiertas

- **Unidades de medida:** catálogo cerrado (enum o tabla) en ambos sistemas, o seguir con texto libre y normalizar. El enum evita que `kg` ≠ `kilogramos` rompa el compromiso.
- **Indicadores del dashboard:** definir cuáles salen de RescueSync (emergencias, lotes, ofertas, cobertura) y cuáles de Bonita (tiempos de tarea, casos abiertos, incidentes).
- **Permisos nuevos:** hoy no existen para seleccionar oferta, notificar, monitorear despliegue, finalizar actividad ni ver el dashboard. Propuesta inicial, a validar: selección y notificación para el municipio; monitoreo para el centro coordinador; finalización para la ONG; dashboard para centro y municipio.

## 6. Lista de ajustes

Las estimaciones son un primer cálculo.

| # | Ajuste | Estimación | Tarea E4 | Responsable |
| --- | --- | --- | --- | --- |
| 1 | Push del merge de E3-10 a `development` | 10 min | | |
| 2 | Verificar el SN en Render (`/actuator/health`, `/api/info`, `/swagger-ui.html`) | 20 min | | |
| 3 | Seleccionar oferta: endpoint, permiso, estados y pantalla | 6 h | | |
| 4 | Notificar a ONG y recibir notificación: endpoint, estado y pantalla | 5 h | | |
| 5 | Monitorear despliegue: endpoint, estado `EN_EJECUCION` y pantalla | 5 h | | |
| 6 | Finalizar actividad: endpoint, `FINALIZADA`/`CERRADA` y pantalla | 5 h | | |
| 7 | Resolver cobertura incompleta: endpoint y pantalla (`REFORMULADO`) | 6 h | | |
| 8 | Reflejar en el backend los resultados de Evaluar, Comprometer y Liberar | 4 h | | |
| 9 | Completar tareas de Bonita desde el backend después de "Publicar convocatoria" | 3 h | | |
| 10 | Timeout y warm-up del conector ante arranque en frío de Render | 2 h | | |
| 11 | CUIT obligatorio en ONGs y migración de datos | 2 h | | |
| 12 | Catálogo de unidades de medida | 4 h | | |
| 13 | Permisos y roles nuevos | 2 h | | |
| 14 | Dashboard de indicadores | 6 h | | |
| 15 | Diagnosticar `configurar_bonita_e3_10.py --check` y alinear README con JDK 17 | 1 h | | |
| 16 | Importar el `.bos` en otra máquina y probar el flujo completo | 3 h | | |
| 17 | Probar backend y SN contra PostgreSQL real | 2 h | | |

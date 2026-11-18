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

## 5. Decisiones tomadas

Fecha: 2026-11-18. Las tomó Claude por delegación del equipo; quedan registradas para que el equipo las revise y, si hace falta, las cambie.

### D1 — Unidades de medida: catálogo cerrado

- **Decisión:** reemplazar el texto libre por un catálogo cerrado `UnidadMedida` (enum) en RescueSync, con el mismo conjunto de valores en el Sistema Nacional.
- **Valores iniciales:** `PERSONAS`, `RACIONES`, `KILOGRAMOS`, `LITROS`, `UNIDADES`, `CAJAS`, `VEHICULOS`, `HORAS`.
- **Por qué:** el Sistema Nacional compara por tipo de recurso y unidad, y solo normaliza mayúsculas y espacios. Con texto libre, `kg` y `kilogramos` no coinciden y el compromiso termina en incidente. Un catálogo cierra el error en el origen. `TipoRecurso` ya es un enum, así que la unidad sigue el mismo criterio.
- **Alternativa descartada:** seguir con texto libre y normalizar mejor. No resuelve los sinónimos y obliga a mantener reglas de equivalencia en dos sistemas.
- **Queda por hacer:** acordar la lista final con quien mantiene el Sistema Nacional, migrar los lotes y recursos existentes y actualizar el formulario de lotes. Ver ajuste 12.

### D2 — Indicadores del dashboard: reparto por origen del dato

- **Decisión:** los indicadores de negocio salen de RescueSync y los de proceso salen de Bonita.
- **RescueSync (base de datos propia):**
  - emergencias por estado;
  - lotes por estado y porcentaje de cobertura (`totalOfrecido` sobre `cantidadRequerida`);
  - ofertas recibidas, seleccionadas y finalizadas;
  - total ofrecido por tipo de recurso.
- **Bonita (API del proceso):**
  - casos abiertos y cerrados;
  - tiempo de ejecución por tarea;
  - incidentes abiertos, es decir, fallas de los conectores.
- **Por qué:** cada sistema es la fuente de verdad de sus datos. Así el dashboard no duplica estado ni depende de que Bonita conozca el modelo de lotes.
- **Alternativa descartada:** leer todo desde Bonita. Obligaría a copiar lotes y ofertas a variables del proceso.
- **Queda por hacer:** confirmar que el usuario de servicio de Bonita puede consultar la API de casos y tareas.

### D3 — Permisos nuevos: según el lane del diagrama BPMN

- **Decisión:** cada permiso nuevo se asigna al rol del lane donde está la tarea en el diagrama.

| Permiso | Rol | Lane en el BPMN |
| --- | --- | --- |
| `SELECCIONAR_OFERTA` | `OPERADOR_MUNICIPAL` | Municipio |
| `NOTIFICAR_ONG` | `OPERADOR_MUNICIPAL` | Municipio |
| `MONITOREAR_DESPLIEGUE` | `CENTRO_COORDINADOR` | Centro Coordinador |
| `FINALIZAR_ACTIVIDAD` | `REPRESENTANTE_ONG` | ONG |
| `CONSULTAR_DASHBOARD` | `CENTRO_COORDINADOR` y `OPERADOR_MUNICIPAL` | No aplica |

- **Por qué:** el modelo de permisos queda alineado con el proceso que ya aprobó el equipo, y no hace falta inventar excepciones. `SERVICIO_BONITA` conserva solo `CONSULTAR_OFERTAS_CONSOLIDADAS`.
- **Ajustes:** el operador municipal solo ve las emergencias de su municipio y el representante de ONG solo finaliza ofertas de su propia ONG. Esas restricciones van en el servicio, no en el permiso.
- **Queda por hacer:** agregar los valores a `Permiso` y asignarlos en `Rol`. Ver ajuste 13.

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
| 12 | Catálogo de unidades de medida (decisión D1) | 4 h | | |
| 13 | Permisos y roles nuevos (decisión D3) | 2 h | | |
| 14 | Dashboard de indicadores (decisión D2) | 6 h | | |
| 15 | Diagnosticar `configurar_bonita_e3_10.py --check` y alinear README con JDK 17 | 1 h | | |
| 16 | Importar el `.bos` en otra máquina y probar el flujo completo | 3 h | | |
| 17 | Probar backend y SN contra PostgreSQL real | 2 h | | |

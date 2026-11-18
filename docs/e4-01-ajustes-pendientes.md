# E4-01 — Relevamiento y ajustes pendientes

Fecha del relevamiento: 2026-11-18. Base: `development` con `feature/bonitaapi` (E3-10) mergeada localmente.

Las columnas **Tarea E4** y **Responsable** quedan para completar en la reunión: el relevamiento no tiene los títulos de E4-02 a E4-10. Las estimaciones son una primera propuesta.

**Actualización del 18/11/2026 (tarde), acordada con el equipo:** se integró el dashboard de indicadores de E4 (contrato y detalle en [`e4-indicadores-contrato.md`](e4-indicadores-contrato.md)). Se mantiene esa implementación: las decisiones **D2** y **D3** quedan revisadas para reflejarla, el ajuste 14 está hecho y el 17 está hecho para el backend. Los cambios están marcados con *(actualizado)*.

**Actualización del 18/11/2026 (noche):** se probó E3-10 de punta a punta dentro de Bonita, con el backend y el Sistema Nacional levantados. Los conectores funcionan; aparecieron tres problemas del proceso y del script, que quedan como **ajustes 20, 21 y 22**. Informe completo: [`e3-10-prueba-integracion.md`](e3-10-prueba-integracion.md). Los cambios están marcados con *(prueba E3-10)*.

## 1. Terreno

| Punto | Estado |
| --- | --- |
| `development` tiene E3-11 | Sí (`5bf2021`). |
| E3-10 | Estaba solo en `origin/feature/bonitaapi` (`3cf2123`), sin mergear. Merge limpio sin conflictos, hecho **en local**; falta push. *(prueba E3-10)* Probada en Bonita la rama `feature/bonitaapi` tal cual está; sigue sin estar en `development`. |
| Tests del backend | Arreglados y subidos (`775e87a`): 68/68 pasan. *(actualizado)* Con E4 integrado: **117/117**. |
| Sistema Nacional en Render | **Sin verificar.** La URL pública no figura en el repo. Falta probar `/actuator/health`, `/api/info` y `/swagger-ui.html`. |

## 2. Proceso BPMN contra el código

El diagrama (`RescueSync 1.2`) tiene 4 lanes: Municipio, Centro Coordinador, ONG y Sistema Nacional.

| Actividad | Bonita | Backend | Frontend |
| --- | --- | --- | --- |
| Registrar emergencia | Tarea humana; el backend la completa al registrar | `POST /api/emergencias` | `AltaEmergenciaPage` |
| Revisar y desglosar emergencia | Tarea humana; el backend la completa al publicar | `POST/PUT/DELETE .../lotes` | `LotesPage` |
| Publicar convocatoria | Tarea humana; el backend la completa | `POST /emergencias/{id}/convocatoria` | `LotesPage` |
| Esperar / cargar / editar ofertas | Tarea humana (ONG) | `POST/PUT /lotes/{id}/ofertas` | `OfertasPage` |
| Período convocatorio → Evaluar cobertura de lotes | Timer + conector de ofertas consolidadas (E3-09). *(prueba E3-10)* **La tarea no tiene contrato y el gateway "¿Lotes cubiertos?" tiene tres salidas con la misma condición, que nadie modifica: siempre va a "Resolver cobertura incompleta"** | `GET .../ofertas-consolidadas`. *(prueba E3-10)* No hay endpoint para decidir la cobertura | ~~No aplica~~ *(prueba E3-10)* **No existe**: el centro coordinador no tiene dónde revisar ni decidir la cobertura. Ajuste 20 |
| Resolver cobertura incompleta | Tarea humana | **No existe.** `EstadoLote.REFORMULADO` sin uso | **No existe** |
| Evaluar ofertas | Service task + conector SN `EVALUAR` | No guarda el resultado (`EstadoOferta.EVALUADA` sin uso) | No aplica |
| **Seleccionar oferta** | Tarea humana con contrato (`ofertaSeleccionadaIdInput`, `continuarSeleccionandoInput`) | **No existe** (`SELECCIONADA`/`RECHAZADA` sin uso) | **No existe** |
| **Notificar a ONG** | Tarea humana, sin lógica | **No existe** | **No existe** |
| Recibir notificación (ONG) | Tarea humana, sin lógica | **No existe** | **No existe** |
| Comprometer recursos | Service task + conector SN `COMPROMETER` | No actualiza estados (`COMPROMETIDA`, `EN_EJECUCION` sin uso) | No aplica |
| **Monitorear despliegue** | Tarea humana, sin lógica | **No existe** | **No existe** |
| **Finalizar actividad** | Tarea humana, sin lógica | **No existe** (`FINALIZADA`, `CERRADA` sin uso) | **No existe** |
| Liberar recursos | Service task + conector SN `LIBERAR` | No actualiza estados | No aplica |

*(actualizado)* El **dashboard de indicadores** ya existe: rol `DIRECTIVO`, pestaña "Dirección · Indicadores" en el front y endpoints `/api/indicadores/*` con exportación JSON, CSV y XLSX. Ver D2 y [`e4-indicadores-contrato.md`](e4-indicadores-contrato.md).

Conclusión: de las 14 actividades solo está de punta a punta lo que va hasta publicar convocatoria y cargar ofertas, más el conector de ofertas consolidadas. Las tres service tasks del Sistema Nacional existen en Bonita con conector, pero nadie las alimenta ni refleja su resultado en el backend. Las 4 tareas en negrita no tienen backend ni frontend. Los estados `EVALUADA`, `SELECCIONADA`, `RECHAZADA`, `COMPROMETIDA`, `FINALIZADA`, `ADJUDICADA`, `EN_EJECUCION` y `CERRADA` están definidos pero nada los usa.

*(actualizado)* **Regla para los ajustes 3 a 8:** todo cambio de estado de `Emergencia`, `Lote` u `Oferta` tiene que pasar por `HistorialEstadoService.cambiarEstado(...)`, no por `setEstado(...)`. El dashboard calcula "suministros movilizados en el mes", "lotes resueltos por consorcio" y la fecha de cierre a partir de `historial_estados`. Si una transición no se registra ahí, no aparece en los indicadores.

## 3. Integraciones

| Integración | Estado | Ajuste |
| --- | --- | --- |
| Backend → Bonita | Inicia instancia y completa 3 tareas. Si falla la sincronización, solo loguea un warning y la emergencia queda desincronizada. *(actualizado)* Ahora tiene timeouts (conexión 5 s, lectura 15 s). Se probó contra Bonita 10.4 y quedó en evidencia que, si al registrar falla algo que no es `BonitaIntegrationException` (por ejemplo un 415), el alta entera responde 500 y se deshace. | Decidir si se reintenta o se marca la emergencia como desincronizada. Atrapar cualquier error de Bonita al registrar (ajuste 18). |
| Backend → Bonita (indicadores) | *(actualizado)* El dashboard lee de Bonita `case`, `archivedCase` y `archivedFlowNode` con el usuario `BONITA_USER`. Se verificó con Bonita 10.4: `archivedActivity` trae solo el estado final y no sirve para medir duraciones. | ~~Verificar el camino de un caso terminado (ajuste 19).~~ *(prueba E3-10)* Verificado. |
| Backend → Bonita: tareas posteriores | No completa ninguna después de "Publicar convocatoria". | Depende de las tareas nuevas (selección, notificación, monitoreo, finalización). |
| Bonita → Backend (ofertas consolidadas) | Funciona con cuenta técnica `SERVICIO_BONITA` y permiso `CONSULTAR_OFERTAS_CONSOLIDADAS`. | Ninguno. |
| Bonita → Sistema Nacional | Conector con login `SERVICIO`, hasta 3 intentos, 429/5xx reintentables. | Ver timeouts abajo. |
| Identificadores | El SN valida por CUIT. `Ong.cuit` ya existe, con 11 dígitos y nullable, y viaja en las ofertas consolidadas. | **Las ONGs existentes sin CUIT hacen fallar la evaluación.** Hacer el CUIT obligatorio en el alta o completar los datos. |
| `ofertaRef` / `emergenciaRef` | El conector los arma desde los IDs de RescueSync. | Confirmar con el equipo del SN que son los IDs locales y que no hay choque entre entornos. |
| Unidades de medida | Texto libre en `Lote.unidadMedida`. El SN normaliza (minúsculas, espacios), pero `kg` ≠ `kilogramos`. | Ver decisiones abiertas. *(actualizado)* El dashboard agrupa los suministros por unidad, así que el catálogo D1 también lo beneficia. |

**Timeout y reintento ante el arranque en frío de Render.** El timeout por request es de 90 s, que alcanza para un arranque de 30 a 60 s. El timeout de conexión es de 5 s. El backoff entre reintentos es de 200 ms y 400 ms. Si el proxy de Render responde 502/503 mientras levanta, los 3 intentos se gastan en menos de 1 s y el caso queda en incidente.
Propuesta: antes de la primera operación, llamar a `/actuator/health` con timeout largo, o subir el backoff a unos 15–20 s. Estimación: 2 h, más probarlo contra Render.

## 4. Estado técnico

| Punto | Resultado |
| --- | --- |
| Tests del backend | **68/68 pasan** (arreglado en `775e87a`: `jwt.secret`, cookie en vez de Bearer, token del centro coordinador, `plazo-ms`). *(actualizado)* Con E4: **117/117**. |
| Tests del conector SN | **20/20 pasan con JDK 17.** Con `JAVA_HOME` en JDK 26 el build falla (`Unsupported class file major version 70` en Groovy). La doc dice JDK 21 y el `pom` usa `release 17`. Alinear README y `JAVA_HOME`. |
| `scripts/configurar_bonita_e3_10.py --check` | **Falla**: `AssertionError: Configuración no idempotente` (Python 3.12, Windows). El `.bos` está intacto (mismo hash que en git). Sin diagnosticar; puede ser una diferencia de codificación del entorno o un problema real del script. *(prueba E3-10)* **Diagnosticado:** Python 3.11 y 3.12 escriben los saltos de línea de los atributos XML sin escapar; el `.bos` versionado los tiene escapados (`&#10;`), que es lo correcto. **No regenerar el `.bos` con esas versiones**: rompe el script Groovy de "Seleccionar oferta". Ajuste 22 |
| Importar `rescue-sync.bos` en otra máquina | **Sin probar.** Sí se pudo extraer el archivo y leer el diagrama. Hace falta Bonita Studio 2025.2 (el doc E3-10 es explícito en esto). *(prueba E3-10)* **Hecho** en una segunda máquina: importado, con los conectores incluidos en el `.bos` más los de Maven local, y RescueSync 1.2 desplegado |
| Conectores con JDK | *(prueba E3-10)* Con **JDK 21**: 12/12 (E3-09) y 20/20 (E3-10). La primera vez hace falta conexión para bajar `surefire-junit-platform` |
| PostgreSQL real | **Sin probar.** Docker Desktop no estaba corriendo (no responde el engine). El backend de test usa H2 en modo PostgreSQL. `ddl-auto: update` en el SN: no hay migraciones. *(actualizado)* **Backend probado** contra PostgreSQL 16 (base vacía en Docker): crea el esquema y corren `seed-backend-demo.sql` y `seed-indicadores-demo.sql`, que es idempotente. El SN sigue sin probar. Ojo: una base creada antes de E3 no sirve con el modelo actual (`emergencias.municipio` era texto); hay que recrearla (`docker compose down -v`). |
| Flujo con Bonita 10.4 | *(actualizado)* **Probado** con `RescueSync` 1.1 desplegado en Studio local: registrar emergencia, desglosar y publicar convocatoria completan sus tareas en Bonita, y el dashboard calcula los tiempos de esas etapas. *(prueba E3-10)* **Probado con 1.2 hasta el final**: evaluación, selección, compromiso, liberación, ciclo de selección, cierre del caso y stock insuficiente. Ver el informe |

## 5. Decisiones tomadas

Fecha: 2026-11-18. Las tomó Claude por delegación del equipo; quedan registradas para que el equipo las revise y, si hace falta, las cambie.

### D1 — Unidades de medida: catálogo cerrado

- **Decisión:** reemplazar el texto libre por un catálogo cerrado `UnidadMedida` (enum) en RescueSync, con el mismo conjunto de valores en el Sistema Nacional.
- **Valores iniciales:** `PERSONAS`, `RACIONES`, `KILOGRAMOS`, `LITROS`, `UNIDADES`, `CAJAS`, `VEHICULOS`, `HORAS`.
- **Por qué:** el Sistema Nacional compara por tipo de recurso y unidad, y solo normaliza mayúsculas y espacios. Con texto libre, `kg` y `kilogramos` no coinciden y el compromiso termina en incidente. Un catálogo cierra el error en el origen. `TipoRecurso` ya es un enum, así que la unidad sigue el mismo criterio.
- **Alternativa descartada:** seguir con texto libre y normalizar mejor. No resuelve los sinónimos y obliga a mantener reglas de equivalencia en dos sistemas.
- **Queda por hacer:** acordar la lista final con quien mantiene el Sistema Nacional, migrar los lotes y recursos existentes y actualizar el formulario de lotes. Ver ajuste 12.

### D2 — Indicadores del dashboard: reparto por origen del dato *(actualizado)*

- **Decisión:** se mantiene el principio original: los indicadores de negocio salen de RescueSync y los de proceso salen de Bonita. El conjunto de indicadores es el **implementado en E4**, que sigue los ejemplos del enunciado.
- **RescueSync (base de datos propia más `historial_estados`):**
  - suministros movilizados por mes, tipo de recurso y unidad;
  - porcentaje de lotes resueltos mediante consorcios de 2 o más ONGs, en total y por tipo de recurso;
  - estado de los proyectos: emergencias por estado, porcentaje finalizado, cobertura y fecha de cierre por emergencia.
- **Bonita (API del proceso):**
  - tiempo promedio, mediana, mínimo y máximo **por etapa** de la emergencia, que agrupa las tareas del diagrama (`EtapaEmergencia`);
  - casos analizados y finalizados, y duración promedio de los casos terminados.
- **Filtros y exportación:** rango de fechas, municipio, tipo de desastre y tipo de recurso. Exporta JSON, CSV para Excel y XLSX.
- **Por qué:** cada sistema es la fuente de verdad de sus datos, y los indicadores responden a los ejemplos de la consigna (suministros del mes, resolución por consorcios, proyectos finalizados, tiempos por etapa). La fórmula exacta de cada uno está en `e4-indicadores-contrato.md`.
- **Del D2 original no se incluyó:** ofertas por estado, lotes por estado e **incidentes abiertos** de Bonita. Se pueden agregar después sin cambiar lo existente.
- **Verificado:** el usuario `BONITA_USER` puede consultar `case`, `archivedCase` y `archivedFlowNode`.

### D3 — Permisos nuevos: según el lane del diagrama BPMN *(actualizado)*

- **Decisión:** cada permiso de una tarea del proceso se asigna al rol del lane donde está la tarea en el diagrama. El dashboard, que no es una tarea del proceso, tiene un **rol propio**.

| Permiso | Rol | Lane en el BPMN | Estado |
| --- | --- | --- | --- |
| `SELECCIONAR_OFERTA` | `OPERADOR_MUNICIPAL` | Municipio | Pendiente |
| `NOTIFICAR_ONG` | `OPERADOR_MUNICIPAL` | Municipio | Pendiente |
| `MONITOREAR_DESPLIEGUE` | `CENTRO_COORDINADOR` | Centro Coordinador | Pendiente |
| `FINALIZAR_ACTIVIDAD` | `REPRESENTANTE_ONG` | ONG | Pendiente |
| `CONSULTAR_INDICADORES` | `DIRECTIVO` (rol nuevo) | No aplica | **Hecho** |

- **Por qué:** el modelo de permisos queda alineado con el proceso que ya aprobó el equipo. El enunciado pide el dashboard para el **personal directivo**, que no participa de ningún lane, por eso tiene rol propio en vez de sumárselo al centro coordinador o al operador municipal. Si el equipo quiere que otro rol también lo vea, alcanza con agregarle `CONSULTAR_INDICADORES`. Para el operador municipal además habría que filtrar por su municipio en el servicio. `SERVICIO_BONITA` conserva solo `CONSULTAR_OFERTAS_CONSOLIDADAS`.
- **Ajustes:** el operador municipal solo ve las emergencias de su municipio y el representante de ONG solo finaliza ofertas de su propia ONG. Esas restricciones van en el servicio, no en el permiso.
- **Hecho:** `DIRECTIVO` y `CONSULTAR_INDICADORES` en `Rol`/`Permiso`, el script `scripts/usuarios_rol_directivo.sql` para bases existentes y el usuario demo `direccion@test.com`.
- **Queda por hacer:** los otros cuatro permisos. Ver ajuste 13.

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
| 8 | Reflejar en el backend los resultados de Evaluar, Comprometer y Liberar. *(prueba E3-10)* Confirmado: un caso terminado en Bonita deja la emergencia en `CONVOCATORIA_PUBLICADA` y la oferta en `PENDIENTE` | 4 h | | |
| 9 | Completar tareas de Bonita desde el backend después de "Publicar convocatoria" | 3 h | | |
| 10 | Timeout y warm-up del conector ante arranque en frío de Render | 2 h | | |
| 11 | CUIT obligatorio en ONGs y migración de datos | 2 h | | |
| 12 | Catálogo de unidades de medida (decisión D1) | 4 h | | |
| 13 | Permisos y roles nuevos (decisión D3). *(actualizado)* `DIRECTIVO`/`CONSULTAR_INDICADORES` hecho; faltan los cuatro permisos de tareas | 1,5 h | | |
| 14 | ~~Dashboard de indicadores (decisión D2)~~ *(actualizado)* **Hecho** | — | | |
| 15 | Diagnosticar `configurar_bonita_e3_10.py --check` y alinear README con JDK 17. *(prueba E3-10)* Diagnóstico hecho (ver ajuste 22); falta alinear el README | 30 min | | |
| 16 | ~~Importar el `.bos` en otra máquina y probar el flujo completo~~ *(prueba E3-10)* **Hecho**, salvo caída temporal de la API nacional y reintentos | — | | |
| 17 | Probar backend y SN contra PostgreSQL real. *(actualizado)* Backend **hecho**; falta el SN | 1 h | | |
| 18 | *(nuevo)* Alta de emergencia: atrapar cualquier error de Bonita (no solo `BonitaIntegrationException`), así el alta no se deshace si falla la sincronización | 30 min | | |
| 19 | ~~*(nuevo)* Tiempos del dashboard: verificar contra un caso terminado en Bonita (`archivedCase`, duración total)~~ *(prueba E3-10)* **Hecho**: `archivedCase?f=sourceObjectId=` devuelve el caso terminado con `start` y `end_date` | — | | |
| 20 | *(prueba E3-10)* **Decisión de cobertura de lotes.** Proceso: contrato en "Evaluar cobertura de lotes" con una decisión de tres valores (`COMPLETA`, `PARCIAL`, `SIN_COBERTURA`) y una condición distinta por salida del gateway. Backend: endpoint del centro coordinador que registre la decisión, actualice los estados con `HistorialEstadoService` y complete la tarea. Front: pantalla de cobertura por lote. **Bloquea el camino normal del proceso** | 6 h | | |
| 21 | *(prueba E3-10)* **Validación de "Seleccionar oferta".** Hoy está en un script de operación: una oferta inválida deja la tarea `failed` y el caso trabado. Pasarla a la restricción del contrato (Bonita responde 400 y la tarea sigue disponible) o validar en el backend antes de ejecutar (junto con el ajuste 3) | 1 h | | |
| 22 | *(prueba E3-10)* **`configurar_bonita_e3_10.py` y la versión de Python.** Con Python anterior a 3.13, regenerar el `.bos` escribe saltos de línea literales en los atributos y rompe el script Groovy de "Seleccionar oferta". Hacer que el script escape `\n` por su cuenta, o que exija Python 3.13 o posterior | 30 min | | |

# E3-10 — Prueba de integración de punta a punta (18/11/2026)

Primera ejecución real del proceso **RescueSync 1.2** dentro de Bonita con el backend y el Sistema Nacional levantados. Hasta ahora E3-10 solo tenía pruebas automáticas (ver `e3-10-sistema-nacional.md`, sección 6: "no se desplegó un caso durante esta implementación"; ese documento llega a `development` con el merge de E3-10, ajuste 1 de E4-01).

Se probó la rama `feature/bonitaapi` sin cambios. Los ajustes que surgen de esta prueba están en [`e4-01-ajustes-pendientes.md`](e4-01-ajustes-pendientes.md) (20, 21 y 22).

**Resumen:** los tres conectores de E3-10 (evaluar, comprometer y liberar) y el de E3-09 funcionan dentro de Bonita como describe la guía: con certificación y sin ella, compromiso y liberación exactos del stock, varios ciclos de selección, cierre del caso y error por stock insuficiente sin reserva. Aparecieron **tres problemas en el modelo del proceso y en el script de configuración**, ninguno en los conectores (sección 4), y se confirmó con casos reales que **RescueSync no refleja nada de lo que pasa después de la convocatoria** (ajuste 8 de E4-01).

| Escenario de la guía (sección 6, prueba manual) | Resultado |
| --- | --- |
| 3. Evaluación con y sin certificación | ✅ |
| 4–5. Compromiso de una sola oferta y stock exacto | ✅ |
| 6. Liberación y stock repuesto una vez | ✅ |
| 7. Seguir seleccionando / no volver a elegir una finalizada / cierre con `false` | ✅ / ⚠️ problema 2 / ✅ |
| 8. Stock insuficiente: incidente sin reserva | ✅ |
| 8. Caída temporal de la API y reintentos | No probado |
| Gateway de cobertura (anterior a E3-10, pero en el camino) | ❌ problema 1 |

## 1. Entorno

| Pieza | Versión / configuración |
| --- | --- |
| Código | Rama `feature/bonitaapi` (`3cf2123`) |
| Bonita | Studio Community 2025.2 / Runtime 10.4, `rescue-sync.bos` importado y **RescueSync 1.2** desplegado (única versión habilitada) |
| Conectores | `bonita-ofertas-consolidadas-connector` y `bonita-sistema-nacional-connector` 1.0.0-SNAPSHOT, construidos con **JDK 21** y `mvn install` |
| Backend | Puerto 8081, PostgreSQL 16 limpio, `seed-backend-demo.sql` (ONGs con CUIT), `PLAZO_CONVOCATORIA_MS=180000` (convocatoria de 3 minutos) |
| Sistema Nacional | Puerto 8082, PostgreSQL 16 limpio, `seed-sistema-nacional-demo.sql` |
| Credenciales técnicas | `RESCUESYNC_BONITA_SERVICE_EMAIL/PASSWORD` en el backend y en Bonita; `SISTEMA_NACIONAL_BONITA_USERNAME/PASSWORD` (cuenta `bonita@sistemanacional.test`, rol `SERVICIO`) en Bonita. Variables de usuario de Windows cargadas con `setx`, con Studio reiniciado después |

Las bases eran contenedores descartables (no las de desarrollo de cada uno).

### Datos cargados en el Sistema Nacional

El seed nacional trae ONGs y usuarios, pero **no trae certificaciones ni inventario**. Se cargaron por la API para cubrir los casos de la guía:

| ONG | Certificación ALIMENTOS | Inventario ALIMENTOS | Para probar |
| --- | --- | --- | --- |
| Cruz Roja Argentina (`30500000001`) | Sí: INTERMEDIO, riesgo máximo ALTO, sin vencimiento (`POST /api/ongs/1/certificaciones` como ADMIN) | 200 raciones (`POST /api/ongs/1/recursos` como su representante) | Camino feliz |
| Caritas Argentina (`30500000002`) | **No** | **40 raciones** | Evaluación sin capacidades y stock insuficiente |

En RescueSync, cada emergencia de prueba tuvo un lote de **100 raciones de ALIMENTOS**. La unidad tiene que ser igual a la del inventario nacional.

## 2. Pruebas automáticas

| Prueba | Resultado |
| --- | --- |
| `mvn -f bonita-ofertas-consolidadas-connector/pom.xml install` (JDK 21) | 12/12 |
| `mvn -f bonita-sistema-nacional-connector/pom.xml install` (JDK 21) | 20/20 |
| Backend: `OfertasConsolidadasIntegrationTests`, `BonitaServiceAccountIntegrationTests` | 9/9 |
| `python scripts/configurar_bonita_e3_10.py --check` | **Falla** con Python 3.11 (y 3.12, ver problema 3). El `.bos` versionado está bien |

En modo offline (`mvn -o`) los conectores no corren sus tests si nunca se descargó `surefire-junit-platform`; la primera vez hace falta conexión.

## 3. Recorrido del proceso

Las tareas humanas posteriores a "Publicar convocatoria" no tienen pantalla todavía (ajustes 3 a 7 de E4-01), así que se ejecutaron con la API de Bonita como `walter.bates`: `PUT /API/bpm/humanTask/{id}` con `assigned_id` y `POST /API/bpm/userTask/{id}/execution` con el contrato como cuerpo JSON (`{}` si no tiene).

### Caso 2 (emergencia 1): dos ofertas, selección de a una

Ofertas: Cruz Roja 60 raciones (oferta 1) y Caritas 50 (oferta 2).

| Paso | Resultado |
| --- | --- |
| Registrar, desglosar y publicar (desde la app) | ✅ El backend completó las tres tareas en Bonita |
| Temporizador → **ofertas consolidadas** (E3-09) | ✅ `ofertasConsolidadasJson` con el lote, las dos ofertas y el **CUIT** de cada ONG. Confirma que Bonita tomó las credenciales técnicas del entorno |
| Evaluar cobertura de lotes → gateway "¿Lotes cubiertos?" | ❌ Con cobertura **completa** (110/100) el caso fue a **"Resolver cobertura incompleta"**. Ver problema 1 |
| Resolver cobertura incompleta | Completada sin datos (no tiene contrato); sigue a "Evaluar ofertas" |
| **Evaluar ofertas** (E3-10) | ✅ Cruz Roja: `HABILITADA` con su capacidad de ALIMENTOS. Caritas: `capacidadesHabilitadas: []` y la observación "no tiene una certificación vigente". **No se rechaza** automáticamente, como dice la guía |
| Seleccionar oferta `{ofertaSeleccionadaIdInput: 1, continuarSeleccionandoInput: true}` | ✅ |
| Notificar a ONG / Recibir notificación | ✅ (tareas sin lógica) |
| **Comprometer recursos** (E3-10) | ✅ Compromiso 1: 60 raciones, `emergenciaRef 1`, `ofertaRef 1`, `COMPROMETIDO`. Stock de Cruz Roja: disponible **200 → 140**. Caritas sin cambios |
| Monitorear despliegue / Finalizar actividad | ✅ (tareas sin lógica) |
| **Liberar recursos** (E3-10) | ✅ Compromiso `LIBERADO`; disponible **140 → 200**, una sola vez. `ofertasProcesadasJson = [1]`, `hayOfertasPendientes = true` |
| Gateway de selección | ✅ Volvió a "Seleccionar oferta" (quedaban ofertas y se eligió continuar) |
| Volver a elegir la oferta 1 (ya finalizada) | ⚠️ La API aceptó el pedido (204) y la tarea quedó **`failed`** (incidente). El caso queda trabado. Ver problema 2 |

### Caso A (emergencia 3, caso Bonita 5): cierre normal

Oferta: Cruz Roja 60 raciones (oferta 4). Selección con `continuarSeleccionandoInput: false`.

| Paso | Resultado |
| --- | --- |
| Evaluar cobertura → Resolver cobertura incompleta → Evaluar ofertas | ✅ Mismo camino que el caso 2 (problema 1) |
| Seleccionar, notificar, recibir, **comprometer** | ✅ Compromiso 2: emergencia 3, oferta 4, 60 raciones |
| Monitorear, finalizar, **liberar** | ✅ Compromiso `LIBERADO`; stock de Cruz Roja otra vez en 200 |
| Salida del gateway con `continuar: false` | ✅ El caso **terminó**: sin nodos activos, `GET /API/bpm/case/5` responde 404 |
| `GET /API/bpm/archivedCase?f=sourceObjectId=5` | ✅ Estado `completed`, con `start` y `end_date`. Verifica lo que usa el dashboard para la duración total de un caso (ajuste 19 de E4-01) |

### Caso B (emergencia 4, caso Bonita 6): stock insuficiente

Oferta: Caritas 50 raciones (oferta 5), con 40 de stock nacional.

| Paso | Resultado |
| --- | --- |
| Hasta "Recibir notificación" | ✅ Mismo camino que el caso A |
| **Comprometer recursos** | ✅ La tarea quedó `failed` (incidente). En `bonita.log`: `Sistema Nacional: /api/compromisos devolvió HTTP 409`. Sin reintentos (el 409 no se reintenta) |
| Efecto en el Sistema Nacional | ✅ Ningún compromiso para Caritas; stock intacto (40 disponibles) |
| Variables del caso | ✅ `compromisoNacionalJson` sigue en `{}`: no se publica un compromiso parcial |

El log de Bonita Studio está en `C:\BonitaStudioCommunity-2025.2-u0\workspace\tomcat\server\logs\bonita.log` (ruta de la instalación usada). Es donde aparece el motivo de cada incidente; la API REST de la edición Community no lo informa.

### Lo que no se refleja en RescueSync

Al terminar los tres casos, el backend seguía con **todo en el estado de la convocatoria**: emergencias `CONVOCATORIA_PUBLICADA`, lotes `PUBLICADO` y ofertas `PENDIENTE`, incluida la emergencia 3, cuyo caso terminó en Bonita con su oferta comprometida, finalizada y liberada. Es el **ajuste 8** de E4-01, confirmado con casos reales. Mientras no se resuelva, el dashboard de indicadores no ve nada del proceso posterior a la convocatoria.

## 4. Problemas encontrados

### Problema 1 — La decisión de cobertura no existe en el proceso

- La tarea **"Evaluar cobertura de lotes"** no tiene contrato ni operaciones. Solo tiene el conector de E3-09 (`ON_ENTER`).
- El gateway exclusivo **"¿Lotes cubiertos?"** tiene tres salidas con **la misma condición**, `coberturaCompleta`:
  - `si` → Evaluar ofertas;
  - `parcial` → Resolver cobertura incompleta;
  - `no` → vuelve a Publicar convocatoria.
- `coberturaCompleta` vale `true` desde el inicio y **ninguna operación del proceso la modifica**.
- Resultado: las tres salidas son siempre verdaderas y Bonita toma la primera que evalúa ("parcial"). Comprobado con el caso 2: cobertura completa y fue igual a "Resolver cobertura incompleta".
- E3-10 corrigió este mismo patrón en el gateway de selección, pero no en este.
- **Tampoco hay pantalla ni endpoint** para que el centro coordinador revise la cobertura. En E4-01 la fila figura como "Frontend: No aplica".

**Propuesta (ajuste 20 de E4-01):**
- Proceso: contrato en "Evaluar cobertura de lotes" con una decisión de tres valores (`COMPLETA`, `PARCIAL`, `SIN_COBERTURA`) y una condición distinta por salida.
- Backend: endpoint del centro coordinador que registre la decisión, actualice los estados de lotes y emergencia (con `HistorialEstadoService`) y complete la tarea con el contrato. Los datos ya están en `GET /api/emergencias/{id}/ofertas-consolidadas` (`totalOfrecido` contra `cantidadRequerida`).
- Front: pantalla de cobertura por lote para el centro coordinador.

### Problema 2 — Una selección inválida deja la tarea en incidente

- "Seleccionar oferta" valida la oferta elegida (que sea de la emergencia y no esté finalizada) con un **script en una operación** de la tarea, que lanza `IllegalArgumentException`.
- En Bonita, una excepción en una operación ocurre **después** de aceptar la ejecución: la API responde 204 y la tarea pasa a `failed`. El caso queda trabado hasta que un administrador intervenga.
- Si la misma validación estuviera en una **restricción del contrato**, Bonita respondería 400 al momento y la tarea seguiría disponible para elegir otra oferta. Hoy la restricción `seleccionObligatoria` solo controla que los campos no sean nulos.
- La regla de negocio se cumple (no se vuelve a comprometer una oferta finalizada), pero el manejo del error no es el adecuado para una pantalla.

**Propuesta (ajuste 21 de E4-01):** mover la validación a la restricción del contrato, o validar en el backend antes de ejecutar la tarea cuando exista la pantalla de selección (ajuste 3).

### Problema 3 — `configurar_bonita_e3_10.py` depende de la versión de Python

- En el `.bos` versionado, los saltos de línea del script Groovy de "Seleccionar oferta" están escapados como `&#10;` dentro del atributo XML, que es lo correcto.
- `xml.dom.minidom` de Python 3.11 y 3.12 escribe esos saltos **sin escapar**. Por eso `--check` falla con "Configuración no idempotente": compara dos formas distintas de escribir lo mismo. Le pasó a Valentín con 3.12 y en esta prueba con 3.11.
- **Riesgo real:** ejecutar el script **sin** `--check` con esas versiones reescribe el `.bos` con saltos literales. Al leer un XML, los saltos dentro de un atributo se convierten en espacios: el script Groovy quedaría en una sola línea y "Seleccionar oferta" fallaría por error de sintaxis.
- Hasta corregirlo: **no regenerar el `.bos` con Python anterior a 3.13**; para verificar, alcanza con usar el `.bos` versionado.

**Propuesta (ajuste 22 de E4-01):** que el script escape `\n` en los atributos por su cuenta, o que exija Python 3.13 o posterior.

## 5. Cómo reproducir

1. Construir los conectores con JDK 21: `mvn -f bonita-ofertas-consolidadas-connector/pom.xml install` y `mvn -f bonita-sistema-nacional-connector/pom.xml install`.
2. Cargar las cuatro variables de credenciales en el entorno de Bonita y reiniciar Studio por completo.
3. Importar `rescue-sync.bos`, desplegar RescueSync 1.2 y dejar habilitada solo esa versión.
4. Levantar el Sistema Nacional con su seed y cargar certificación e inventario como en la sección 1. Levantar el backend con su seed y `PLAZO_CONVOCATORIA_MS` corto.
5. Crear emergencia, lote en **raciones** y ofertas, y publicar la convocatoria antes de que venza el plazo.
6. Avanzar las tareas humanas por la API de Bonita como en la sección 3, consultando las variables del caso (`GET /API/bpm/caseVariable/{caseId}/{nombre}`) y el inventario nacional (`GET /api/ongs/{id}/recursos`).

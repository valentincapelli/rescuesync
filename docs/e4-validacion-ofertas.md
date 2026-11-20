# E4 — Validación de ofertas y perfiles de competencia

Fecha: 19/11/2026. Cubre parte del ajuste 8 de [`e4-01-ajustes-pendientes.md`](e4-01-ajustes-pendientes.md): reflejar en RescueSync el resultado de **"Evaluar ofertas"**.

## Qué pide el enunciado

- **Eslabón 4:** "el sistema evalúa automáticamente cada oferta recibida". El Sistema Nacional "no aplicará un rechazo punitivo o binario, sino que retornará perfiles de competencia o niveles de habilitación". Las ofertas "se adaptarán a lotes principales o de apoyo secundario según corresponda".
- **Eslabón 5:** "el municipio afectado visualiza únicamente las ofertas validadas y selecciona las que mejor cubran sus necesidades".

## Qué faltaba

E3-10 ya consultaba al Sistema Nacional desde Bonita y dejaba el perfil de cada oferta en la variable `ofertasEvaluadasJson` del caso. Pero ese resultado nunca llegaba al backend: ninguna oferta pasaba a `EVALUADA` y la pantalla de selección del municipio (E4-04) aparecía vacía.

## Decisiones

| Tema | Decisión | Por qué |
| --- | --- | --- |
| Cómo se adapta la oferta | Se clasifica automáticamente en **principal**, **apoyo** o **sin habilitación** | Lo pide el eslabón 4. Solo mostrar el perfil no alcanza |
| Regla | Riesgo máximo certificado comparado con la gravedad de la emergencia (ver tabla abajo) | Es la regla que documenta el propio SN en `POST /api/certificaciones/validar`. Usa la misma escala (`BAJO` < `MEDIO` < `ALTO` < `CRITICO`), así que no hace falta cargar un nivel por lote |
| Ofertas sin habilitación | Se informan aparte y **no se pueden seleccionar** | Cumple las dos frases: el SN no rechaza (solo informa el perfil) y el municipio solo elige entre ofertas validadas |
| Cómo llega el resultado | El backend lee `ofertasEvaluadasJson` del caso cuando el municipio abre la pantalla de selección | No hay que tocar el `.bos`. Es el mismo mecanismo que ya usa el seguimiento de despliegues |

| Clasificación | Regla |
| --- | --- |
| `PRINCIPAL` | ONG `HABILITADA` con certificación vigente y riesgo máximo ≥ gravedad de la emergencia |
| `APOYO` | ONG `HABILITADA` con certificación vigente y riesgo máximo < gravedad |
| `SIN_HABILITACION` | Sin certificación vigente para el tipo de recurso, ONG no habilitada o CUIT no registrado |

Si el SN devuelve varias capacidades para el tipo de recurso, se usa la de mayor riesgo y, a igual riesgo, la de mayor competencia.

## Cómo funciona

1. El caso pasa por "Evaluar ofertas" y Bonita guarda `ofertasEvaluadasJson`. Este paso no cambió.
2. El municipio abre **"Seleccionar ofertas"**. `GET /api/ofertas/evaluadas` busca primero las emergencias de su municipio en `COBERTURA_EVALUADA` o `COBERTURA_PARCIAL` que tienen caso en Bonita y ofertas `PENDIENTE`.
3. Para cada una, `ValidacionOfertasService` lee la variable. Si todavía vale `[]`, el caso no evaluó y las ofertas siguen `PENDIENTE`.
4. Si ya hay resultado, bloquea las ofertas `PENDIENTE` (`FOR UPDATE`), guarda el perfil y la clasificación y las pasa a `EVALUADA` con `HistorialEstadoService`. Así quedan en los indicadores.
5. La respuesta separa por lote `ofertas`, que son las seleccionables (principal primero, después apoyo), y `ofertasSinHabilitacion`.

Detalles:

- Es idempotente: solo toca ofertas `PENDIENTE`. Volver a abrir la pantalla no consulta a Bonita ni duplica el historial.
- Bonita se lee antes de bloquear, así la llamada HTTP no retiene filas.
- Si Bonita falla en una emergencia, se registra un warning y se listan las demás.
- Se ignora una evaluación que no coincide con el lote o el tipo de recurso de la oferta.
- `PUT /api/ofertas/seleccion` responde `409` si se incluye una oferta sin habilitación.

## Cambios

**Backend**
- `Oferta.validacion` (`@Embeddable ValidacionOferta`): clasificación, nivel de competencia, riesgo máximo, vencimiento, observaciones del SN y fecha de evaluación. Son columnas nuevas `validacion_*` en `ofertas`, que `ddl-auto: update` crea solo.
- Enums nuevos `ClasificacionOferta` y `NivelCompetencia`.
- `ValidacionOfertasService`, que lee Bonita y aplica la regla. `clasificar(...)` es una función pura.
- `GET /api/ofertas/evaluadas`: cada oferta trae `validacion` y cada lote trae `ofertasSinHabilitacion`.

**Frontend** (`SeleccionOfertasPage`)
- Columna "Perfil nacional" con la etiqueta Principal / Apoyo secundario, competencia, riesgo máximo y vencimiento.
- Bloque desplegable con las ofertas sin habilitación y el motivo que informa el SN.
- Arreglo aparte, ya subido a `development` en `2180621`: `App.tsx` tenía una `{` faltante desde el merge `c3b08b7`, y el front no compilaba.

## Pruebas

| Prueba | Resultado |
| --- | --- |
| `ClasificacionOfertaTests`: regla con perfiles reales del SN | 6/6 |
| `ValidacionOfertasIntegrationTests`: endpoint con Bonita simulado | 7/7 |
| Backend completo, `mvn test` | 161/161 |
| Front, `npm run build` | OK |
| Prueba en Bonita con el SN | **Pendiente** |

## Limitaciones conocidas

- **Cobertura parcial:** con `PARCIAL` el caso queda en "Resolver cobertura incompleta", que no tiene endpoint (ajuste 7). No llega a "Evaluar ofertas", así que esas ofertas siguen `PENDIENTE`.
- **CUIT:** una ONG sin CUIT hace fallar el conector de evaluación y deja el caso en incidente. RescueSync no tiene alta de ONGs, solo seeds: hay que cargarlo con `scripts/ongs_cuit.sql` o en el seed.
- **Selección múltiple frente a un ciclo por oferta en Bonita:** sigue igual que antes ([`plan-monitoreo-finalizacion.md`](plan-monitoreo-finalizacion.md), sección 5).
- Las ofertas marcadas `EVALUADA` en `seed-indicadores-demo.sql` no tienen perfil. Se muestran como seleccionables, con "Sin datos de validación".

## Cómo probarlo a mano

1. Desplegar RescueSync 1.2 y levantar el backend, el SN y el front (ver [`merge-bonitaapi-development.md`](merge-bonitaapi-development.md)).
2. En el SN, certificar ALIMENTOS a una ONG con riesgo `CRITICO` y a otra con `MEDIO`. Dejar una tercera sin certificar.
3. Crear una emergencia de gravedad `ALTO` con un lote de ALIMENTOS, cargar las tres ofertas y decidir cobertura `COMPLETA`.
4. Cuando el caso llega a "Seleccionar oferta", entrar como operador municipal en "Seleccionar ofertas". Resultado esperado: la primera ONG como principal, la segunda como apoyo y la tercera en "sin habilitación nacional".

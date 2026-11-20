# E4-02 — Evaluación de cobertura y caminos alternativos en Bonita

> Antecedente del 19/11/2026. E4-09 completa la pantalla, la resolución parcial, la republicación y la recuperación de respuestas inciertas: ver [comportamiento actual](e4-09-correcciones.md). El gateway “¿Quedan ofertas?” también fue corregido en la integración posterior. Las secciones de backend y pendientes de esta guía reflejan la versión original.

Resuelve el ajuste 20 de [`e4-01-ajustes-pendientes.md`](e4-01-ajustes-pendientes.md) (problema 1 de [`e3-10-prueba-integracion.md`](e3-10-prueba-integracion.md)): la tarea **"Evaluar cobertura de lotes"** no tenía contrato y las tres salidas del gateway **"¿Lotes cubiertos?"** compartían la misma condición, por lo que Bonita siempre tomaba "Resolver cobertura incompleta".

> Alcance: proceso y backend. **No incluye pantalla** para el Centro Coordinador (queda en el ajuste 20 de E4-01).

## 1. Modelo en Bonita (`rescue-sync.bos`, pool RescueSync 1.2)

Se implementó sobre la 1.1. Con el merge de E3-10 quedó dentro de la 1.2: ver [`merge-bonitaapi-development.md`](merge-bonitaapi-development.md).

| Elemento | Configuración |
| --- | --- |
| Variable de proceso | `decisionCobertura` (Text, sin valor por defecto) |
| Contrato de "Evaluar cobertura de lotes" | Input `decisionCoberturaInput` (TEXT) |
| Restricción del contrato | `decisionValida`: `decisionCoberturaInput in ["COMPLETA","PARCIAL","SIN_COBERTURA"]` (mensaje "Decisión inválida") |
| Operación de la tarea | `decisionCobertura` = `decisionCoberturaInput` |

Gateway **¿Lotes cubiertos?** (condiciones Groovy):

| Salida | Condición | Destino |
| --- | --- | --- |
| `si` | `decisionCobertura == "COMPLETA"` | Evaluar ofertas |
| `parcial` | `decisionCobertura == "PARCIAL"` | Resolver cobertura incompleta |
| `sin cobertura` (default) | `decisionCobertura == "SIN_COBERTURA"` | Publicar convocatoria (reabre la convocatoria) |

`coberturaCompleta` se conserva: todavía la usa el gateway "¿Quedan ofertas?".

## 2. Backend

`POST /api/emergencias/{id}/cobertura` con `{"decision": "COMPLETA" | "PARCIAL" | "SIN_COBERTURA"}`. Permiso `EVALUAR_COBERTURA` (rol Centro Coordinador). Solo aplica con la emergencia en `CONVOCATORIA_PUBLICADA`.

El servicio compara lo ofrecido contra lo requerido por lote y exige que la decisión sea coherente:

| Decisión | Condición | Efecto |
| --- | --- | --- |
| `COMPLETA` | Todos los lotes cubiertos | Lotes `CUBIERTO`; emergencia `COBERTURA_EVALUADA` |
| `PARCIAL` | Al menos un lote sin cubrir | Lotes `CUBIERTO` o `PARCIALMENTE_CUBIERTO`; emergencia `COBERTURA_PARCIAL` |
| `SIN_COBERTURA` | — | Sin cambios de estado; nuevo plazo de convocatoria |

Luego completa la tarea en Bonita enviando `decisionCoberturaInput`.

Respuestas: `200` registrada; `409` estado inválido o decisión inconsistente con las ofertas; `400` valor fuera del enum; **`502` si Bonita no pudo recibir la decisión** (se revierte la transacción local).

### Cambio de comportamiento

Antes, un fallo al completar la tarea en Bonita solo se registraba como warning y el endpoint respondía `200` con el caso sin avanzar. Ahora se propaga como `BonitaIntegrationException` (502) y no se guarda ningún cambio local. Es distinto de la publicación de la convocatoria, que sigue tolerando el fallo de Bonita.

## 3. Prerrequisito: credenciales del conector

La tarea ejecuta al entrar el conector `consultarOfertasConsolidadas`, que necesita `RESCUESYNC_BONITA_SERVICE_EMAIL` y `RESCUESYNC_BONITA_SERVICE_PASSWORD` en el entorno **del proceso que ejecuta Bonita** (antes de abrir Studio). Sin ellas la tarea queda en estado `failed`, `BonitaClient` no la encuentra (busca `ready`) y la decisión no llega a Bonita. Ver `docs/e3-08-e3-09-continuidad.md` y `rescuesync-backend/README.md`.

## 4. Prueba de punta a punta (19/11/2026)

Bonita Studio 2025.2 / Runtime 10.4, backend con `PLAZO_CONVOCATORIA_MS=20000` para no esperar el timer de 2 días. Un lote requerido de 10 unidades por emergencia.

| Escenario | Ofrecido | Decisión | Estado del backend | Camino en Bonita |
| --- | --- | --- | --- | --- |
| Cobertura completa | 10 | `COMPLETA` | `COBERTURA_EVALUADA` | ¿Lotes cubiertos? → Evaluar ofertas → ¿Quedan ofertas? → Seleccionar oferta |
| Cobertura parcial | 4 | `PARCIAL` | `COBERTURA_PARCIAL` | ¿Lotes cubiertos? → Resolver cobertura incompleta |
| Sin cobertura | 0 | `SIN_COBERTURA` | `CONVOCATORIA_PUBLICADA` | ¿Lotes cubiertos? → Publicar convocatoria |

En los tres casos `decisionCobertura` quedó con el valor enviado. También se comprobó: repetir la decisión sobre una emergencia ya evaluada devuelve `409`, y un valor fuera del enum devuelve `400`.

## 5. Pendiente

- **Gateway "¿Quedan ofertas?"**: sus salidas `si` y `no` tienen la misma condición (`coberturaCompleta`, siempre `true`), así que nunca se toma `no` hacia Fin. No se tocó en E4-02.
- Pantalla de cobertura para el Centro Coordinador y la resolución de cobertura incompleta (ajuste 7 de E4-01).
- Con PARCIAL, el caso queda esperando en "Resolver cobertura incompleta"; no hay endpoint que complete esa tarea.

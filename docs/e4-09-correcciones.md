# E4-09 — Correcciones de perfiles y continuidad funcional

Implementación del 20/11/2026, en `feature/e4-09-correcciones`, sobre `development` (`8ad901b`). Resuelve los ocho hallazgos de la [auditoría](e4-09-verificacion-perfiles.md), siguiendo los responsables y el proceso de la consigna. Mantiene Spring Boot/JPA, React, los permisos existentes y el proceso RescueSync 1.2. No modifica el archivo `.bos` ni agrega módulos funcionales ajenos al trabajo.

## Cambios

| Hallazgo | Corrección |
| --- | --- |
| H1 | El operador municipal solo puede consultar lotes de emergencias de su municipio; la API devuelve 404 para el ajeno. |
| H2 | El CCR dispone de cantidades por lote, fecha de cierre y decisiones de cobertura en la pantalla de lotes, usando los componentes y estilos existentes. |
| H3 | Se valida el vencimiento; la cobertura parcial exige una resolución antes de continuar; la nueva convocatoria completa la publicación en Bonita antes de habilitar otro plazo. |
| H4 | Cada aviso nuevo identifica su oferta. La aplicación completa “Notificar a ONG” para la oferta activa y “Recibir notificación” cuando un destinatario confirma su recepción. Las ofertas siguientes esperan su propio ciclo. |
| H5 | “Mis ofertas” permite consultar y editar ofrecimientos propios mientras estén pendientes y la convocatoria siga abierta. La API vuelve a validar pertenencia, estado y plazo al guardar. |
| H6 | La selección requiere una clasificación nacional principal/apoyo. Una oferta sin validación se muestra y rechaza como no seleccionable; no se fabrican validaciones para datos de demostración. |
| H7 | Login y logout pueden recuperar una sesión con cookie inválida en ambas APIs. Las rutas protegidas siguen rechazando esa cookie. |
| H8 | La edición verifica que la oferta pertenezca al lote indicado en la URL. |

## Cobertura

Las siguientes operaciones requieren el permiso `EVALUAR_COBERTURA` del CCR, salvo la publicación, que conserva `PUBLICAR_CONVOCATORIA`:

| Operación | Uso |
| --- | --- |
| `GET /api/emergencias/{id}/cobertura` | Consulta estado, plazo, cantidades, resolución y sincronización pendiente. |
| `POST /api/emergencias/{id}/cobertura` | Recibe `{"decision":"COMPLETA"}`, `PARCIAL` o `SIN_COBERTURA`. |
| `POST /api/emergencias/{id}/cobertura/resolucion` | Recibe `{"observacion":"..."}` (1–2000 caracteres no blancos) y completa la tarea existente de resolución. |
| `POST /api/emergencias/{id}/convocatoria` | Publicación inicial existente o republicación posterior a `SIN_COBERTURA`. |
| `POST /api/emergencias/{id}/cobertura/sincronizar` | Reconcilia una operación pendiente usando su ID exacto de tarea. |

`COMPLETA` exige cubrir todos los lotes. `PARCIAL` exige ofertas y al menos un lote incompleto. Si falta cobertura, el CCR también puede solicitar una nueva convocatoria. No se permite decidir antes del vencimiento. La resolución parcial registra cómo se atenderá la emergencia con los recursos disponibles y completa la tarea humana prevista; no agrega un circuito de compras ni cambia cantidades automáticamente.

Las operaciones guardan primero la intención con bloqueo de fila, realizan HTTP fuera de la transacción y confirman después. Una respuesta incierta conserva la tarea reservada: el endpoint de decisión devuelve 202 y la pantalla indica que está pendiente. “Actualizar cobertura” consulta el historial; solo reintenta la misma tarea si sigue disponible y pasaron 30 segundos. No publica otro plazo ni ejecuta tareas del siguiente ciclo por su nombre. La selección permanece bloqueada mientras la cobertura o su resolución estén pendientes.

La republicación usa `PLAZO_CONVOCATORIA_MS`, como la publicación inicial. Debe mantenerse consistente con el plazo usado al iniciar el caso en Bonita.

## Recepción de notificaciones

Se conserva `PATCH /api/notificaciones/{id}/leida` y se añade al listado el campo `recepcion`: `NO_REQUIERE`, `SIN_LEER`, `PENDIENTE` o `CONFIRMADA`. La interfaz muestra “Confirmar recepción” para los avisos vinculados a una oferta seleccionada.

La lectura se guarda antes de contactar Bonita. La confirmación de cualquiera de los destinatarios de la ONG alcanza para continuar el proceso; cada usuario conserva su propia fecha de lectura. Los listeners posteriores al commit suspenden la transacción original para que las reservas y confirmaciones persistan en transacciones independientes.

El coordinador verifica la emergencia y la oferta activa en Bonita. Conserva el ID de cada tarea y reconcilia respuestas perdidas incluso si el proceso ya se archivó. La recuperación periódica existente de selección incluye notificaciones pendientes; mantener `bonita.seleccion.recuperacion-habilitada=true` (valor predeterminado de la aplicación). Las tareas demoradas se retoman sin duplicar recepción ni adelantar otra oferta. Los avisos de rechazo no ejecutan tareas de recepción.

## Base de datos y puesta en marcha

Aplicar [scripts/correcciones_e4_09.sql](../scripts/correcciones_e4_09.sql) sobre la base RescueSync antes de iniciar esta versión, además de las migraciones previas de E4-05/E4-06. Es aditivo e idempotente: agrega los campos de seguimiento y la relación única aviso–oferta. No elimina datos ni cambia el esquema del Sistema Nacional.

Los avisos históricos se conservan con `oferta_id` nulo y siguen siendo legibles. No se infiere su oferta a partir del texto. Los casos históricos que ya estén detenidos en tareas de notificación necesitan revisión manual en Bonita; esta implementación automatiza los avisos generados desde la versión corregida. Una cobertura parcial antigua tampoco se da por resuelta automáticamente. Si antes de la corrección ya se decidió `SIN_COBERTURA`, revisar ese caso: la versión anterior no persistía la decisión necesaria para reconocer la republicación pendiente.

No se aplicó la migración sobre la base de trabajo del equipo. No se hizo merge ni push.

## Verificación

- Backend: **207 pruebas, 0 fallos/errores**. Suite existente y regresiones de aislamiento, sesión, edición, cobertura y notificaciones. Incluye requests con transacciones reales para comprobar los listeners después del commit.
- Sistema Nacional: **75 pruebas, 0 fallos/errores**. Suite completa con recuperación de login/logout y rechazo de cookies inválidas en rutas protegidas.
- Frontend: `npm run build` (TypeScript y Vite).
- Migración: ejecutada dos veces en una instancia temporal de PostgreSQL 18, con registros previos; conserva esos registros, aplica los valores predeterminados y crea la relación y el índice único. La instancia temporal quedó detenida.
- Notificaciones: recepción ajena rechazada, lectura anticipada de otra oferta, dos representantes concurrentes, respuesta perdida reconciliada desde archivo y reintento que no ejecuta una tarea distinta.
- Cobertura: plazo vigente rechazado, resolución obligatoria, republicación con nuevo plazo y respuesta perdida sin doble envío.

Los tests integrados usan H2 y un cliente Bonita simulado. Falta validar los caminos completo, parcial y republicación, y dos ofertas consecutivas, con Bonita y el Sistema Nacional reales; también revisar esas pantallas en navegador. Esos pasos son validación del entorno integrado, no funcionalidades adicionales.

Para la prueba real: aplicar la migración, levantar los tres servicios, usar un plazo breve en un caso nuevo y recorrer registro → lotes → ofertas → cobertura → evaluación nacional → selección → recepción → compromiso → monitoreo → finalización → liberación. Repetir la cobertura parcial y la republicación; comprobar que la segunda oferta se procesa solo al terminar la primera.

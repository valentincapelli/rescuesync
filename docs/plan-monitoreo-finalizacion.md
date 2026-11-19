# Plan: completar monitoreo del despliegue y finalización de actividades

Estado: propuesta para implementar; no se modificó código ni el proceso.
Actualización: el alcance implementado y las dependencias pendientes de integración se documentan en [monitoreo-finalizacion.md](monitoreo-finalizacion.md). Este documento conserva el plan original.
Relevamiento: 19/11/2026, `development` en `fe5ccfe` y `feature/bonitaapi` en `3cf2123`.
Se revisaron las ramas locales y las referencias `origin/*` disponibles, sin consultar cambios nuevos del remoto.

## 1. Dependencia de E3-10

**No hace falta mergear `feature/bonitaapi` para empezar la implementación.** Se puede desarrollar desde `development` el modelo de seguimiento, los permisos, los endpoints, las pantallas y las pruebas con un adaptador de Bonita simulado.

**Sí hace falta disponer de E3-10 para validar el recorrido real completo:** compromiso nacional → monitoreo → finalización → liberación nacional. Puede probarse en una rama de integración temporal; no exige que el merge a `development` sea el primer paso. Para entregar el flujo funcionando en `development`, ambas funcionalidades deben quedar integradas.

Evidencia del repositorio:

- El BOS de `development` contiene RescueSync 1.1 y ya tiene las tareas humanas `Monitorear despliegue` y `Finalizar actividad`, ambas con contrato vacío. Las tareas nacionales aún no tienen los conectores de E3-10.
- `feature/bonitaapi` incorpora RescueSync 1.2, evaluación/compromiso/liberación nacionales y selección de una oferta por ciclo. Conserva las dos tareas humanas anteriores.
- `BonitaClient` ya busca y completa tareas. Hay que ampliar la lectura del contexto de cada ciclo y el tratamiento de reintentos; no alcanza con ejecutar una tarea por nombre.
- Ya existen `COMPROMETIDA`, `FINALIZADA`, `EN_EJECUCION` y `CERRADA`, junto con `HistorialEstadoService` y el dashboard que consume ese historial.
- `OfertaService.seleccionarOfertas` ya selecciona varias ofertas, rechaza las restantes y crea notificaciones. No sincroniza esas acciones con Bonita y usa `setEstado` directamente. El relevamiento E4-01 es anterior a parte de ese código.

## 2. Alcance funcional

La página 2 del PDF del TP define que el Centro Coordinador supervisa el despliegue y avance, la ONG informa la finalización y el Sistema Nacional libera los recursos antes de volver al ciclo de selección. El PDF no contiene el grooming de E4 ni detalla campos de seguimiento; los campos y rutas siguientes son propuestas de implementación.

Se implementará:

- Consulta del despliegue por emergencia, lote y oferta/ONG, con estado y últimas novedades.
- Registro de novedades por el Centro Coordinador y una acción explícita para completar `Monitorear despliegue`.
- Consulta de actividades propias por la ONG y finalización con observación de cierre.
- Persistencia de autor, fechas y observaciones, diferenciando finalización informada, liberación confirmada y cierre de emergencia.
- Reflejo de los resultados de compromiso y liberación en RescueSync para que el seguimiento y los indicadores tengan datos reales.

Guardar una novedad no completa la tarea de Bonita. La acción de completar monitoreo habilita la etapa de finalización según la secuencia actual del proceso. No se incorporan mapas, GPS, archivos adjuntos ni porcentajes de avance en esta primera versión.

## 3. Reglas y contratos que fijaremos primero

1. La unidad de seguimiento es una **oferta dentro de un caso**, identificada por `emergenciaId`, `ofertaId` y `bonitaCaseId`. Registrar también los IDs concretos de las tareas ejecutadas para distinguir ciclos y reintentos.
2. Una oferta entra en `COMPROMETIDA` y su emergencia en `EN_EJECUCION` únicamente al confirmar el compromiso exitoso de esa oferta. `SELECCIONADA` no prueba que haya recursos reservados.
3. Solo `CENTRO_COORDINADOR` puede registrar/completar monitoreo. Agregar `MONITOREAR_DESPLIEGUE`.
4. Solo `REPRESENTANTE_ONG` puede finalizar una oferta de su propia ONG. Agregar `FINALIZAR_ACTIVIDAD` y aplicar la restricción tanto a consultas como a acciones.
5. Finalizar requiere compromiso confirmado, monitoreo completado y la tarea `Finalizar actividad` correspondiente al ciclo actual. Cambiar a `FINALIZADA` cuando se confirme esa ejecución; la liberación puede seguir pendiente.
6. La emergencia pasa a `CERRADA` solo tras confirmar las liberaciones de todas las actividades adjudicadas, sin actividades pendientes, y la terminación satisfactoria del caso. Un 404 del caso activo por sí solo no demuestra su finalización.
7. Una liberación fallida conserva la finalización de la actividad y muestra el pendiente/error; no cierra la emergencia ni inventa disponibilidad de stock.
8. Todos los cambios de estado de emergencia, lote y oferta pasan por `HistorialEstadoService.cambiarEstado`. Evitar duplicar historial al repetir una operación.
9. No reinterpretar `EstadoLote.CERRADO` como finalización de asistencia: el modelo actual lo utiliza para adjudicación/compromiso. Mantener esa semántica separada del cierre de emergencia.

## 4. Implementación en orden

### Paso 1 — Base y contrato de integración

- Crear `feature/monitoreo-finalizacion` desde `development` al comenzar la implementación.
- Definir un adaptador de seguimiento sobre `BonitaClient` que consulte oferta actual, compromiso, liberación y estado del caso, y ejecute las dos tareas humanas.
- Usar los nombres de E3-10: `ofertaSeleccionadaId`, `compromisoNacionalJson`, `liberacionNacionalJson` y `ofertasProcesadasJson`.
- Validar referencias y estados de las respuestas; los JSON pueden corresponder al ciclo anterior. Guardar evidencias por oferta para no perder resultados cuando las variables cambien.
- Para desarrollar sin E3-10, simular el adaptador solo en tests. En la aplicación real, un proceso sin esos datos debe indicar que la integración no está disponible y bloquear las transiciones que no puede confirmar.

### Paso 2 — Persistencia, permisos y servicios

- Crear un registro de seguimiento por caso/oferta, con referencias a tareas, confirmación de compromiso, monitoreo, finalización, liberación y estado de sincronización.
- Guardar las novedades en una colección/tabla propia con autor, fecha y texto. Usar un registro de operación pendiente para las acciones que modifican Bonita.
- Implementar los servicios de consulta, registro de novedad, completar monitoreo y finalizar actividad; validar permisos, pertenencia y estado en el backend.
- Proteger acciones concurrentes con control de versión/bloqueo y una clave única por caso, oferta y acción.
- Reutilizar el historial para las transiciones y corregir los `setEstado` del flujo de selección que participa en esta integración, coordinándolo con quien mantiene esa tarea.

### Paso 3 — API propuesta

| Método y ruta | Uso | Acceso |
| --- | --- | --- |
| `GET /api/emergencias/{id}/despliegues` | Listar seguimiento de la emergencia | Centro Coordinador |
| `POST /api/ofertas/{id}/seguimientos` | Registrar una novedad | Centro Coordinador |
| `POST /api/ofertas/{id}/monitoreo/completar` | Completar monitoreo del ciclo actual | Centro Coordinador |
| `GET /api/actividades/mias` | Consultar actividades y pendientes propios | ONG autenticada |
| `POST /api/ofertas/{id}/finalizacion` | Informar el cierre de la actividad | ONG propietaria |

Las respuestas incluyen estado de negocio, estado de sincronización, fechas y acciones disponibles. Una operación aceptada pero todavía no confirmada devuelve estado pendiente, sin presentarse como finalizada. Datos inválidos: 400; permisos insuficientes: 403; estado incompatible: 409. Repetir una acción ya confirmada devuelve su resultado sin ejecutarla otra vez.

### Paso 4 — Sincronización con Bonita y recuperación

- Implementar reconciliación periódica de casos en seguimiento y operaciones pendientes usando el adaptador. Las consultas de pantalla leen el estado persistido y muestran cuándo se confirmó.
- Antes de ejecutar, comprobar el caso, la oferta actual y el ID de tarea; no confiar en un `taskId` enviado por el navegador.
- Persistir primero la intención, ejecutar fuera de una transacción larga y confirmar el resultado en otra transacción. Una transacción SQL no revierte una tarea ya ejecutada en Bonita.
- Si hay timeout, consultar la tarea/actividad archivada antes de reintentar. Conservar el ID concreto para no ejecutar la tarea homónima del siguiente ciclo.
- Reconciliar también cuando una tarea se complete directamente en Bonita. Para casos archivados, verificar la recuperación del resultado final; si las variables necesarias no están accesibles, incorporar una notificación persistente desde el proceso antes de su cierre como ajuste de integración.
- Bonita conserva la responsabilidad de llamar al Sistema Nacional. El backend de seguimiento no vuelve a comprometer ni liberar recursos por su cuenta.
- Mantener inicialmente los contratos vacíos de las dos tareas humanas y guardar los datos de seguimiento en RescueSync. Si hace falta modificar el BOS, aplicar esos cambios sobre 1.2 durante la integración para preservar los conectores.

### Paso 5 — Frontend

- Agregar una sección de despliegues para el Centro Coordinador: emergencia, lotes, ONG, recursos, novedades y acción de completar monitoreo.
- Agregar una sección de actividades propias para la ONG, con historial y formulario de finalización.
- Mostrar pendiente de sincronización/liberación y errores recuperables. Deshabilitar acciones mientras estén pendientes; la autorización definitiva permanece en el backend.
- Refrescar tras cada acción y permitir actualizar el estado. El dashboard existente seguirá consumiendo su contrato actual.

### Paso 6 — Pruebas y entrega

Sin merge de E3-10:

- Tests de servicios/API con Bonita simulado: permisos, ONG ajena, estados inválidos, correlación de oferta/caso y registro del historial.
- Doble clic, dos usuarios concurrentes, timeout después de ejecución exitosa, recuperación tras reinicio y reintento sin duplicados.
- Dos ofertas en una emergencia: terminar la primera no cierra el caso ni afecta la siguiente.
- Compromiso fallido impide iniciar; liberación fallida impide cerrar; una tarea completada fuera de la app se reconcilia.
- Verificar que el historial alimenta suministros movilizados y fecha de cierre sin duplicaciones. Ejecutar pruebas del backend y build del frontend.

Con E3-10 disponible en una rama de integración o ya mergeada:

- Importar/desplegar el BOS integrado y crear casos nuevos con PostgreSQL, Bonita y Sistema Nacional reales.
- Probar compromiso → novedad → completar monitoreo → finalizar como ONG → liberación → siguiente oferta/cierre.
- Comprobar stock exacto, aislamiento entre ofertas, reintentos y actualización del dashboard.
- Documentar configuración, escenarios y resultados. La prueba aislada no se presenta como aprobación del recorrido completo.

## 5. Dependencias y desajustes para la validación completa

- **Selección múltiple frente a ciclo individual:** el backend selecciona un conjunto, pero Bonita 1.2 procesa una oferta por ciclo. Propuesta: mantener el conjunto adjudicado como cola y pasar a Bonita una oferta pendiente por vez; la siguiente debe salir de esa cola, nunca de las rechazadas. Resolver con la tarea de selección antes del E2E.
- **Fin del proceso:** E3-10 permite salir con `continuarSeleccionando=false` aun cuando haya otras ofertas evaluadas. Alinear ese comportamiento con la cola adjudicada y el flujo del PDF; nunca cerrar RescueSync dejando adjudicaciones pendientes.
- **Cobertura y notificaciones:** el informe E3-10 documenta el gateway de cobertura defectuoso. Las notificaciones persistidas tampoco completan por sí solas las tareas de notificación del proceso. Se necesitan esos pasos resueltos para validar el recorrido desde el alta; para pruebas parciales se pueden preparar casos en la etapa necesaria y declarar ese alcance.
- **Historial:** la selección actual modifica estados directamente. Corregirlo para que los indicadores no pierdan transiciones.
- **BOS y script E3-10:** el informe documenta problemas al regenerar el BOS con Python 3.11/3.12. Revisar/corregir el script antes de usarlo para incorporar cambios de proceso.

## 6. Criterio de tarea terminada

El Centro Coordinador puede consultar y registrar el despliegue; la ONG puede finalizar únicamente su actividad; Bonita avanza una sola vez por acción; compromiso, finalización y liberación se reflejan sin estados ficticios; la emergencia cierra solo cuando corresponde; y el dashboard recibe las transiciones. Debe existir evidencia de pruebas automáticas y de al menos un caso real completo con E3-10 integrado.

Referencias: PDF del TP, página 2; `docs/e4-01-ajustes-pendientes.md`; `docs/e3-10-prueba-integracion.md`; `docs/e4-indicadores-contrato.md`; y `docs/e3-10-sistema-nacional.md` en `feature/bonitaapi`.

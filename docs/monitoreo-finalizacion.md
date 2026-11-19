# Monitoreo del despliegue y finalización de actividades

Implementación sobre `development`, en `feature/monitoreo-finalizacion`. Corresponde a las tareas de la página 2 del PDF del TP: el Centro Coordinador supervisa la ayuda, la ONG informa la finalización y Bonita continúa con la liberación de recursos del Sistema Nacional.

## Uso

- **Centro Coordinador → Despliegues:** elegir una emergencia y pulsar **Actualizar**. Consultar las ofertas con compromiso confirmado, registrar novedades y completar el monitoreo con una observación.
- **ONG → Actividades:** consultar las actividades propias. Una vez completado el monitoreo, informar la finalización con una observación de cierre.
- **Actualizar** consulta el avance y recupera solicitudes pendientes. Finalizar una actividad no implica cerrar la emergencia: la pantalla diferencia la finalización de la liberación confirmada.

Se mantienen las pestañas, estilos y componentes de formulario del frontend existente. No se agregan mapas, porcentajes de avance, adjuntos ni nuevas dependencias.

## Requisito para usarlo con Bonita

Debe estar desplegado **RescueSync 1.2 de `feature/bonitaapi` (E3-10)**, con las credenciales y conectores nacionales configurados. La implementación de seguimiento no modifica el BOS ni reemplaza los conectores nacionales. Con el proceso 1.1 de `development`, Actualizar informa la falta de integración y no genera compromisos ni transiciones ficticias.

Las tareas humanas conservan su contrato vacío; las novedades se guardan en RescueSync. El backend usa el `bonitaCaseId` de la emergencia y la oferta seleccionada del ciclo. Las tareas anteriores deben haber llevado el caso hasta **Comprometer recursos**.

Para activar la sincronización periódica y la recuperación automática después de reiniciar:

```text
BONITA_DESPLIEGUES_SYNC_ENABLED=true
BONITA_DESPLIEGUES_SYNC_INTERVAL_MS=30000
```

Está desactivada por defecto hasta disponer del proceso integrado. La actualización explícita de la pantalla siempre está disponible. No hace falta cambiar roles de los usuarios existentes: los permisos se derivan de `Rol` al autenticarse.

Con `spring.jpa.hibernate.ddl-auto=update` (configuración de desarrollo existente), se crean las tablas aditivas `despliegues` y `novedades_despliegue`. No se borran datos ni se modifica el esquema nacional.

## API

Todas las rutas usan la sesión y el formato de errores existentes.

| Método | Ruta | Acceso |
| --- | --- | --- |
| GET | `/api/emergencias/{id}/despliegues` | Centro Coordinador |
| POST | `/api/emergencias/{id}/despliegues/sincronizar` | Centro Coordinador |
| POST | `/api/ofertas/{id}/seguimientos` | Centro Coordinador |
| POST | `/api/ofertas/{id}/monitoreo/completar` | Centro Coordinador |
| GET | `/api/actividades/mias` | ONG, solo actividades propias |
| POST | `/api/actividades/mias/sincronizar` | ONG |
| POST | `/api/ofertas/{id}/finalizacion` | ONG propietaria |

Las tres acciones sobre ofertas reciben `{"observacion":"Texto"}`: texto no vacío, hasta 2000 caracteres. Las acciones de proceso devuelven 202 si quedaron registradas pero falta confirmar su ejecución; 200 si ya se confirmó. Un reintento de la misma acción conserva su autor y observación originales, y no crea otra novedad. Las novedades informativas son entradas independientes.

## Arquitectura y consistencia

- `DespliegueController` expone DTOs y controla permisos; `DespliegueService` coordina las llamadas externas; `DespliegueRegistroService` concentra las transacciones locales y la pertenencia a la ONG.
- `DespliegueBonitaClient` traduce las variables de E3-10 mediante `BonitaClient`. Usa `emergenciaId`, `ofertaSeleccionadaId`, `compromisoNacionalJson` y `ofertasProcesadasJson`, y tareas activas/archivadas. Para casos terminados consulta variables archivadas por el ID original. Referencia: [API oficial de Bonita](https://api-documentation.ofelia.com/0.0.21/).
- `ofertasProcesadasJson` es la evidencia acumulada de liberación del contrato E3-10: el conector solo agrega una oferta después de validar la respuesta nacional. Así se recuperan ciclos anteriores aunque el último JSON de compromiso/liberación haya sido reemplazado.
- Solo se acepta un compromiso de la misma emergencia, oferta y cantidad. Un resultado nacional para una oferta rechazada se informa como inconsistencia de selección.
- Las solicitudes se persisten antes del HTTP, con el ID exacto de tarea. Los reintentos consultan primero el avance y ejecutan únicamente esa tarea si sigue activa; nunca buscan una tarea homónima del ciclo siguiente. Bonita puede rechazar una segunda ejecución concurrente, que se reconcilia consultando el resultado.
- Un bloqueo de la emergencia serializa las escrituras locales. Ninguna transacción SQL queda abierta durante las llamadas HTTP. La intención persiste ante timeouts y se recupera con Actualizar o el sincronizador.
- La oferta cambia a `COMPROMETIDA` al confirmar el compromiso y a `FINALIZADA` al confirmar la tarea o la liberación. La emergencia permanece `EN_EJECUCION` mientras quede alguna adjudicación pendiente. Solo pasa a `CERRADA` con todas las actividades adjudicadas liberadas y el caso archivado en estado `completed`. Un 404 no basta.
- Las transiciones pasan por `HistorialEstadoService`. También se corrigieron los cambios directos de estado en la selección existente para conservar el historial. Las fechas locales indican cuándo RescueSync confirmó cada avance; no reconstruyen la fecha histórica exacta de una operación antigua nunca sincronizada.
- `EstadoLote.CERRADO` conserva su semántica actual de adjudicación; no se utiliza como prueba de finalización de la asistencia.

## Validación y límites

Las pruebas automatizadas usan H2, MockMvc y respuestas controladas de Bonita. Cubren permisos, propiedad de ONG, validación, avance de tareas, concurrencia, timeouts, recuperación de pendientes, dos ofertas, cierre, historial e indicadores. El cliente HTTP se prueba contra `MockRestServiceServer`, incluyendo rutas de variables archivadas y ejecución con contrato vacío.

```bash
mvn -f rescuesync-backend/pom.xml test
cd rescuesync-frontend
npm ci
npm run build
```

La validación real con Bonita y Sistema Nacional queda para la integración de E3-10. No se modificaron las tareas previas de cobertura, selección o notificación: siguen aplicando los desajustes documentados en `e3-10-prueba-integracion.md` y en el plan. En particular, seleccionar varias ofertas en el backend todavía requiere coordinar su procesamiento individual en Bonita. Este seguimiento no decide qué oferta debe procesarse a continuación.

Prueba manual al integrar E3-10:

1. Crear un caso nuevo y llevarlo hasta un compromiso nacional exitoso de una oferta.
2. Actualizar Despliegues como Centro Coordinador, registrar una novedad y completar el monitoreo.
3. Verificar que una ONG ajena no ve ni puede finalizar la actividad; finalizar como su propietaria.
4. Confirmar la liberación nacional, stock y cambios en los indicadores. Repetir con otra oferta sin cerrar anticipadamente la emergencia.
5. Interrumpir la conexión durante una acción y recuperar con Actualizar; comprobar que el historial y el stock no se duplican.

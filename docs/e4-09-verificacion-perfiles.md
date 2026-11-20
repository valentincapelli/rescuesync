# Verificación de perfiles, permisos y funcionalidades

> Informe histórico sobre `8ad901b`. Los hallazgos H1–H8 se corrigieron posteriormente en `feature/e4-09-correcciones`; ver [implementación, migración y pruebas](e4-09-correcciones.md). Los resultados siguientes describen la base auditada, no el estado corregido.

**Fecha:** 20/11/2026. **Base revisada:** `development`, commit `8ad901b`, incluido el merge de E4-05 (selección de ofertas y recuperación con Bonita).

La verificación encontró permisos de rol correctamente asignados en las operaciones principales, pero también un acceso entre municipios y pasos del proceso que todavía no pueden completarse desde la aplicación. **La revisión está realizada; la aplicación todavía requiere correcciones antes de considerar completo el circuito funcional.**

El alcance toma el flujo y los responsables de las páginas 1–2 del PDF `DSSD - TP Integrador - Grupo 21-2.pdf`, la API nacional descrita en su página 5 y las decisiones existentes del repositorio. El aislamiento por municipio y el rol `DIRECTIVO` se contrastan con la decisión D3 de [E4-01](e4-01-ajustes-pendientes.md), no con una regla adicional inventada para esta revisión. El PDF suministrado contiene el grooming hasta E3, no el detalle de E4.

## Perfiles y alcance de acceso

| Perfil | Funciones disponibles | Resultado |
| --- | --- | --- |
| `OPERADOR_MUNICIPAL` | Registrar emergencias; consultar ofertas evaluadas y seleccionarlas | El municipio se obtiene del usuario y la selección consulta la emergencia de ese municipio. La lectura de lotes tiene una excepción indebida: H1. |
| `CENTRO_COORDINADOR` | Crear, editar y publicar lotes; evaluar cobertura por API; consultar y monitorear despliegues | Los permisos existen. Falta la operación de cobertura en la interfaz y completar los caminos alternativos: H2–H3. |
| `REPRESENTANTE_ONG` | Cargar ofertas; editar ofertas propias por API; leer sus notificaciones; finalizar actividades propias | La pertenencia se valida al editar y finalizar. Faltan edición en la interfaz y continuidad de la notificación en Bonita: H4–H5. |
| `DIRECTIVO` | Indicadores, filtros y exportaciones | `CONSULTAR_INDICADORES` está separado de las operaciones del proceso. Las pruebas de autorización y exportación pasan. |
| `SERVICIO_BONITA` | Consultar ofertas consolidadas | No tiene permisos para adjudicar, editar lotes, monitorear ni finalizar. Los tests de la cuenta técnica verifican sus restricciones. |
| Sistema Nacional: `ONG` | Operar recursos propios y consultar su información y certificaciones | Los controles combinan rol y pertenencia mediante `AccesoOng`. Las pruebas de acceso propio/ajeno pasan. |
| Sistema Nacional: `ADMIN` | Gestionar certificaciones; consultas; validación y liberación | No necesita permiso de creación de compromisos: esa operación corresponde a `SERVICIO` en el diseño actual. |
| Sistema Nacional: `SERVICIO` | Consultar capacidades, validar certificaciones, comprometer y liberar recursos | Las pruebas de permisos, concurrencia e idempotencia pasan. |

La navegación de [App.tsx](../rescuesync-frontend/src/App.tsx) filtra las secciones por rol. La autorización de las operaciones se comprueba también en el backend; ocultar botones no es el único control. No hace falta agregar roles nuevos para resolver los hallazgos.

## Cambios necesarios

### H1 Alta prioridad — Lectura de lotes de otro municipio

**Evidencia reproducida:** con un usuario del municipio A, `GET /api/emergencias/{emergenciaDeB}/lotes` devuelve **200** y los lotes del municipio B. El mismo usuario puede consultar sus lotes correctamente.

[LoteController.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/controller/LoteController.java), línea 49, no recibe al usuario. [LoteService.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/service/LoteService.java), línea 53, solo comprueba que exista la emergencia. El permiso `CONSULTAR_LOTES` del municipio termina habilitando cualquier ID.

**Cambio acotado:** comprobar el municipio del usuario en el servicio antes de devolver los lotes, conservando el acceso que corresponda al CCR y a las ONGs. La consulta ajena debe responder 403 o 404, sin datos. Esto cumple la restricción D3 ya acordada.

### H2 Alta prioridad — El CCR no puede evaluar cobertura desde la interfaz

El endpoint `POST /api/emergencias/{id}/cobertura` y el permiso `EVALUAR_COBERTURA` existen y funcionan en las pruebas. Sin embargo, [LotesPage.tsx](../rescuesync-frontend/src/pages/LotesPage.tsx), línea 278, deja los lotes en modo de lectura luego de publicar; [api/emergencias.ts](../rescuesync-frontend/src/api/emergencias.ts) no ofrece una operación de cobertura y ninguna pantalla llama al endpoint.

**Impacto:** aun con cobertura completa, el CCR necesita una llamada manual fuera de la aplicación para seguir el flujo descrito en la página 2 del PDF. La documentación de [E4-02](e4-02-cobertura.md) también reconoce que la pantalla quedó pendiente.

**Cambio acotado:** incorporar a la vista del CCR el resumen requerido/ofrecido por lote y la decisión de cobertura, respetando el plazo de convocatoria y las validaciones del backend. Se pueden reutilizar los componentes y estilos de lotes.

### H3 Alta prioridad — Cobertura parcial y republicación no tienen continuidad completa

En `rescue-sync.bos`, `PARCIAL` conduce a la tarea humana **Resolver cobertura incompleta**. No hay operación de la aplicación que la complete, por lo que el caso no llega a **Evaluar ofertas** por ese camino.

Para `SIN_COBERTURA`, [EmergenciaService.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/service/EmergenciaService.java), línea 268, renueva el plazo local y completa **Evaluar cobertura de lotes**. Bonita vuelve a **Publicar convocatoria**, pero la emergencia local sigue en `CONVOCATORIA_PUBLICADA`. El endpoint de publicación, línea 181, exige que el desglose continúe abierto.

**Evidencia reproducida:** decidir `SIN_COBERTURA` devuelve **200**; intentar publicar nuevamente devuelve **409**. La prueba verifica la llamada de evaluación a un cliente Bonita simulado. El retorno a la tarea de publicación se comprobó en el modelo exportado, no en un runtime durante esta auditoría.

**Cambio acotado:** permitir la republicación en ese punto concreto del proceso y completar la tarea correcta; conectar también la resolución de cobertura parcial. Las fechas de recepción local y el timer de Bonita deben corresponder al mismo ciclo. No alcanza con habilitar nuevamente el botón de publicación.

### H4 Alta prioridad — Las notificaciones no hacen avanzar las tareas de Bonita

E4-05 ya incorpora una intención persistida por oferta y ejecuta **Seleccionar oferta** mediante [SeleccionBonitaCoordinador.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/service/SeleccionBonitaCoordinador.java), línea 105. Esa integración ya no está pendiente.

El modelo exportado conserva esta secuencia de tareas: **Seleccionar oferta → Notificar a ONG → Recibir notificación → Comprometer recursos**. Las dos tareas de notificación son humanas. La aplicación crea los avisos locales al adjudicar, pero [NotificacionService.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/service/NotificacionService.java), línea 83, solo registra la fecha de lectura y no ejecuta la recepción en Bonita. Tampoco hay una llamada que complete **Notificar a ONG**.

**Impacto según el código y el modelo:** seleccionar y leer el aviso en la web no basta para llegar al compromiso nacional ni al inicio del despliegue; aún hace falta completar las tareas manualmente en Bonita.

**Cambio acotado:** asociar la notificación con la oferta/caso correspondiente y conectar envío y recepción con las tareas previstas, conservando el control por destinatario y evitando ejecutar dos veces una misma tarea. Reutilizar el mecanismo de coordinación existente. No requiere agregar correo electrónico ni otro canal de notificaciones.

### H5 Prioridad media — La ONG no puede editar sus ofertas desde la web

La página 2 del PDF permite cargar y editar ofertas durante la convocatoria. [OfertasPage.tsx](../rescuesync-frontend/src/pages/OfertasPage.tsx), líneas 77 y 89, solo presenta el formulario de alta. [api/ofertas.ts](../rescuesync-frontend/src/api/ofertas.ts) no tiene operación de edición ni listado de ofertas propias pendientes.

La API sí implementa edición y valida ONG propietaria, estado del lote y fecha de cierre. Además, rechaza una segunda oferta de la misma ONG al mismo lote: volver a cargarla no reemplaza la edición.

**Cambio acotado:** listar los ofrecimientos propios e incorporar edición con el formulario y estilos existentes, habilitada únicamente mientras la convocatoria permita modificarla. El listado debe filtrar por la ONG autenticada en el servidor.

### H6 Prioridad media — Una oferta sin resultado nacional puede adjudicarse

[OfertaEvaluadaResponse.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/dto/ofertas/OfertaEvaluadaResponse.java), línea 43, considera seleccionable una oferta con `validacion == null`. [OfertaService.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/service/OfertaService.java), línea 351, solo rechaza una clasificación no habilitada cuando la validación y su clasificación existen.

**Evidencia reproducida:** una oferta en estado `EVALUADA`, pero con todos los campos de validación vacíos, recibe **200** al seleccionarla. Una oferta explícitamente `SIN_HABILITACION` sí se rechaza en la suite existente.

La excepción está documentada para datos de demostración, pero se aplica en el servicio general. No significa que una ONG pueda cambiar por API su oferta a `EVALUADA`: el caso afecta datos de demo, migrados o incompletos que ya estén en ese estado.

**Cambio acotado:** exigir una clasificación nacional admisible para adjudicar y completar los datos de validación de los seeds que necesiten demostrar la selección, manteniendo el criterio actual principal/apoyo.

### H7 Prioridad media — Una cookie inválida impide recuperar la sesión

[JwtAuthenticationFilter.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/security/JwtAuthenticationFilter.java), línea 98, corta la cadena con 401 ante un JWT inválido, también para las rutas públicas de login y logout.

**Evidencia reproducida:** credenciales válidas dan **200** sin cookie; las mismas credenciales con `access_token=token-invalido` dan **401**. El logout con esa cookie también da **401**, sin llegar a borrarla. No se afirma que ocurra en toda expiración normal: aplica cuando el navegador todavía envía una cookie inválida o vencida.

El filtro del Sistema Nacional tiene el mismo comportamiento por inspección del código; la reproducción HTTP puntual se hizo en RescueSync.

**Cambio acotado:** permitir que login y logout procesen la recuperación de sesión aun cuando la cookie anterior sea inválida, manteniendo el rechazo de tokens inválidos en las rutas protegidas. Aplicar el mismo criterio a ambas APIs.

### H8 Prioridad baja — La edición de ofertas ignora el lote de la URL

[OfertaController.java](../rescuesync-backend/src/main/java/com/grupo21/rescuesync/controller/OfertaController.java), línea 56, publica `PUT /api/lotes/{loteId}/ofertas/{ofertaId}` pero solo utiliza `ofertaId`.

**Evidencia reproducida:** editar una oferta propia usando en la URL el ID de otro lote devuelve **200**. La comprobación de ONG propietaria sigue funcionando, por lo que este hallazgo no permite editar ofertas ajenas.

**Cambio acotado:** validar la relación lote/oferta de la ruta y responder 404 si no coincide, como ya hace el servicio de lotes con emergencia/lote.

## Estado del circuito funcional

| Etapa de la consigna | Estado revisado |
| --- | --- |
| Registrar emergencia e iniciar caso | Implementada; si Bonita falla, el alta puede quedar persistida sin caso. Ver observaciones siguientes. |
| Desglosar y publicar lotes | Implementada con interfaz, API y validaciones de estado. |
| Cargar y editar ofertas durante el plazo | Alta completa; edición disponible solo por API. H5. |
| Evaluar cobertura y resolver alternativas | API y condiciones del gateway presentes; falta interfaz y continuidad de alternativas. H2–H3. |
| Evaluar ofertas en el Sistema Nacional | Conector integrado y sincronización de resultados implementada. H6 afecta el caso sin perfil persistido. |
| Seleccionar ofertas | Implementada con aislamiento del municipio, notificaciones e intenciones de sincronización con Bonita. |
| Notificar y recibir la notificación | Avisos y lectura locales implementados; continuidad en Bonita pendiente. H4. |
| Comprometer recursos | API y conector implementados; el recorrido desde la web está condicionado por H4. |
| Monitorear y finalizar actividades | Interfaz, permisos, control de ONG, historial y recuperación implementados; pruebas con Bonita simulado aprobadas. |
| Liberar recursos y continuar o cerrar | API, conector y coordinación implementados; falta validar el circuito completo con el runtime integrado actual. |
| Indicadores y exportaciones | Implementados y probados; asignados a `DIRECTIVO` según D3. |

## Observaciones acotadas

- **Acceso de ONGs a datos consolidados:** `GET /api/emergencias/{id}/ofertas-consolidadas` también admite a `REPRESENTANTE_ONG` por su permiso `CONSULTAR_EMERGENCIAS` y devuelve ofertas de otras organizaciones. El frontend no lo usa. La documentación consultada no define que esas ofertas deban ser confidenciales, por lo que no se clasifica como una infracción confirmada; conviene explicitar si esa lectura está prevista y ajustar el permiso si solo corresponde a CCR y Bonita.
- **Recuperación de alta y publicación:** `EmergenciaService.iniciarProcesoBonita` y `sincronizarPublicacionConBonita` registran el fallo y permiten confirmar el cambio local. Si Bonita estaba caído, la aplicación puede parecer avanzada sin que el caso lo esté. Conviene hacer visible esa situación y ofrecer una recuperación controlada; no es necesario reemplazar la arquitectura ni agregar infraestructura nueva.
- **Documentación histórica:** E4-01 conserva permisos y funcionalidades como pendientes que ya están implementados. E4-02 todavía describe el antiguo problema del gateway “¿Quedan ofertas?”, corregido en la integración posterior. Para decidir trabajo restante, usar este relevamiento y el código del commit indicado, conservando aquellas guías como antecedentes.

## Pruebas ejecutadas

| Componente | Resultado final |
| --- | --- |
| Backend RescueSync, incluido E4-05 | **189 tests**, 0 fallos, 0 errores, 0 omitidos |
| Sistema Nacional | **74 tests**, 0 fallos, 0 errores, 0 omitidos |
| Conector del Sistema Nacional | **20 tests**, 0 fallos, 0 errores, 0 omitidos |
| Conector de ofertas consolidadas | **12 tests**, 0 fallos, 0 errores, 0 omitidos |
| Frontend | `npm run build` aprobado sobre el último merge |

Los comandos fueron `mvn -q -f <componente>/pom.xml test` para cada módulo y `npm run build` en `rescuesync-frontend`. Los tests de backend emplean H2; los conectores emplean servidores HTTP de prueba. Se habilitó la ejecución fuera del sandbox donde Mockito o el servidor local lo requerían.

Además, se ejecutaron **9 pruebas temporales de auditoría**, con fixtures aislados y aserciones del comportamiento esperado:

| Caso | Esperado | Observado |
| --- | --- | --- |
| Municipio A consulta lotes de B | 403 o 404 | **200** — H1 |
| Login válido con cookie inválida anterior | 200 | **401** — H7 |
| Logout con cookie inválida | 204 y borrado de cookie | **401** — H7 |
| Adjudicar una oferta evaluada sin validación | 409 | **200** — H6 |
| Editar con lote y oferta que no corresponden | 404 | **200** — H8 |
| Republicar luego de `SIN_COBERTURA` | Continuidad de publicación | **409** — H3 |
| ONG intenta editar una oferta ajena | Rechazo sin cambiar cantidad | **409**, cantidad intacta |
| Cobertura con cada rol y sin sesión | Solo CCR autorizado | **200** CCR, **403** otros cuatro roles, **401** sin sesión |
| ONG consulta y marca avisos ajenos | Solo avisos propios; rechazo de marca ajena | Listado filtrado y **404** al marcar la ajena |

Las **6 aserciones fallidas** de auditoría confirman los desvíos descritos; no son fallos de preparación de las pruebas. Las otras 3 pasaron. Estas pruebas de diagnóstico se retiraron del directorio de tests habitual; su fuente y resultados se conservaron localmente en `/private/tmp/rescuesync-e4-09-evidencia/` para repetir las reproducciones al corregirlas. No se modificó lógica de negocio ni configuración de la aplicación.

**Límite de la validación:** no se ejecutó una sesión de navegador ni el circuito completo con Bonita real y PostgreSQL en esta revisión. No había un runtime escuchando en el puerto local 8080. Las conclusiones de continuidad de Bonita se basan en el modelo exportado y las llamadas presentes/ausentes en el código; los resultados históricos de otras guías no sustituyen esa prueba sobre el estado integrado actual.

## Orden de corrección y cierre

1. Corregir el aislamiento municipal H1 y la recuperación de sesión H7.
2. Completar cobertura y sus alternativas H2–H3, y conectar las notificaciones con Bonita H4: son los cortes del flujo principal.
3. Completar la edición de ofertas H5 y corregir validación y relación de IDs H6–H8.
4. Repetir las reproducciones afectadas y las suites correspondientes.
5. Ejecutar el circuito real completo con dos municipios y dos ONGs: cobertura completa, parcial y republicación; selección de varias ofertas; recepción; compromiso; monitoreo; finalización; liberación y cierre. Verificar stock, historial, aislamiento y ausencia de tareas duplicadas.

Los cambios propuestos completan responsabilidades del proceso existente y corrigen validaciones. No requieren nuevos perfiles, un rediseño visual, microservicios adicionales ni funcionalidades ajenas a la consigna.

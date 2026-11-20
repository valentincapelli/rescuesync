# Integración Bonita con el Sistema Nacional — E3-10

E3-10 conecta las tareas **Evaluar ofertas**, **Comprometer recursos** y **Liberar recursos** del proceso existente con la API del Sistema Nacional. Se apoya en E3-05/06/07 y en el JSON que obtiene E3-09. Conserva la estructura de conector Maven independiente usada por E3-09.

El archivo `rescue-sync.bos` contiene **RescueSync 1.2**, con los tres conectores de entrada y la selección de una oferta por ciclo. El módulo fuente es `bonita-sistema-nacional-connector/`. La configuración del archivo se puede revisar y reproducir con `scripts/configurar_bonita_e3_10.py`; no requiere editar un workspace de Studio ni despliega procesos.

## Alcance y recorrido

| Tarea | Operación sobre el Sistema Nacional | Resultado en el proceso |
| --- | --- | --- |
| Evaluar ofertas | `POST /api/certificaciones/validar` por CUIT y tipo de recurso de cada oferta | `ofertasEvaluadasJson`: conserva los lotes y ofertas y agrega `validacion` con el perfil nacional a cada oferta |
| Seleccionar oferta | Tarea humana del municipio: elige un ID del array evaluado | `ofertaSeleccionadaId` y `continuarSeleccionando` |
| Comprometer recursos | Busca la ONG por CUIT con `GET /api/ongs`, identifica su recurso con `GET /api/ongs/{ongId}/recursos` y llama a `POST /api/compromisos` | `compromisoNacionalJson`, con el compromiso de la oferta elegida |
| Liberar recursos | `POST /api/compromisos/liberar` con `emergenciaRef` y `ofertaRef` | `liberacionNacionalJson`, agrega la oferta a `ofertasProcesadasJson` y calcula `hayOfertasPendientes` |

La evaluación informa capacidades, niveles y observaciones. Una lista `capacidadesHabilitadas: []` se conserva como información para el municipio; no rechaza ni borra ofertas automáticamente. El compromiso requiere que exista una ONG nacional habilitada y un recurso compatible. La API nacional verifica el stock dentro de su transacción.

Se compromete la cantidad completa de la oferta elegida. La coincidencia del inventario es por tipo de recurso y unidad, con la misma normalización del servicio nacional: minúsculas, espacios recortados y espacios interiores repetidos reducidos. No se convierten unidades (`kg` y `kilogramos` siguen siendo diferentes). Los IDs locales de ONG o lote no se utilizan como IDs nacionales de recurso.

Después de **Finalizar actividad** se libera esa misma oferta. El gateway de selección vuelve al municipio únicamente si quedan ofertas pendientes y el municipio eligió continuar. Sus dos salidas, que antes repetían `coberturaCompleta`, ahora usan condiciones opuestas sobre `hayOfertasPendientes && continuarSeleccionando`.

Este inciso no agrega pantallas web, envío de notificaciones, adjudicación automática, cambios de estados de ofertas en el backend, infraestructura ni despliegue cloud. Las tareas humanas de notificación, monitoreo y finalización siguen siendo las del proceso previo. La cobertura inicial de lotes conserva su lógica anterior; su eventual ajuste es independiente de estos conectores.

## 1. Identificación de ONGs por CUIT

El backend ahora incorpora `Ong.cuit` y lo devuelve en `ong: {id, nombre, cuit}` dentro de las ofertas consolidadas. Es el dato común entre ambas bases. El campo admite `null` en registros anteriores para poder actualizar sin inventar identidades; **antes de evaluar ofertas, completar el CUIT de cada ONG participante**, con 11 dígitos sin guiones.

Para una PostgreSQL existente de **RescueSync**, desde la raíz:

```bash
psql -h localhost -p 5433 -U rescuesync -d rescuesync \
  -v ON_ERROR_STOP=1 -f scripts/ongs_cuit.sql
```

El script es aditivo e idempotente: agrega la columna y su índice único, completa solamente las dos ONGs de demostración que coinciden con el seed nacional y lista las que todavía carecen de CUIT. Para las demás, cargar el CUIT verificado por ID en esa base. No ejecutar este script en la base nacional. El seed de demostración del backend también incorpora esos CUIT.

Un CUIT faltante o inválido provoca un incidente antes de consultar la API; el conector no intenta asociar por nombre o por IDs de bases distintas. La API nacional debe contener las ONGs, certificaciones e inventario correspondientes. E3-10 no carga stock ni certificaciones de oficio.

## 2. Credenciales y conectividad

Usar Studio **2025.2-u0 / Runtime 10.4.0**. Levantar el backend en 8081 y el Sistema Nacional en 8082, cada uno con su base. Mantener la configuración técnica de E3-09 según la [guía anterior](e3-08-e3-09-continuidad.md).

En el entorno del proceso que inicia **Bonita Runtime**, agregar:

```text
SISTEMA_NACIONAL_BONITA_USERNAME
SISTEMA_NACIONAL_BONITA_PASSWORD
```

Son las credenciales de una cuenta **SERVICIO ya existente en la API nacional**, creada por su configuración/seed. El conector exige ese rol porque el endpoint de compromiso lo requiere. El usuario nacional se autentica mediante `username`, mientras que la cuenta técnica del backend usa `email`; son cuentas y sesiones independientes aunque ambas APIs llamen `access_token` a su cookie.

Guardar los valores en la configuración local de arranque o gestor de secretos; no en el BOS, diagrama, POM, documentación ni archivos versionados. Reiniciar Studio/Runtime después de cambiar su entorno. Las variables anteriores `RESCUESYNC_BONITA_SERVICE_EMAIL` y `RESCUESYNC_BONITA_SERVICE_PASSWORD` continúan siendo necesarias para E3-09.

La variable de proceso `sistemaNacionalBaseUrl` inicia en `http://localhost:8082`. Cambiar ese valor inicial al origen alcanzable desde Bonita cuando la API esté en otro host. Desde un contenedor, `localhost` apunta al propio contenedor. No incluir `/api`, credenciales, query ni fragmentos en esa URL.

## 3. Construir e importar

Desde la raíz, con JDK 21 y Maven:

```bash
mvn -f bonita-ofertas-consolidadas-connector/pom.xml install
mvn -f bonita-sistema-nacional-connector/pom.xml clean install
python3 scripts/configurar_bonita_e3_10.py
python3 scripts/configurar_bonita_e3_10.py --check
```

El script reconstruye la configuración E3-10 del modelo 9 y actualiza el JAR/POM de esta extensión dentro del BOS. También deja explícitos los mapeos de implementación de ambos conectores en la configuración del proceso y alinea Jackson con la versión 2.17.2 de E3-09 para que el build incluya todas las dependencias declaradas. Conserva la organización, los actores y el conector E3-09. No ejecutarlo sobre un diagrama que haya recibido cambios manuales en las mismas variables/tareas sin revisar antes cómo combinarlos. `--check` verifica la configuración sin modificar el archivo.

En Studio: **File → Import → BOS Archive…**, seleccionar `rescue-sync.bos` y resolver los conflictos. Instalar las extensiones por sus coordenadas Maven si Studio usa un repositorio distinto; ambos módulos deben tener JAR y POM disponibles. Ver [README del conector](../bonita-sistema-nacional-connector/README.md).

Revisar y desplegar **RescueSync 1.2**. Para crear casos desde el backend, dejar habilitada solamente la versión prevista de `RescueSync`: el cliente actual busca por nombre y toma la primera habilitada. La importación/despliegue no actualiza los casos ya iniciados en 1.1; probar con una emergencia y un caso nuevos.

## 4. Configuración incluida en el proceso

Los tres conectores están en **Connectors in / ON_ENTER**, con propagación del error (`ignoreErrors=false`). Comparten `emergenciaId`, `sistemaNacionalBaseUrl` y `ofertasProcesadasJson`.

| Tarea | `operacion` | `ofertasJson` | Salida `resultadoJson` |
| --- | --- | --- | --- |
| Evaluar ofertas | `EVALUAR` | `ofertasConsolidadasJson` | `ofertasEvaluadasJson` |
| Comprometer recursos | `COMPROMETER` | `ofertasEvaluadasJson` | `compromisoNacionalJson` |
| Liberar recursos | `LIBERAR` | `ofertasEvaluadasJson` | `liberacionNacionalJson` |

Comprometer y liberar reciben también `ofertaSeleccionadaId`. Evaluar y liberar asignan además las salidas `ofertasProcesadasJson` y `hayOfertasPendientes` a las variables del mismo nombre. Los tres JSON de resultados son `String`; los arrays inician en `[]` y los objetos de compromiso/liberación en `{}`. `ofertasProcesadasJson` guarda IDs de ofertas cuya actividad ya finalizó, no todos los IDs consultados.

La tarea humana **Seleccionar oferta** incorpora este contrato mínimo:

```json
{
  "ofertaSeleccionadaIdInput": 10,
  "continuarSeleccionandoInput": false
}
```

Sustituir `10` por un ID de oferta presente en `ofertasEvaluadasJson`. La operación de la tarea verifica que el ID exista y no haya finalizado antes de pasar a la notificación. `false` significa terminar después de procesar **esta** oferta; `true` significa volver a seleccionar otra al finalizar la actual si todavía quedan ofertas. No omite el compromiso ni la liberación de la oferta elegida.

Para pruebas, ejecutar la tarea con esas entradas mediante las herramientas de desarrollo de Studio o la API de ejecución de tareas de Bonita, usando una sesión autorizada para esa tarea. El formulario/pantalla de selección de la aplicación web pertenece a otro trabajo; el contrato permite probar la integración sin agregar esa pantalla.

## 5. Errores y reintentos

- Credenciales ausentes, login sin cookie o rol distinto de `SERVICIO`: incidente; no se consulta el inventario ni se modifica stock.
- Respuesta de certificaciones sin capacidades: evaluación válida con observaciones, sin rechazo automático.
- ONG o recurso sin coincidencia única: incidente. Completar/corregir el dato de origen; no elegir un recurso arbitrario.
- Stock insuficiente (`409`): incidente; no se publica un compromiso exitoso ni se avanza al monitoreo.
- Fallo de conexión, timeout, `429` o `5xx`: hasta tres intentos con el mismo payload. Ante `401`, renovar sesión una vez. Ver los tiempos en el README del módulo.
- Respuesta perdida después de comprometer: el reintento conserva `recursoId`, `ofertaRef`, `emergenciaRef` y cantidad. La API reutiliza su compromiso y el conector comprueba que la respuesta coincida. Liberar nuevamente la misma oferta tampoco repone stock dos veces.

Un incidente debe resolverse antes de continuar el caso. Los conectores no publican salidas parciales ni guardan tokens/contraseñas en variables de proceso.

## 6. Cómo probar

### Pruebas automáticas

```bash
mvn -f bonita-sistema-nacional-connector/pom.xml test
mvn -f rescuesync-backend/pom.xml \
  -Dtest=OfertasConsolidadasIntegrationTests,BonitaServiceAccountIntegrationTests test
python3 scripts/configurar_bonita_e3_10.py --check
```

Resultado verificado: **20 pruebas del conector y 9 del backend, sin fallos**. Las del conector usan un servidor HTTP controlado; las del backend usan H2. Cubren el contrato de la integración y la publicación del CUIT, pero no sustituyen una ejecución dentro de Bonita.

También se extrajo el BOS en un directorio temporal y se construyó con el plugin oficial de Bonita: **`mvn package -Dbonita.allowEmptyFormMapping=true`, exitoso**. Se inspeccionó el BAR resultante: contiene los cuatro conectores del flujo (uno E3-09 y tres E3-10), sus dos implementaciones y todos los JAR declarados por ambas. Se ejecutó el script Groovy de selección con una oferta válida, una ajena y una ya finalizada. El JAR del BOS coincide con el módulo probado.

La opción `allowEmptyFormMapping` permite comprobar el paquete conservando los formularios sin vincular del diagrama previo. Maven advierte que esos mapeos deben resolverse antes de habilitar un despliegue: en Studio, configurar el formulario de cada tarea o **sin formulario** si se va a ejecutar mediante API. Para seleccionar por API, usar las dos entradas del contrato documentado; este build no crea pantallas.

Si Maven informa que no existe el `settings.xml` del usuario al construir las dependencias del proceso, proporcionar uno válido mediante `mvn -s /ruta/settings.xml package -Dbonita.allowEmptyFormMapping=true`. Para esta comprobación se usó un archivo de configuración vacío en un directorio temporal, sin cambiar la configuración personal.

La suite completa anterior del backend tiene fallos documentados de autenticación y configuración de JWT; no se modifican en este inciso. Ver la sección de verificación en la guía E3-08/E3-09.

### Prueba manual con Studio y la API nacional

1. Completar CUIT en RescueSync, preparar una ONG nacional habilitada con inventario del mismo tipo y unidad que el lote y anotar su stock disponible. Configurar las dos cuentas técnicas e importar/desplegar 1.2.
2. Crear una emergencia nueva, un lote y dos ofertas identificables. Dejar vencer la convocatoria y completar las tareas humanas previas según el flujo existente hasta **Evaluar ofertas**.
3. Verificar `ofertasEvaluadasJson`: mismos lotes/ofertas, con `validacion` por CUIT y tipo. Una certificación ausente o vencida debe aparecer como capacidad vacía con observaciones; repetir también con una certificación vigente.
4. En **Seleccionar oferta**, enviar el ID de una sola oferta con el contrato de arriba. Completar las tareas de notificación del proceso. Verificar que **Comprometer recursos** deja `compromisoNacionalJson` con esa `ofertaRef`, la `emergenciaRef` actual, el recurso nacional correcto y estado `COMPROMETIDO`.
5. Consultar el inventario nacional: debe aumentar `cantidadComprometida` y reducir `cantidadDisponible` exactamente en la cantidad de esa oferta; la otra oferta no se compromete automáticamente.
6. Completar **Monitorear despliegue** y **Finalizar actividad**. Verificar `liberacionNacionalJson`, estado `LIBERADO` y recuperación del stock una sola vez. La oferta pasa a `ofertasProcesadasJson`.
7. Con `continuarSeleccionandoInput=true`, confirmar que se puede elegir la otra oferta y que no se puede volver a elegir una finalizada. Con `false`, el caso sigue la salida de cierre después de liberar la elegida.
8. Repetir en datos de prueba con una cantidad mayor al stock: el compromiso debe quedar en incidente por `409`, sin reserva parcial ni avance. Una caída temporal de la API debe agotar los reintentos y dejar un incidente recuperable.

No se ejecutaron migraciones sobre bases reales ni se desplegó un caso durante esta implementación. La prueba manual anterior queda para el entorno de Studio del equipo; las pruebas globales de integración/concurrencia y el flujo completo corresponden a E3-14.

## Referencias de Bonita

- [Estructura de conectores y compatibilidad de versiones](https://documentation.ofelia.com/bonita/2025.2/process/connector-archetype).
- [Construcción de proyectos por Maven](https://documentation.ofelia.com/bonita/2025.2/build-run/build-application): permite comprobar un proyecto extraído del BOS con `mvn package -Dbonita.allowEmptyFormMapping=true` cuando sus tareas aún no tienen formularios vinculados.

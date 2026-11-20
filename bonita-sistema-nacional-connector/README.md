# Conector Bonita: Sistema Nacional (E3-10)

Módulo independiente con la misma estructura que `bonita-ofertas-consolidadas-connector`: Java 17, Maven y `bonita-common` 10.4.0, para Studio 2025.2-u0 / Runtime 10.4.0. Usa los servicios existentes de certificaciones, inventario, compromiso y liberación.

## Construir e instalar

Con JDK 17 o 21, desde este directorio:

```bash
mvn clean package
mvn install
```

El JAR queda en `target/bonita-sistema-nacional-connector-1.0.0-SNAPSHOT.jar`. El ZIP `*-impl.zip` incluye la implementación y sus dependencias Jackson. Para agregar la extensión en Studio usar **Extensions → Add → From Maven repository**:

```text
com.grupo21.rescuesync.bonita:bonita-sistema-nacional-connector:1.0.0-SNAPSHOT
```

Instalar JAR y POM en el repositorio que usa Studio permite resolver también las dependencias transitivas. El proceso ya configurado está en `../rescue-sync.bos`.

## Entradas y salidas

| Entrada | Tipo | Uso |
| --- | --- | --- |
| `operacion` | String | `EVALUAR`, `COMPROMETER` o `LIBERAR` |
| `sistemaNacionalBaseUrl` | String | Origen HTTP(S), por ejemplo `http://localhost:8082` |
| `emergenciaId` | Long | ID positivo de la emergencia de RescueSync |
| `ofertasJson` | String | Array de lotes con ofertas consolidadas para evaluar; array evaluado para comprometer o liberar |
| `ofertaSeleccionadaId` | Long | ID de la oferta elegida; obligatorio al comprometer y liberar |
| `ofertasProcesadasJson` | String | Array de IDs ya finalizados, inicialmente `[]`; obligatorio al comprometer y liberar |

`resultadoJson` es el array enriquecido de ofertas, el compromiso o la liberación, según la operación. `ofertasProcesadasJson` y `hayOfertasPendientes` se actualizan al evaluar y al liberar. Las salidas se publican únicamente cuando toda la ejecución termina correctamente.

La selección se resuelve por ID de oferta. Para comprometer, la ONG se identifica en el Sistema Nacional por **CUIT** y su recurso por **tipo + unidad normalizada**. Los IDs de ONG de ambas bases pueden ser distintos. Se compromete la cantidad completa de esa oferta; no se reparten cantidades ni se adjudica automáticamente.

## Autenticación y errores

Definir en el entorno que arranca **Bonita Runtime**:

```text
SISTEMA_NACIONAL_BONITA_USERNAME
SISTEMA_NACIONAL_BONITA_PASSWORD
```

Corresponden a un usuario existente de rol `SERVICIO` del Sistema Nacional. El login envía `username` y `password`; mantiene la cookie `access_token` en memoria, en una sesión independiente de la del backend. No guardar credenciales en parámetros del proceso, el BOS ni archivos versionados.

Cada ejecución inicia su propia sesión. Cada llamada permite hasta tres intentos ante fallos de conexión, timeout, 429 o 5xx, con pausas de 200 y 400 ms; el timeout por llamada es de 90 s y el de conexión, 5 s. Ante un 401 se renueva la sesión una sola vez. Otros 4xx, contratos inválidos y respuestas que no coinciden con las referencias enviadas hacen fallar el conector. Bonita debe mantener ese fallo como incidente.

Los reintentos envían exactamente la misma operación. El compromiso usa la idempotencia existente por recurso y oferta; la liberación envía siempre **emergencia y oferta**, sin liberar otras ofertas por accidente. Nunca se registra el cuerpo del login ni su cookie en las salidas o mensajes de error.

La [guía E3-10](../docs/e3-10-sistema-nacional.md) detalla la actualización de CUIT, las variables y tareas del proceso y la prueba manual.

## Pruebas

`mvn test` ejecuta 20 pruebas con un servidor HTTP local: autenticación y renovación, evaluación sin rechazo automático, identidad de ONG/recurso, selección individual, stock insuficiente, idempotencia ante respuesta perdida, liberación repetida, entradas y respuestas inválidas, límites de reintento y descriptores del conector. No requieren Studio ni una base real.

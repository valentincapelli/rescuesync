# Conector Bonita: ofertas consolidadas

Conector independiente para Bonita Studio 2025.2 / Runtime 10.4.0, generado a partir del arquetipo oficial `org.bonitasoft.archetypes:bonita-connector-archetype:1.3.0`. Usa Java 17 y la API `bonita-common` 10.4.0. No requiere la extensión REST Connector.

## Compilar y probar

Usar JDK 17 o 21 y Maven:

```powershell
cd rescuesync/bonita-ofertas-consolidadas-connector
mvn clean package
```

El JAR instalable en Studio queda en `target/bonita-ofertas-consolidadas-connector-1.0.0-SNAPSHOT.jar`. El ZIP `target/bonita-ofertas-consolidadas-connector-1.0.0-SNAPSHOT-impl.zip` contiene la implementación y su dependencia Jackson; sirve para actualización de implementación en runtimes que ofrecen esa función, no para agregar la definición a Studio.

## Instalar en Bonita Studio

1. Abrir el proyecto `RescueSync` en Studio 2025.2.
2. Abrir la vista **Extensions** desde la vista general del proyecto (o **New → Extension**).
3. Para conservar la dependencia transitiva Jackson, ejecutar `mvn install` en este módulo y elegir **Add → From Maven repository**. Introducir:
   - Group ID: `com.grupo21.rescuesync.bonita`
   - Artifact ID: `bonita-ofertas-consolidadas-connector`
   - Version: `1.0.0-SNAPSHOT`
4. Verificar que aparece **RescueSync - ofertas consolidadas** en la categoría **Custom**. Si Studio usa otro repositorio Maven local, configurar allí el repositorio que contiene el JAR y el POM o publicar ambos en un repositorio Maven accesible. La importación directa del JAR no comunica por sí sola la dependencia transitiva.

## Configurar el proceso

El `rescue-sync.bos` actualizado ya contiene esta configuración. Después de importarlo, verificar los siguientes puntos; crearlos manualmente sólo si se parte de otro proyecto de Studio.

1. Crear una variable de proceso `ofertasConsolidadasJson` de tipo `String`, inicialmente vacía.
2. En la tarea humana **Evaluar cobertura de lotes**, agregar el conector **RescueSync - ofertas consolidadas** en **Connectors in**.
3. Configurar `emergenciaId` con la expresión de variable de proceso `emergenciaId` (tipo `Long`), y `backendBaseUrl` con el origen del backend, por ejemplo `http://localhost:8081`. Desde un runtime en contenedor, usar una dirección que alcance el backend desde el contenedor.
4. En **Outputs**, asignar `ofertasConsolidadasJson` a la variable de proceso del mismo nombre.
5. Confirmar en el diagrama que el flujo del boundary timer **Periodo convocatorio** llega a esta tarea. El conector de entrada se ejecuta antes de que la tarea humana quede disponible. No guardar una salida parcial si falla una llamada.

Las credenciales se leen del **entorno del proceso que ejecuta Bonita Runtime**:

```text
RESCUESYNC_BONITA_SERVICE_EMAIL
RESCUESYNC_BONITA_SERVICE_PASSWORD
```

Definirlas en el administrador de secretos o en la configuración de arranque del runtime. Reiniciar el runtime después de agregarlas. Para pruebas locales de Studio, iniciar Studio desde un entorno que las tenga definidas, sin escribir sus valores en el proceso, el `.bos`, parámetros, scripts versionados o logs. La cuenta técnica del backend debe tener el permiso específico de consulta de ofertas consolidadas.

El conector crea un cliente HTTP y un almacén de cookies nuevos por intento; no expone ninguna salida de login. Acepta sólo HTTP 200 con cookie `access_token` y una respuesta de tipo array JSON. Cada llamada tiene timeout de 10 s, con 3 s de conexión. Reintenta hasta 3 veces en fallos de conexión, timeout, HTTP 429 o HTTP 5xx, con pausas de 200 y 400 ms. HTTP 4xx restantes, cookie ausente y JSON inválido hacen fallar el conector inmediatamente. Bonita debe tratar una falla del conector como incidente; revisar y resolver el incidente antes de permitir que se evalúe cobertura con datos incompletos.

## Fuentes de compatibilidad

- [Arquetipo y artefactos de conectores de Bonita 2025.2](https://documentation.ofelia.com/bonita/2025.2/process/connector-archetype)
- [Gestión de extensiones en Studio](https://documentation.ofelia.com/bonita/2026.1/bonita-overview/managing-extension-studio)

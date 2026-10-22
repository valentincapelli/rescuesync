# Entrega y continuidad de E3-08 / E3-09

Estado revisado el 21/10/2026. La prueba manual informada en Bonita, caso 2001, fue exitosa: venció el timer, se ejecutó `consultarOfertasConsolidadas`, se guardó el JSON en `ofertasConsolidadasJson` y quedó disponible la tarea humana. El `rescue-sync.bos` incluido contiene esa configuración.

El módulo `bonita-ofertas-consolidadas-connector/` está incluido para que el equipo pueda reconstruir e instalar el conector.

## Qué está implementado

| Pieza | Ubicación y función |
| --- | --- |
| E3-08 | `rescuesync-backend`: `GET /api/emergencias/{emergenciaId}/ofertas-consolidadas`. Devuelve un array de todos los lotes de la emergencia, incluso los que no tienen ofertas. |
| Autenticación técnica | `BonitaServiceAccountInitializer` crea o actualiza al arrancar el usuario `SERVICIO_BONITA`, sin Municipio ni ONG. Sólo tiene `CONSULTAR_OFERTAS_CONSOLIDADAS`. El endpoint también admite usuarios humanos con `CONSULTAR_EMERGENCIAS`. |
| Conector E3-09 | `bonita-ofertas-consolidadas-connector/`: hace login técnico, conserva la cookie `access_token` sólo en memoria durante la ejecución y consulta E3-08. Entrega exclusivamente un `String` JSON. |
| Proceso Bonita | En el proyecto actualizado de Studio, `RescueSync` 1.1: el timer `Periodo convocatorio` lleva a `Evaluar cobertura de lotes`; al entrar en esa tarea corre `consultarOfertasConsolidadas`. |

El JSON es un array. Cada lote contiene `id`, `tipoRecurso`, `descripcion`, `cantidadRequerida`, `unidadMedida`, `estado`, `totalOfrecido` y `ofertas`. Cada oferta contiene `id`, `ong: {id, nombre}`, `cantidadOfrecida`, `observaciones` y `estado`. Un lote sin ofertas tiene `ofertas: []` y `totalOfrecido: 0`. Una emergencia inexistente devuelve 404.

## Requisitos y arranque

- Backend: Java 21, Maven, Spring Boot, PostgreSQL y puerto 8081. La configuración de conexión está en `rescuesync-backend/src/main/resources/application.yml` (`DB_URL`, `DB_USER`, `DB_PASSWORD`). El `docker-compose.yml` raíz ofrece PostgreSQL en el puerto 5433 para desarrollo.
- Conector: JDK 17 o 21, Maven; compila contra `bonita-common` 10.4.0. Usar Bonita Studio 2025.2-u0 / Runtime 10.4.0.
- En una PostgreSQL existente, verificar que `usuarios_rol_check` acepte `SERVICIO_BONITA`. Si no lo acepta, aplicar **una vez en esa base** `scripts/usuarios_servicio_bonita.sql` según `rescuesync-backend/README.md`, antes de iniciar el backend con la cuenta técnica. `ddl-auto=update` no modifica esa restricción por sí solo. No hace falta cargar `seed-backend-demo.sql` para crear la cuenta técnica.

### Credenciales técnicas

Configurar **las mismas** dos variables en el entorno del proceso del backend **antes de arrancarlo** y en el entorno del proceso que ejecuta Bonita Runtime **antes de arrancar Studio/Runtime**:

```text
RESCUESYNC_BONITA_SERVICE_EMAIL
RESCUESYNC_BONITA_SERVICE_PASSWORD
```

Usar una identidad exclusiva y el gestor de secretos o la configuración local no versionada de cada entorno. No colocar los valores en el `.bos`, parámetros de proceso, POM, `launch.json` versionado, scripts SQL, documentación o logs. Reiniciar ambos procesos tras cambiar las variables: el backend crea/actualiza la cuenta sólo al arrancar, y Bonita lee el entorno de su propio proceso. `BONITA_USER` y `BONITA_PASSWORD` sirven para la comunicación en sentido inverso, backend hacia Bonita.

## Construir e instalar el conector

Desde `bonita-ofertas-consolidadas-connector/`:

```text
mvn clean package
mvn install
```

`package` produce el JAR `target/bonita-ofertas-consolidadas-connector-1.0.0-SNAPSHOT.jar`; `install` publica JAR y POM en el repositorio Maven local. En Studio: abrir el proyecto → **Extensions** → **Add** → **From Maven repository** e introducir `com.grupo21.rescuesync.bonita:bonita-ofertas-consolidadas-connector:1.0.0-SNAPSHOT`. El POM permite resolver Jackson, dependencia transitiva del conector. Si otro equipo usa otro repositorio Maven, debe instalar el módulo allí o publicarlo en un repositorio Maven compartido; copiar sólo el JAR omite esa relación de dependencias. Más detalle en `bonita-ofertas-consolidadas-connector/README.md`.

En `Evaluar cobertura de lotes`, el conector va en **Connectors in** con:

| Campo | Valor |
| --- | --- |
| Nombre | `consultarOfertasConsolidadas` |
| `emergenciaId` | Variable de proceso `emergenciaId` (`Long`) |
| `backendBaseUrl` | Origen del backend alcanzable desde Bonita; en Studio local, `http://localhost:8081` |
| Salida `ofertasConsolidadasJson` | Asignar a variable de proceso `String` del mismo nombre |

El conector falla ante 401, 404, cookie ausente o JSON inválido. Reintenta hasta tres veces en timeout, fallos de conexión, 429 y 5xx. Un fallo debe aparecer como incidente; no dar por evaluada la cobertura con un valor vacío o viejo.

## Exportación Bonita verificada

El archivo raíz `rescue-sync.bos` fue exportado desde el proyecto `rescue-sync11` de Bonita 10.4.0. Su `app/diagrams/MiDiagrama-1.0.proc` coincide byte por byte con el diagrama actual del workspace y contiene `RescueSync` 1.1, el timer, `consultarOfertasConsolidadas` como conector `ON_ENTER`, entradas `emergenciaId` y `backendBaseUrl`, y la asignación a `ofertasConsolidadasJson`. `app/pom.xml` declara `bonita-ofertas-consolidadas-connector:1.0.0-SNAPSHOT`. El archivo incluye el JAR del conector en `app/.store/`; su clase principal y definición coinciden con las del build local. No copiar el workspace entero: incluye archivos de ejecución y bases locales.

La organización `app/organizations/ACME.xml` se conserva por decisión del equipo. Sus 21 campos de contraseña tienen un único valor, que coincide con la contraseña de demostración documentada por Bonita para los usuarios de ACME. El contenido de ACME coincide con la exportación anterior. El escaneo de los demás XML, el diagrama y la configuración no encontró credenciales técnicas, JWT, cookies ni secretos del proyecto. La [documentación de Bonita sobre la organización ACME](https://documentation.ofelia.com/bonita/latest/identity/organization-management-in-bonita-bpm-studio) confirma que es una organización de ejemplo, y su [guía de inicio](https://documentation.ofelia.com/bonita/latest/getting-started/define-who-can-do-what) describe las credenciales de prueba. No usar esa organización de demostración como identidad técnica.

Para importar el proceso en otro Studio 2025.2-u0, usar **File → Import → BOS Archive…**, seleccionar `rescue-sync.bos` y resolver los conflictos de dependencias. Verificar la configuración del conector antes de ejecutar. La exportación lleva el JAR, pero aún no se probó la importación en otra máquina; el módulo Maven y su POM siguen siendo necesarios para reproducir el build y resolver Jackson. La [documentación de Bonita sobre intercambio de proyectos](https://documentation.ofelia.com/bonita/latest/setup-dev-environment/workspaces-and-repositories) describe el procedimiento.

## Prueba de extremo a extremo

1. Levantar PostgreSQL y el backend con la cuenta técnica configurada; confirmar que el backend inicia sin error de restricción de rol.
2. Instalar el conector en Studio, arrancar Bonita con las mismas variables de entorno y desplegar `RescueSync` 1.1 desde la exportación actualizada.
3. Crear una emergencia con al menos un lote; registrar ofertas según el flujo existente. Usar `emergenciaId` en el contrato de inicio del proceso.
4. Dejar vencer el timer `Periodo convocatorio`. Confirmar que `Evaluar cobertura de lotes` queda disponible y que la variable de proceso `ofertasConsolidadasJson` contiene un array JSON con los lotes y totales esperados. Un lote sin ofertas debe aparecer con lista vacía y total cero.
5. Si falla, revisar el incidente del conector y el estado HTTP; no registrar ni compartir el cuerpo de login, la cookie o el JWT.

La prueba manual del caso 2001 fue informada como exitosa; la ejecución no se reproduce automáticamente en la suite Maven.

## Problemas frecuentes

| Síntoma | Comprobación |
| --- | --- |
| Faltan variables | Confirmar que las dos variables están en **ambos** procesos y reiniciarlos. Si sólo una está en el backend, éste no inicia; si faltan ambas, no crea la cuenta. |
| Login HTTP 401 | Confirmar que backend y Bonita usan exactamente el mismo email y contraseña, que la cuenta existe en la base apuntada por `DB_URL` y que el backend se reinició tras cualquier cambio. Un permiso insuficiente afecta al GET posterior, no al login. |
| Dependencia no resuelta | Instalar/publicar JAR **y POM** por Maven; verificar que Studio pueda resolver `jackson-core` y que la versión del conector coincida con la declarada en el proyecto Bonita. |
| Bonita no ve cambios de entorno o extensión | Reiniciar Runtime/Studio y volver a desplegar el proceso. Revisar incidentes del conector. |
| `localhost` no conecta | Desde un Runtime en contenedor, `localhost` es el contenedor; configurar una dirección alcanzable para el backend. |
| El proceso importado no tiene el conector | Confirmar que se importó `rescue-sync.bos` actualizado y verificar la entrada `consultarOfertasConsolidadas` en el diagrama. |

## Continuidad: Bonita y Sistema Nacional

`ofertasConsolidadasJson` queda disponible como variable de proceso de tipo `String` después del conector de entrada, asociada a la instancia que ya conserva `emergenciaId`. Para procesarla, parsear el array y correlacionar `id` de lote y de oferta con esa emergencia; no tratar los totales como reservas confirmadas. El conector sólo lee RescueSync y no modifica el Sistema Nacional.

El diagrama actualizado ya dibuja en la lane **Sistema Nacional** las Service Tasks `Evaluar ofertas`, `Comprometer recursos` y `Liberar recursos`; no tienen conectores configurados en el diagrama inspeccionado. Son puntos posibles de extensión, sujetos a definir qué datos necesita cada paso, cómo se mapean las identidades entre sistemas, la autenticación técnica independiente de E3-03/E3-10, idempotencia y tratamiento de errores. Consultar el README y el código actuales de `rescuesync-sistema-nacional` antes de elegir contratos o endpoints; este documento no presupone nuevas operaciones.

## Verificación de esta preparación

- Conector: `mvn -o clean package` con JDK 21 y el repositorio Maven local configurado terminó correctamente; **12 pruebas, 0 fallos**. El JAR y ZIP de implementación se generan en `target/` y no se versionan.
- Backend específico: `mvn -o -Dtest=OfertasConsolidadasIntegrationTests,BonitaServiceAccountIntegrationTests test`; **8 pruebas, 0 fallos** con H2.
- Suite completa del backend: `mvn -o test` **falla** en esta rama. `EmergenciaIntegrationTests` (40 de 41) y `LoteIntegrationTests` (15 de 15) esperan respuestas de rutas que ahora devuelven 401 sin autenticación. `RescueSyncApplicationTests` (4 errores) no resuelve `jwt.secret` en su configuración de prueba. No se ajustaron esas pruebas porque están fuera del cambio de documentación y lógica validada.
- No se ejecutaron migraciones ni pruebas contra una PostgreSQL real durante esta preparación. La prueba manual del caso 2001 es la evidencia de integración en Bonita proporcionada por el equipo.

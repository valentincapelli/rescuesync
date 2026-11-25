# Preparación de la defensa oral

El paso a paso de cada prueba (camino feliz, caminos alternativos, orquestación y dashboard) está en [`guia-pruebas.md`](guia-pruebas.md).

Se lee junto con dos documentos:

- **`RescueSync – Guion de defensa final (Grupo 21) (1).pdf`** (en la raíz del repo): el guion minuto a minuto del grupo. Es la **fuente de verdad del recorrido**: reparto, tiempos y frases.
- [`preparacion-demo.md`](preparacion-demo.md): checklist técnico de servicios, puertos y usuarios.

Este documento cruza el guion con el código real: qué datos necesita cada paso, qué afirmaciones del guion son ciertas y dónde hay una brecha. Última verificación contra el código: 24/11/2026.

## 0. Lo que hay que resolver antes de la defensa

| # | Tema | Estado | Acción |
|---|---|---|---|
| 1 | **"Reformular lotes" (Franco, camino alternativo 2)** | **No está implementado.** El guion lo muestra y lo da por hecho. Solo existe el valor `REFORMULADO` en `EstadoLote`, sin uso; el proceso de Bonita no tiene esa decisión y los lotes son de solo lectura al publicar (`e4-01-ajustes-pendientes.md`, fila "Resolver cobertura incompleta") | Decidir **antes**: implementarlo o cambiar el guion (sección 3.2). No improvisar en vivo |
| 2 | Exportación CSV/XLSX (Juan) | Implementada y probada por API el 18/11, **no probada desde la pantalla** | Descargar ambos archivos y abrirlos en Excel antes de la defensa |
| 3 | Casos archivados en Bonita para "Tiempos por etapa" (Franco) | Se generan solo corriendo emergencias reales hasta CERRADA con Bonita levantado. El seed de indicadores **no crea casos de Bonita** | Sección 4.4 |
| 4 | Timer de convocatoria | El guion pide 1 a 2 min. Se configura **sin redeploy de Bonita**: el backend lo envía al abrir cada caso con `PLAZO_CONVOCATORIA_MS` (por defecto, 2 días) | Ya está en `rescuesync-backend/.env` (`PLAZO_CONVOCATORIA_MS=120000`): arrancar con `mvn spring-boot:run` **desde esa carpeta**. El `.env` no se versiona: en otra PC hay que crear la línea |
| 5 | Sistema Nacional en Render | Se duerme (40 a 60 s en frío). El guion ya pide despertarlo 5 min antes | Verificar `GET /actuator/health` y que `validar` devuelva `disponibilidad` |
| 6 | E4-09 (perfiles y permisos) y E4-10 (pruebas integrales), "sin cerrar" en el grooming | El código de E4-09 está documentado en `e4-09-*.md` | Confirmar en el tablero que figuren cerradas |
| 7 | Kubernetes (opcional) | No implementado (no hay manifiestos). El guion tampoco lo incluye | No mencionarlo como logro |

## 1. Correcciones del guion contra el código

Afirmaciones del guion que conviene ajustar o saber matizar:

- **Reformular lotes**: ver punto 1 de arriba. Las otras dos decisiones (reabrir y continuar parcial) existen y funcionan.
- **"La decisión queda asociada al caso y después aparece en el dashboard"** (cierre del tramo alternativo): el dashboard refleja el estado final de la emergencia (`COBERTURA_PARCIAL`, cobertura alcanzada), pero **no tiene un indicador específico de "decisiones del Centro Coordinador"**. Decirlo así: "se refleja en el estado y la cobertura de cada proyecto".
- **"Reabrir muestra que los lotes siguen iguales y se fija nueva ventana"**: cierto. Las ofertas ya recibidas **se conservan** y cuentan en la siguiente evaluación (`e4-02-cobertura.md`).
- **PARCIAL vs SIN_COBERTURA**: en el código, `SIN_COBERTURA` es el nombre histórico de "reabrir convocatoria" y se acepta con cualquier cobertura incompleta, con o sin ofertas. La interfaz lo muestra como "Solicitar nueva convocatoria". Si preguntan por qué no se llama distinto: decisión del grupo, respaldada por el enunciado (pág. 2).
- **Orquestación**: cierta. Se verificó que las referencias al Sistema Nacional están solo en `bonita-sistema-nacional-connector/`, en el propio `rescuesync-sistema-nacional/` y en `scripts/configurar_bonita_e3_10.py`. Ni `rescuesync-backend` ni `rescuesync-frontend` contienen la URL ni un cliente del SN.
- **JWT con RBAC, alcance por municipio u ONG**: cierto. El backend usa `JwtService` y `JwtAuthenticationFilter` (no hay sesión de servidor).
- **Bloqueo pesimista** (`PESSIMISTIC_WRITE`) en el compromiso del SN y en la edición de ofertas, e idempotencia con `emergenciaRef`/`ofertaRef`: presentes en el código. La intención de selección usa control optimista (`@Version`).
- **Historial de versiones de oferta**: implementado (`versiones-ofertas.md`): botón "Ver historial de versiones" en "Mis ofertas". La versión 1 es el alta y no tiene autor (la oferta nunca lo guardó).
- **Stock**: además de lo que dice el guion, la validación del SN devuelve `disponibilidad` y el backend bloquea (409) seleccionar una oferta que supera el stock acumulado de su ONG. Es un extra para mostrar y para responder.

## 2. Reparto (según el guion)

| Integrante | Tramos | Consulta del dashboard |
|---|---|---|
| Valentín | Arquitectura; generación de lotes; monitoreo del Coordinador; **reabrir convocatoria** | Proyectos por estado y cobertura |
| Juan | Orquestación (apertura); alta de emergencia; liberación y cierre bilateral | Resumen ejecutivo + exportación |
| Ezequiel | Adjudicación y notificación; compromiso con concurrencia; **continuar con cobertura parcial**; Network de la WebApp | Suministros movilizados |
| Santiago | Carga de ofertas; finalización por la ONG; conectores en Bonita y log nacional | Cobertura colaborativa (consorcios) |
| Franco | Vencimiento y ofertas consolidadas; validación en la API Nacional; **reformular lotes**; cierre | Tiempos por etapa (cruza con Bonita) |

Las 5 consultas del guion coinciden con los endpoints implementados, una por integrante (pauta cumplida):

| Consulta | Endpoint | Fuente |
|---|---|---|
| Resumen ejecutivo + exportación | `GET /api/indicadores/resumen` y `/reporte?formato=csv\|xlsx` | Local + Bonita |
| Suministros movilizados | `/api/indicadores/suministros` | Local |
| Cobertura colaborativa | `/api/indicadores/consorcios` | Local |
| Proyectos por estado y cobertura | `/api/indicadores/proyectos` | Local |
| Tiempos por etapa | `/api/indicadores/tiempos-etapas` | **API REST de Bonita** (`archivedCase`, `archivedFlowNode`) |

## 3. Qué necesita cada tramo del guion

### 3.1 Camino feliz (guion, 0:02 a 0:12)

| Paso del guion | Datos y estado previo | Verificar |
|---|---|---|
| 1. Alta (Juan) | Usuario `operador1.laplata@test.com`, municipio La Plata | Se crea la emergencia y aparece el id de caso de Bonita |
| 2. Lotes y publicación (Valentín) | `centro.coordinador@test.com` | Mostrar la tarea avanzada en el Portal de Bonita |
| 3. Ofertas (Santiago) | Dos ONGs con usuario: Cruz Roja y Cáritas. **Para consorcio, Cáritas necesita certificación de Alimentos** (`BASICO`/`MEDIO`) en el SN | Editar una oferta y abrir "Ver historial de versiones" |
| 4. Vencimiento (Franco) | Plazo corto; credenciales de servicio cargadas en Bonita (`RESCUESYNC_BONITA_SERVICE_EMAIL/PASSWORD`) | Timer Event esperando; `ofertasConsolidadasJson` cargada; cobertura COMPLETA |
| 5. Validación nacional (Franco) | SN levantado; certificaciones vigentes; **stock suficiente por ONG** | Ofertas evaluadas con perfil principal/apoyo en el perfil Municipio |
| 6. Adjudicación (Ezequiel) | — | Notificación visible en la ONG y confirmación de recepción |
| 7. Compromiso (Ezequiel) | SN accesible desde Bonita | `GET /api/recursos/disponibilidad` muestra el stock descontado |
| 8. Ejecución (Valentín, Santiago) | — | Novedad registrada, monitoreo completado, actividad finalizada |
| 9. Liberación y cierre (Juan) | — | Stock repuesto; caso archivado en Bonita; emergencia CERRADA |

> **Ya está hecho** (24/11/2026): el `rescue-sync.bos` del repo trae la URL de Render. Los pasos siguientes quedan como referencia, por ejemplo si hay que volver a configurarlo.

**Demo con el Sistema Nacional de Render.** El proceso apunta al SN por la variable de proceso `sistemaNacionalBaseUrl`, cuyo valor inicial es `http://localhost:8082`. Para que **todo caso nuevo use Render sin intervención**, se cambia ese valor inicial en el proceso y se redespliega (una sola vez, desde Bonita Studio):

1. Abrir el diagrama RescueSync y seleccionar el pool **RescueSync** (clic en el borde del pool, no en un carril).
2. Pestaña **Details / Data → Pool variables**, seleccionar `sistemaNacionalBaseUrl` y editar.
3. En **Default value** (tipo *Constant*), escribir exactamente `https://rescuesync-sistema-nacional-2qwh.onrender.com`, sin barra final.
4. **Deploy** (menú Server / botón Deploy) y confirmar el reemplazo del proceso. Con la instancia vacía no hay casos que perder.
5. Verificar: crear una emergencia y comprobar la variable del caso. `GET /bonita/API/bpm/caseVariable/{caso}/sistemaNacionalBaseUrl` debe devolver la URL de Render.

Hacerlo **antes** de los ensayos. Después, exportar el `.bos` y reemplazar `rescue-sync.bos` del repo, y cambiar el valor de `sistemaNacionalBaseUrl` en `scripts/configurar_bonita_e3_10.py` (línea 26), para que el repo no vuelva a `localhost` al reconfigurar.

Si en algún momento hace falta volver al SN local, se repite el cambio con `http://localhost:8082`.

Plan B, solo si el cambio en Studio no se puede hacer a tiempo: `scripts/apuntar-bonita-a-render.py` corrige la variable de cada caso nuevo desde afuera (hay que dejarlo corriendo en una terminal durante toda la demo). No es la solución: depende de un proceso externo.

Antes de arrancar: despertar Render (`GET /actuator/health`, ver sección 4.4).

### 3.2 Caminos alternativos (guion, 0:12): decisión pendiente sobre "Reformular"

El guion encadena tres decisiones sobre **una** emergencia con tres vencimientos (a 1 o 2 min cada uno, son 3 a 6 minutos de espera: **por eso el guion pide dejar la emergencia con ofertas cargadas y el timer por vencer**).

Secuencia posible hoy:

1. **Valentín: reabrir** (existe). Lotes sin cubrir completamente → "Solicitar nueva convocatoria". Los lotes quedan iguales y se fija una nueva ventana.
2. **Franco: reformular** (**no existe**).
3. **Ezequiel: continuar parcial** (existe). "Resolver cobertura parcial" con justificación obligatoria; el proceso avanza a validación y adjudicación.

Opciones para el punto 2:

- **A. Implementarlo** (recomendado si hay tiempo). Hace falta: una decisión más en el panel de cobertura (el enum del contrato `decisionCoberturaInput` hoy admite `COMPLETA`, `PARCIAL`, `SIN_COBERTURA`), un camino en el BPMN que vuelva a la tarea de armado de lotes, edición de lotes con justificación en ese estado, estado `REFORMULADO` y trazabilidad del cambio. Requiere modificar y redesplegar el proceso en Bonita.
- **B. Mostrar la brecha y su diseño.** Franco explica la decisión, muestra el estado `REFORMULADO` previsto en el modelo y declara que el relanzamiento con lotes editados no está implementado. Es lo honesto si no se llega.
- **C. Sustituto parcial** (no equivalente, no presentarlo como tal): crear una emergencia nueva con los lotes ajustados.

**Qué no hacer:** mostrar la pantalla como si existiera y que el evaluador lo descubra preguntando. La evaluación es individual y Franco responde por esta parte.

Datos necesarios para los alternativos: una emergencia con ofertas **menores** a lo requerido en al menos un lote (por ejemplo, lote de 300 con una oferta de 150), publicada con plazo corto.

### 3.3 Orquestación (guion, 0:15)

- **Ezequiel, Network**: abrir DevTools > Network antes de empezar el recorrido y filtrar por `Fetch/XHR`; todos los requests van al backend (`/api/...`). Ninguno a la URL del SN.
- **Santiago, conectores**: en Bonita Studio, los conectores `EVALUAR`, `COMPROMETER` y `LIBERAR` con la URL del SN y la cuenta técnica (`bonita@sistemanacional.test`, rol `SERVICIO`).
- **Log nacional**: en Swagger del SN o en el log del servicio, las llamadas de compromiso y liberación llegan con la identidad técnica de Bonita.

### 3.4 Consultas gerenciales (guion, 0:16)

Requisito de la pauta: varias instancias finalizadas **en Bonita**, con registros consistentes en la base local.

1. Antes de la defensa, correr de punta a punta al menos **3 o 4 emergencias reales** hasta CERRADA (idealmente una con consorcio, una con cobertura parcial y una con reapertura). Con eso `tiempos-etapas` tiene `muestras > 0`.
2. Opcional: `scripts/seed-indicadores-demo.sql` suma 10 emergencias `[DEMO-E4]` para dar volumen, pero **no tienen caso de Bonita** (no aportan a los tiempos por etapa) y hoy **no está cargado** a propósito (sección 4.4).
3. Verificar con el usuario `direccion@test.com` que no aparezca el aviso de "Bonita no disponible" y que los filtros de fecha incluyan los casos.

## 4. Precarga de datos

### 4.1 Qué hay que cargar y dónde

| Base | Dónde corre | Cómo se carga | Contenido |
|---|---|---|---|
| **Local RescueSync** (PostgreSQL) | `localhost:5433`, base `rescuesync` (`docker compose up -d` en la raíz) | `scripts/seed-backend-demo.sql`; luego `scripts/seed-indicadores-demo.sql` | Municipios (La Plata, Berisso), ONGs, usuarios y roles (operadores, 2 representantes de Cruz Roja, 1 de Cáritas, coordinador, directivo) |
| **Sistema Nacional** (PostgreSQL) | `localhost:5434`, base `sistema_nacional` (`docker compose up -d --build` en `rescuesync-sistema-nacional/`) o la base de Render | `scripts/seed-sistema-nacional-demo.sql` (ONGs y usuarios) y **por la API del SN, como admin**: certificaciones y recursos | Certificaciones vigentes y stock |
| **Bonita** | `localhost:8080`, proceso RescueSync 1.2 | Corriendo emergencias reales | Casos archivados |

Orden: **primero levantar cada servicio** (para que Hibernate cree las tablas con `ddl-auto: update`) y **después** correr los seeds. Los seeds suponen base limpia.

### 4.2 Datos del Sistema Nacional (por API, como `admin@sistemanacional.test`)

Mínimo para el recorrido del guion:

- Cruz Roja (ONG 1): certificación de Alimentos `INTERMEDIO`, riesgo hasta `ALTO`; stock holgado de raciones (≥ 1000 para no tropezar con el control de stock).
- Cáritas (ONG 2): certificación de Alimentos `BASICO`/`MEDIO`, necesaria para mostrar el consorcio; stock propio (≥ 100).
- Defensa Civil (ONG 3) queda `PENDIENTE` en el seed: sirve para mostrar que una ONG no habilitada no valida.
- Un segundo tipo de recurso, opcional, si se quiere variedad en "Suministros movilizados".

Comprobar para cada ONG con `POST /api/certificaciones/validar` que devuelva la capacidad y la `disponibilidad` esperadas. El detalle de los endpoints está en `docs/e3-10-prueba-integracion.md` (sección 1).

### 4.3 Reglas de oro

- Reiniciar la base local y los casos de Bonita **juntos o ninguno**. Si no, quedan emergencias huérfanas.
- No dejar datos de pruebas anteriores con prefijos como `[E2E-…]`: se ven en el dashboard.
- Si el SN de Render se usa, despertarlo 5 min antes y confirmar que `validar` devuelve `disponibilidad` (verificado el 24/11 tras el redeploy).
- Los servicios y las bases hay que levantarlos en este orden: bases, SN, backend (`PLAZO_CONVOCATORIA_MS=120000`), Bonita con las variables de servicio, frontend.

### 4.4 Estado del entorno al 24/11/2026

Se levantaron las dos bases en Docker (`rescuesync-postgres` en 5433 y `sistema-nacional-postgres` en 5434) y Bonita. Todas las bases se dejaron **limpias, con solo los datos de soporte de la demo**. **No hay servicios de aplicación corriendo** (backend, frontend, SN local).

| Base | Contenido actual | Vacío |
|---|---|---|
| **Local RescueSync (5433)** | 2 municipios (La Plata, Berisso), 2 ONGs (Cruz Roja, Cáritas), 9 usuarios y roles (ver `preparacion-demo.md`) | Emergencias, lotes, ofertas, historial, notificaciones, despliegues, intenciones de selección y revisiones de oferta |
| **SN local (5434)** | 3 ONGs (Defensa Civil `PENDIENTE`), 5 usuarios, certificaciones de Alimentos (Cruz Roja `INTERMEDIO`/`ALTO`, Cáritas `BASICO`/`MEDIO`), stock 1000 y 500 raciones | Compromisos (stock comprometido en 0) |
| **Bonita** | Proceso RescueSync 1.2 desplegado | 0 casos abiertos, 0 archivados |
| **SN Render** | Ver abajo | Ver abajo |

Se eliminaron las 10 emergencias de pruebas anteriores, las 10 `[DEMO-E4]` y la ONG "Banco de Alimentos La Plata". **No se cargó el seed de indicadores**: sus emergencias no tienen caso de Bonita y mezclarían datos inconsistentes con los reales. El dashboard se alimenta de las emergencias que se corran de punta a punta (sección 3.4). Si hace falta volumen, `scripts/seed-indicadores-demo.sql` sigue disponible, sabiendo que no aporta a "Tiempos por etapa".

**Sistema Nacional de Render** (`https://rescuesync-sistema-nacional-2qwh.onrender.com`), verificado el 24/11/2026 por API:
- Responde (`/actuator/health` 200, login admin con las credenciales del seed).
- Cruz Roja: certificación de Alimentos `INTERMEDIO`/`ALTO`, 1000 raciones disponibles, 0 comprometidas.
- Cáritas: **le agregué** la certificación de Alimentos `BASICO`/`MEDIO` (faltaba; necesaria para el consorcio) y tiene 500 raciones, 0 comprometidas.
- Defensa Civil: sin certificaciones ni stock (queda `PENDIENTE`).
- **Historial de compromisos limpio**: se borraron los 3 compromisos de pruebas anteriores (todos `LIBERADO`) y el stock quedó en 0 comprometido. Los ids de oferta y emergencia de la base local arrancan en 1 sin riesgo de colisión.
- **Redesplegado el 24/11/2026 con el control de stock**: `POST /api/certificaciones/validar` ya devuelve `disponibilidad` (Cruz Roja 1000, Cáritas 500 raciones). Se puede mostrar contra Render el paso "oferta que supera el stock queda no seleccionable".
- Apuntar los casos de Bonita a Render (cambio en el proceso): sección 3.1.

**Pendientes del entorno:**
- Levantar backend (`PLAZO_CONVOCATORIA_MS=120000`) y frontend.
- ~~Apuntar el proceso a Render~~ **Hecho y verificado el 24/11/2026**: un caso nuevo de RescueSync 1.2 arranca con `sistemaNacionalBaseUrl` = URL de Render. Falta exportar el `.bos` desde Studio y reemplazar `rescue-sync.bos` del repo (el script `configurar_bonita_e3_10.py` ya usa la URL de Render).
- Generar 3 o 4 casos archivados reales con Bonita y Render.
- Las columnas nuevas del backend (por ejemplo `validacion_stock_disponible`) las crea el backend al arrancar (`ddl-auto: update`); no se verificaron.


## 5. Qué puede salir mal (del guion, con respuestas del código)

| Problema | Reacción |
|---|---|
| El SN en Render tarda (cold start) | Esperar explicando la arquitectura; o usar el SN local |
| El timer no vence a tiempo | Revisar que el backend arrancó con `PLAZO_CONVOCATORIA_MS`; el plazo se toma al abrir el caso, no retroactivamente |
| Un conector falla | El caso queda `failed` y la app lo muestra como incidente con motivo. **No usar "Omitir"** en el Portal Community: da la tarea por hecha sin ejecutarla. Pasar al video o a la emergencia de respaldo |
| "Evaluar ofertas" no avanza | SN dormido o caído, o faltan `RESCUESYNC_BONITA_SERVICE_*` en Bonita |
| Selección responde 409 | Es el control de stock funcionando |
| La ONG ve "Liberación pendiente" | "Actualizar" en Actividades; si persiste, "Actualizar" en Despliegues del Coordinador |
| El dashboard sale vacío | Filtros de fecha; confirmar que hay instancias finalizadas |

## 6. Preguntas probables (para los cinco)

Las respuestas del guion (sección 7 del PDF) son correctas; estas son las precisiones del código:

- **BPMN**: variables `ofertasConsolidadasJson`, `ofertaSeleccionadaIdInput`, `continuarSeleccionandoInput`, `decisionCobertura`. El gateway "¿Lotes cubiertos?" tiene tres salidas: `COMPLETA` → Evaluar ofertas, `PARCIAL` → Resolver cobertura incompleta, `SIN_COBERTURA` → Publicar convocatoria. Si preguntan por el tercer camino de "reformular": ver sección 3.2.
- **Web**: autenticación JWT con permisos por rol (`EVALUAR_COBERTURA`, `CONSULTAR_INDICADORES`, `CONSULTAR_OFERTAS`, entre otros). Si Bonita no recibe una decisión, el endpoint responde 502 y se revierte lo local (no queda estado a medias).
- **API Cloud**: `POST /api/certificaciones/validar` (capacidad, perfil, `disponibilidad`), `POST /api/compromisos`, `POST /api/compromisos/liberar`, `GET /api/recursos/disponibilidad`; documentado en Swagger.
- **Concurrencia**: bloqueo pesimista en el inventario del SN y en la edición de ofertas; idempotencia por `emergenciaRef`/`ofertaRef`; versión optimista en la intención de selección. **Limitación honesta**: el stock que ve la web se lee al evaluar las ofertas; si otra emergencia compromete esas raciones después, el compromiso puede fallar igual y el caso queda en incidente.
- **Integración**: conector `consultarOfertasConsolidadas` (GET a la API local con identidad técnica) y conector del SN; backend ↔ Bonita por REST con reintento y timeouts de 5 s (conexión) y 15 s (lectura).
- **Base de datos**: además de las entidades del guion, `historial_estados` es de donde salen los indicadores de suministros, consorcios y fecha de cierre. Nadie cambia estados con `setEstado` salvo en el alta.

## 7. Limitaciones que conviene declarar antes de que pregunten

- No existe "reformular lotes" (si no se implementa antes).
- Con la convocatoria publicada, los lotes son de solo lectura.
- Un caso `failed` de Bonita no tiene salida desde la aplicación.
- El dashboard no tiene un indicador dedicado a las decisiones de cobertura.
- Los exportes del dashboard no se probaron desde la pantalla (hacerlo antes).
- El frontend no tiene tests automáticos.
- No se implementó la extensión de Kubernetes (hay Docker y compose, y el SN en Render).

## 8. Ensayo (checklist)

- [ ] Resolver el punto de "Reformular lotes" (implementar o ajustar el guion) y avisar a Franco.
- [ ] Levantar el entorno completo y cargar los datos (sección 4).
- [ ] Correr 3 o 4 emergencias de punta a punta para tener casos archivados en Bonita.
- [ ] Probar la descarga CSV y XLSX desde la pantalla.
- [ ] Dejar lista la emergencia de caminos alternativos (ofertas menores a lo requerido, timer por vencer).
- [ ] Un perfil de navegador por rol, más Bonita Portal/Studio, Swagger del SN, DevTools > Network y el dashboard.
- [ ] Ensayar el guion cronometrado, con los pases de palabra.
- [ ] Preparar el plan B: video o capturas de timer, validación nacional y compromiso.
- [ ] Confirmar que E4-09 y E4-10 estén cerradas.
- [ ] Cada integrante repasa la sección 6: la evaluación es individual.

## 9. Recorrido de ensayo paso a paso (probado el 25/11/2026 contra Render)

Recorrido completo ejecutado por navegador: emergencia #1 (caso de Bonita 3002) de punta a punta hasta CERRADA, con 3 ofertas (consorcio) contra el Sistema Nacional de Render. Duró unos 9 minutos de reloj con el plazo en 2 minutos. Resultado: caso archivado en Bonita, compromisos 4, 5 y 6 en `LIBERADO` en Render, stock restaurado (1000 y 500), y los 5 indicadores del dashboard con datos reales (tiempo promedio de resolución ≈ 0,2 h, 50 % de lotes por consorcio).

**Antes de empezar:** backend (8081; `mvn spring-boot:run` desde `rescuesync-backend`, que lee el plazo del `.env`) y frontend (5173; `npm run dev` desde `rescuesync-frontend`) corriendo, Bonita con el proceso apuntando a Render, Render despierto. Usuarios, todos con contraseña `123456`: `operador1.laplata@test.com`, `centro.coordinador@test.com`, `representante1.cruzroja@test.com`, `representante1.caritas@test.com`, `direccion@test.com`.

| # | Perfil | Qué hacer | Qué verificar |
|---|---|---|---|
| 1 | Operador · Registrar emergencia | Inundación, gravedad **Alta**, zona y descripción | "Emergencia #N registrada". En Bonita aparece un caso nuevo |
| 2 | Coordinador · Lotes | Elegir la emergencia, agregar lote A (Alimentos, 300 raciones) y lote B (100). **Publicar convocatoria** y confirmar en el modal | Emergencia en `CONVOCATORIA_PUBLICADA`. **Desde acá corre el plazo (2 min): cargar las ofertas enseguida** |
| 3 | ONG Cruz Roja · Ofertas | Elegir convocatoria y lote; 200 en el lote A y 100 en el B | Aparecen en "Mis ofertas" |
| 4 | ONG Cáritas · Ofertas | 100 en el lote A (consorcio). Después **Editar oferta** (por ejemplo a 110) y **Ver historial de versiones** | Versión 2 con "antes 100", autor y fecha |
| 5 | Coordinador · Lotes | Esperar el vencimiento. **Actualizar cobertura**: ambos lotes "Completa". Pulsar **Confirmar cobertura completa** | Bonita corre "Evaluar ofertas" contra Render (unos 20 s) |
| 6 | Operador · Seleccionar ofertas | Cada oferta muestra perfil nacional: Cruz Roja **Principal**, Cáritas **Apoyo secundario**. Marcar las 3 y **Confirmar selección** | "Selección confirmada: 3 ofertas aceptadas" |
| 7 | Cada ONG · Notificaciones | **Confirmar recepción** de la oferta que el proceso esté atendiendo | Bonita comprueba y compromete en Render. Verificar el stock: `GET /api/ongs/1/recursos` |
| 8 | Coordinador · Despliegues | Elegir la emergencia, **Actualizar**. **Escribir una observación** en el campo (sin texto el botón queda deshabilitado) y **Completar monitoreo** | Estado "Monitoreo completado" |
| 9 | ONG · Actividades | Escribir la **observación de cierre** y **Finalizar actividad** | Bonita libera en Render: "Recursos liberados" |
| 10 | Repetir 7 a 9 | Por cada oferta restante, **empezando por pulsar Actualizar** en Actividades (ONG) o Despliegues (Coordinador) | Ver aviso de abajo |
| 11 | Dirección · Indicadores | Mirar KPIs, filtrar por tipo de desastre, tiempos por etapa | Bonita disponible, casos finalizados ≥ 1 |

**Cosas que sorprenden (conviene saberlas antes de la demo):**
- **El ciclo es secuencial por oferta.** Con 3 ofertas seleccionadas, los pasos 7 a 9 se repiten 3 veces, uno por oferta. Cada vuelta tarda 1,5 a 2 minutos entre clics y Bonita. Para ensayos rápidos, seleccionar una sola oferta; para la demo, elegir 2 como máximo.
- **El avance a la oferta siguiente ocurre al sincronizar.** Después de que una oferta queda "Recursos liberados", hay que pulsar **Actualizar** (ONG · Actividades o Coordinador · Despliegues) para que el backend despache la próxima; si no, el caso espera en "Seleccionar oferta". Lo mismo para ver la emergencia como **Cerrada** tras la última liberación.
- **"Completar monitoreo" y "Finalizar actividad" piden texto** en el campo de observación; sin él los botones quedan deshabilitados sin avisar el motivo.
- **Los modales de confirmación** (publicar, completar monitoreo, finalizar) son propios de la app: no usan `window.confirm`.
- **Los tiempos de Bonita** (conectores contra Render) fueron de 4 a 20 s por tarea con Render despierto. Con Render dormido el primer "Evaluar ofertas" puede tardar hasta 60 s.
- **Tiempos por etapa en cero (bug, verificado el 25/11):** los tiempos van en horas con 1 decimal, así que toda etapa de menos de 3 minutos se ve como `0 h`; acumular casos no lo arregla. Además, la etapa Convocatoria no cuenta la tarea "Esperar /cargar / editar ofertas" porque el timer la cierra como `aborted` y el cálculo solo toma `completed`: mide solo "Publicar convocatoria".

**Antes de los ensayos finales:** la base local y Bonita ahora tienen **un caso real cerrado** (emergencia #1). Si se quiere partir de cero, reiniciar base local y casos de Bonita juntos (sección 4.3).

### Aviso: redesplegar el proceso borra los casos (visto el 25/11/2026)

Tras reabrir Bonita, el proceso RescueSync 1.2 tenía un id nuevo (se volvió a desplegar) y el caso archivado 3002 **ya no existía**, aunque la emergencia #1 seguía `CERRADA` en la base local. Por eso:
- Generar los casos para el dashboard **después del último Run/Deploy** en Studio, y no volver a desplegar hasta terminar la defensa. Tras cada reapertura de Bonita, comprobar `GET /bonita/API/bpm/archivedCase`.
- Si se limpia la base local reiniciando los ids, **borrar también los compromisos del SN que se use** (Render): si no, `(recurso, oferta_ref)` se repite y el SN devuelve el compromiso viejo sin descontar stock. Sin reiniciar ids no hay problema.

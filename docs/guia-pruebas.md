# Guía paso a paso de las pruebas para la muestra

Cubre los flujos que pide la cátedra. Complementa [`defensa-oral.md`](defensa-oral.md) (pautas, reparto y preguntas).

**Qué tan probado está cada flujo:**

| Prueba | Flujo | Estado |
|---|---|---|
| P1 | Camino feliz con consorcio | Probado en pantalla el 25/11 contra Render |
| P2 | Reabrir la convocatoria | **No probado en pantalla con esta versión** (el 20/11 se probó por API). Pasos tomados del código |
| P3 | Continuar con cobertura parcial | Probado en pantalla el 20/11 (versión anterior, SN local). Pasos revisados contra el código actual |
| — | Reformular lotes | **No existe en el sistema** (ver `defensa-oral.md`, sección 3.2) |
| P4 | Orquestación | Verificado en el código; falta ensayar la demostración |
| P5 | Dashboard y exportaciones | Dashboard visto; **exportaciones sin probar en pantalla** |

---

## 0. Preparación (antes de cualquier prueba)

### 0.1 Levantar, en este orden

1. **Docker Desktop**: esperar a que `rescuesync-postgres` figure `healthy` (`docker ps`).
2. **Bonita Studio** y el proceso RescueSync 1.2 desplegado. Si ya está desplegado y tiene casos que quieren conservar, **no volver a ejecutar ni desplegar** (puede borrarlos).
3. **Backend** (terminal 1):
   ```powershell
   cd C:\Users\54222\Desktop\Facultad\4to\DSSD\rescuesync\rescuesync-backend
   mvn spring-boot:run
   ```
   Esperar `Started RescueSyncApplication`. El plazo de 2 minutos sale del `.env`.
4. **Frontend** (terminal 2):
   ```powershell
   cd C:\Users\54222\Desktop\Facultad\4to\DSSD\rescuesync\rescuesync-frontend
   npm run dev
   ```
5. **Render**: abrir https://rescuesync-sistema-nacional-2qwh.onrender.com/actuator/health y esperar la respuesta.

### 0.2 Un perfil de Chrome por rol

La sesión es una sola cookie por perfil del navegador: iniciar sesión con otro rol en otra pestaña o ventana del mismo perfil provoca "No tiene permisos". Contraseña de todos: `123456`.

| Perfil | Usuario | Pestañas que usa |
|---|---|---|
| Operador | `operador1.laplata@test.com` | Registrar emergencia, Seleccionar ofertas |
| Coordinador | `centro.coordinador@test.com` | Lotes, Despliegues |
| Cruz Roja | `representante1.cruzroja@test.com` | Ofertas, Notificaciones, Actividades |
| Cáritas | `representante1.caritas@test.com` | Ofertas, Notificaciones, Actividades |
| Dirección | `direccion@test.com` | Indicadores |

### 0.3 Datos que hay que respetar

- Lotes **solo de Alimentos**, unidad **`raciones`** (escrita así). Otro tipo o unidad deja las ofertas "no seleccionables" y **la emergencia queda trabada sin salida**.
- Stock en el Sistema Nacional: Cruz Roja **1000**, Cáritas **500**. Se repone al liberar; no ofrecer más que eso sumando todos los lotes.

| ONG | Gravedad Baja o Media | Gravedad Alta | Gravedad Crítica |
|---|---|---|---|
| Cruz Roja | Principal | Principal | Apoyo secundario |
| Cáritas | Principal | Apoyo secundario | Apoyo secundario |

### 0.4 Reglas que valen en todas las pruebas

- El plazo (2 min) corre **desde que se publica** la convocatoria. Tener las ventanas de las ONGs abiertas antes de publicar.
- Si una pantalla no refleja un cambio hecho por otro rol, pulsar **Actualizar**.
- **"Evaluar ofertas" puede tardar** (se registraron 170 y 200 s; en otra prueba unos 20 s). Mientras tanto, esperar: no repetir la acción ni usar "Omitir" en el Portal de Bonita.
- "Completar monitoreo" y "Finalizar actividad" se habilitan **recién al escribir una observación**.
- Anotar la hora si algo falla: sirve para buscarlo en los logs.

---

## Bloque común: ciclo de ejecución de cada oferta

Se usa en P1, P2 y P3 después de seleccionar las ofertas. El sistema atiende **una oferta a la vez**: repetir C1 a C4 por cada oferta seleccionada (1,5 a 2 min cada vuelta).

| Paso | Perfil | Acción | Resultado esperado |
|---|---|---|---|
| C1 | ONG de la oferta · Notificaciones | **Confirmar recepción** | Bonita compromete en Render. Emergencia `En ejecución` |
| C2 | Coordinador · Despliegues | Elegir la emergencia → **Actualizar** → (opcional: escribir y **Registrar novedad**) → escribir observación → **Completar monitoreo** → confirmar | La actividad queda esperando a la ONG |
| C3 | ONG · Actividades | **Actualizar** → escribir observación de cierre → **Finalizar actividad** → **Finalizar** | Bonita libera en Render |
| C4 | ONG · Actividades | Esperar unos segundos → **Actualizar** | **"Recursos liberados"** |

**El C4 es obligatorio:** ese "Actualizar" es el que hace avanzar a la oferta siguiente. Después de la última, es el que muestra la emergencia como **Cerrada**.

Si una ONG tiene la notificación pero Despliegues muestra su oferta como "Adjudicación registrada · Esperando tarea de Bonita", todavía no es su turno: empezar por la otra.

---

## P1. Camino feliz con consorcio

**Qué demuestra:** el flujo completo de la pauta 1, con dos ONGs cubriendo un lote. Duración: 10 a 12 min.

**Datos:** Inundación, gravedad **Alta**. Lote A de 300 y lote B de 100 raciones.
Ofertas: Cruz Roja 200 en A; Cáritas 100 en A. En B, Cruz Roja 100.
Para la versión corta (dos vueltas): un solo lote de 200, Cruz Roja 150 y Cáritas 50.

| # | Perfil | Acción | Resultado esperado |
|---|---|---|---|
| 1 | Operador | Enviar el formulario **vacío** (muestra la validación) | Errores y foco en el primer campo |
| 2 | Operador | Completar tipo, gravedad, zona y descripción → **Registrar emergencia** | "Emergencia #N registrada". Caso nuevo en Bonita |
| 3 | Coordinador · Lotes | Elegir la emergencia → agregar lote A y lote B | Dos lotes en `Borrador` |
| 4 | Coordinador · Lotes | **Publicar convocatoria** → **Publicar** | `Convocatoria publicada`; lotes de solo lectura. **Empieza el plazo** |
| 5 | Cruz Roja · Ofertas | Elegir convocatoria y lote → cantidad → **Registrar oferta** (A y B) | Aparecen en "Mis ofertas" |
| 6 | Cáritas · Ofertas | Oferta de 100 en el lote A | Registrada |
| 7 | Cáritas · Ofertas | **Editar oferta** (de 100 a 110) → **Guardar cambios** → **Ver historial de versiones** | Versión 2 con "antes 100", autor y fecha |
| 8 | — | Esperar el vencimiento. Mostrar en Bonita el timer | — |
| 9 | Coordinador · Lotes | **Actualizar cobertura** | Cada lote "Completa"; solo habilitado "Confirmar cobertura completa" |
| 10 | Coordinador · Lotes | **Confirmar cobertura completa** | Bonita corre "Evaluar ofertas" contra Render. **Esperar** |
| 11 | Operador · Seleccionar ofertas | **Actualizar** → elegir la emergencia | Cruz Roja "Principal", Cáritas "Apoyo secundario" |
| 12 | Operador · Seleccionar ofertas | Marcar ofertas → **Confirmar selección** | "Selección confirmada". Emergencia `Adjudicada` |
| 13 | ONGs y Coordinador | **Bloque común** C1 a C4 por cada oferta | Todas con "Recursos liberados" |
| 14 | Cualquiera | **Actualizar** en Actividades o Despliegues | Emergencia **Cerrada**; caso archivado en Bonita |

En la muestra conviene seleccionar **2 ofertas** como máximo, por el tiempo de cada vuelta.

---

## P2. Cobertura insuficiente: reabrir la convocatoria

**Qué demuestra:** el Coordinador pide una nueva ventana manteniendo los mismos lotes.

**Datos:** gravedad **Media**. Un lote de **300** raciones. Primera ventana: Cruz Roja ofrece **150**.

| # | Perfil | Acción | Resultado esperado |
|---|---|---|---|
| 1 | Operador, Coordinador | Registrar, agregar el lote y publicar (como P1, pasos 2 a 4) | Convocatoria publicada |
| 2 | Cruz Roja · Ofertas | Ofrecer 150 | Registrada |
| 3 | Coordinador · Lotes | Al vencer, **Actualizar cobertura** | Lote "Parcial". Habilitados "Resolver cobertura parcial" y "Solicitar nueva convocatoria" |
| 4 | Coordinador · Lotes | **Solicitar nueva convocatoria** | "La evaluación indicó que hace falta una nueva convocatoria" |
| 5 | Coordinador · Lotes | **Publicar nuevamente** (si no aparece, **Actualizar cobertura**) | "Convocatoria reabierta con un nuevo plazo". Empieza otro plazo de 2 min |
| 6 | Cáritas · Ofertas | Ofrecer **150** en el mismo lote | La oferta de Cruz Roja se conserva |
| 7 | Coordinador · Lotes | Al vencer, **Actualizar cobertura** → **Confirmar cobertura completa** | Lote "Completa" (300 de 300) |
| 8 | Operador | Seleccionar (para ir rápido, ambas o una) | Adjudicada |
| 9 | ONGs y Coordinador | **Bloque común** | Emergencia Cerrada |

Cada ONG tiene **una oferta por lote**: para aumentar su aporte en la segunda ventana, la ONG **edita** la oferta existente en vez de cargar otra.

**Variante sin ofertas:** si no ofrece nadie, "Resolver cobertura parcial" queda deshabilitado y solo se puede reabrir.

---

## P3. Cobertura insuficiente: continuar con cobertura parcial

**Qué demuestra:** el proceso avanza a validación y adjudicación con lo disponible, dejando registrada la justificación.

**Datos:** gravedad **Media**. Un lote de **300** raciones. Cruz Roja ofrece **120**.

| # | Perfil | Acción | Resultado esperado |
|---|---|---|---|
| 1 | Operador, Coordinador | Registrar, agregar el lote y publicar | Convocatoria publicada |
| 2 | Cruz Roja · Ofertas | Ofrecer 120 | Registrada |
| 3 | Coordinador · Lotes | Al vencer, **Actualizar cobertura** → **Resolver cobertura parcial** | "Cobertura parcial registrada. Indicá la resolución". Aparece el campo de resolución |
| 4 | Coordinador · Lotes | Escribir la justificación → **Continuar con cobertura parcial** | "Resolución registrada". Queda visible el texto. Bonita evalúa contra Render. **Esperar** |
| 5 | Operador · Seleccionar ofertas | **Actualizar** → marcar → **Confirmar selección** | Adjudicada |
| 6 | ONG y Coordinador | **Bloque común** | Emergencia Cerrada |
| 7 | Dirección | Ver la tabla de proyectos | Esa emergencia con cobertura del **40 %** |

Son **dos clics distintos**: primero la decisión (paso 3) y después la resolución (paso 4). El proceso no avanza con el primero solo.

---

## Cómo mostrar los tres caminos en la muestra

El guion del grupo encadena las decisiones sobre **una** emergencia:

1. Antes de la llamada o durante el camino feliz: publicar una emergencia con una oferta insuficiente.
2. Al vencer: **Solicitar nueva convocatoria** → **Publicar nuevamente** (P2, pasos 3 a 5).
3. **Reformular lotes:** no existe. Resolverlo con el grupo antes de la muestra.
4. Al vencer la segunda ventana, sin agregar ofertas: **Resolver cobertura parcial** → resolución → **Continuar** (P3, pasos 3 y 4).

Cada vencimiento suma 2 minutos de espera: planificar qué se explica mientras tanto. **Ensayar este encadenado completo al menos una vez; no está probado.**

---

## P4. Demostración de orquestación (pauta 3)

Hacerla **durante** P1.

1. **Network del navegador:** antes de empezar, en el perfil que se vaya a mostrar: `F12` → Network → filtro **Fetch/XHR** → tildar **Preserve log**.
   - Esperado: todas las llamadas van a `localhost` (`/api/...`). **Ninguna** a `onrender.com`.
   - En el filtro de texto, escribir `onrender`: la lista tiene que quedar vacía.
2. **Bonita Studio:** mostrar los conectores de "Evaluar ofertas", "Comprometer recursos" y "Liberar recursos", y la variable `sistemaNacionalBaseUrl`.
3. **Sistema Nacional:** mostrar el stock antes y después del compromiso con `GET /api/recursos/disponibilidad` (Swagger de Render, con sesión de admin del SN). Abrirlo en un perfil de navegador **distinto** al que muestra Network, para que no parezca una llamada de la aplicación.
4. **Logs de Render** (panel del servicio): las llamadas de compromiso y liberación. Verificar en el ensayo que el log las muestre; no lo comprobé.

---

## P5. Dashboard y exportaciones (pauta 4)

Con **Dirección**. Hacerlo después de tener P1, P2 y P3 cerradas, y **al menos 5 minutos después** del último cierre (los datos de Bonita se guardan en caché).

| # | Acción | Qué verificar | Quién la presenta |
|---|---|---|---|
| 1 | Resumen (KPIs) | Totales y % de finalizados coherentes con lo hecho | Juan |
| 2 | Botones **JSON**, **CSV** y **Excel** | Los tres descargan y se abren bien en Excel. **Nunca se probó desde la pantalla** | Juan |
| 3 | Suministros movilizados | Cantidades en raciones del mes actual | Ezequiel |
| 4 | Cobertura colaborativa (consorcios) | P1 cuenta como lote con consorcio | Santiago |
| 5 | Proyectos por estado y cobertura | Cerradas, y la parcial con menos de 100 % | Valentín |
| 6 | Tiempos por etapa | **Error conocido:** muestra `0 h` en etapas de menos de 3 min y Convocatoria no cuenta la espera | Franco |
| 7 | Filtros: fechas, municipio, tipo de desastre y tipo de recurso | Los números cambian. El tipo de recurso solo afecta suministros y consorcios | Todos |

**Franco:** el error de "Tiempos por etapa" está diagnosticado y el arreglo quedó pendiente. Decidirlo antes de la muestra; si no se arregla, tiene que saber explicarlo.

Para que el filtro por tipo de desastre muestre diferencias, usar **un tipo distinto** en P1, P2 y P3 (por ejemplo Inundación, Incendio y Tormenta severa).

---

## Orden recomendado de las sesiones de prueba

1. **P1 corta** (un lote, dos ofertas), para confirmar que el entorno funciona.
2. **P3** (cobertura parcial).
3. **P2** (reabrir).
4. Encadenado de caminos alternativos.
5. **P1 completa, cronometrada**, con P4.
6. **P5**.

## Dejar listos los datos para la muestra

- Los casos cerrados de P1, P2 y P3 sirven como datos del dashboard. Hacerlos **después del último despliegue** en Studio.
- Después de reabrir Bonita, entrar a Indicadores y comprobar que "casos finalizados" de Tiempos por etapa siga contando los casos. Si bajó, se perdieron y hay que repetirlos.
- No dejar emergencias trabadas o a medias. Si queda alguna, hay que limpiarla (base local **y** su caso de Bonita).
- No reiniciar los ids de la base local sin borrar también los compromisos de Render.
- El día de la muestra: sección 0 completa, sesiones iniciadas en todos los perfiles (duran 24 h) y video o capturas de respaldo.

## Registro de pruebas

| Fecha | Prueba | Quién | Resultado | Observaciones |
|---|---|---|---|---|
| 25/11 | P1 corta | Valentín | OK | "Evaluar ofertas" tardó 200 s |
| | | | | |
| | | | | |

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| "No tiene permisos para realizar esta operación" | Otra sesión pisó la cookie. Abrir http://localhost:8081/api/auth/me, cerrar sesión y volver a entrar; usar perfiles separados |
| "Evaluar ofertas" no avanza | Abrir el `health` de Render y esperar. Si pasan más de 4 min, revisar el caso en Bonita |
| Ofertas "no seleccionables" | Tipo de recurso sin certificación o unidad distinta de `raciones`. Esa emergencia no tiene salida: empezar otra |
| El caso no pasa a la oferta siguiente | **Actualizar** en Actividades o Despliegues |
| Botón de monitoreo o finalización deshabilitado | Escribir la observación |
| El cierre de convocatoria cae en 2 días | El backend no leyó el `.env`: arrancarlo desde `rescuesync-backend` y registrar otra emergencia |
| Despliegues dice "Incidente de integración" | Una tarea de Bonita falló; ese caso no se recupera desde la aplicación. No usar "Omitir" |

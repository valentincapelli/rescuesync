# Preparación y guion de la demo

Pensado para una demo de unos 15 minutos. Los datos de esta guía salen de la prueba integral del 20/11/2026.

## 1. Antes de la demo (30 minutos antes)

### Servicios (todos deben responder)

| Servicio | Puerto | Comprobación |
|---|---|---|
| Frontend | 5173 | abre el login |
| Backend | 8081 | `GET /actuator/health` responde 401 (pide login): eso indica que está arriba |
| Bonita (proceso RescueSync 1.2) | 8080 | abre el Portal |
| Sistema Nacional local | 8082 | `GET /` responde 401 (pide login) |

- Bonita debe haberse abierto con las variables `RESCUESYNC_BONITA_SERVICE_EMAIL/PASSWORD` cargadas (ver `docs/e3-08-e3-09-continuidad.md`). Sin ellas, "Evaluar ofertas" falla.
- **Plazo de la convocatoria:** el backend lo toma de `PLAZO_CONVOCATORIA_MS` (por defecto, 2 días). Para la demo, usar `180000` (3 minutos) y avisar al evaluador de que es una configuración de pruebas.

### Qué Sistema Nacional usar

Desde el 24/11/2026 el proceso de Bonita (`rescue-sync.bos`) apunta **por defecto al SN de Render**, que es el que se usa en la demo. Para usar el SN local hay que cambiar el valor inicial de `sistemaNacionalBaseUrl` en Studio a `http://localhost:8082` y redesplegar.

Si hay que mostrar el SN hosteado (es lo que pide el guion de defensa del grupo):
1. Despertarlo 2 minutos antes (en frío tarda 40 a 60 s): `curl https://rescuesync-sistema-nacional-2qwh.onrender.com/actuator/health`. Si Render no responde, no usarlo.
2. Confirmar que el deploy incluye el stock: la respuesta de `POST /api/certificaciones/validar` debe traer `disponibilidad`. Verificado el 24/11/2026 tras el redeploy; si falta, no se bloquea la selección por stock.
3. Comprobar que el proceso desplegado sea el del `.bos` actual: la variable `sistemaNacionalBaseUrl` de un caso nuevo tiene que mostrar la URL de Render (ver `docs/defensa-oral.md`, sección 3.1).

### Datos

- El seed del SN local (`scripts/seed-sistema-nacional-demo.sql`) crea solo las ONGs y los usuarios. **Las certificaciones y el inventario se cargan por la API del SN**, como admin (ver la sección 1 de `docs/e3-10-prueba-integracion.md`). En la prueba de esta sesión el SN local tenía: Cruz Roja con certificación de Alimentos (`INTERMEDIO`, riesgo hasta `ALTO`) y 200 raciones, y Cáritas con 40 raciones.
- Comprobar antes de la demo, para cada ONG, que `POST /api/certificaciones/validar` devuelva la capacidad y la `disponibilidad` esperadas.
- Para mostrar un consorcio, Cáritas necesita una certificación de Alimentos (`POST /api/ongs/2/certificaciones` con `ALIMENTOS`, `BASICO`, riesgo `MEDIO`; lo agregué en esta sesión).
- Para un dashboard poblado: `scripts/seed-indicadores-demo.sql` (13 emergencias simuladas, sin caso de Bonita, con consorcios).
- **Regla de oro:** reiniciar la base del backend y los casos de Bonita **juntos o ninguno**. Si solo se reinicia uno, quedan emergencias cuyo caso ya no existe en Bonita (en la prueba fueron #9, #10 y #11).
- Evitar datos de pruebas anteriores con prefijos como `[E2E-…]`: se ven en el dashboard.

### Usuarios (contraseña `123456` para todos)

| Rol | Email |
|---|---|
| Operador Municipal | `operador1.laplata@test.com` |
| Centro Coordinador | `centro.coordinador@test.com` |
| Representante ONG (Cruz Roja) | `representante1.cruzroja@test.com` |
| Representante ONG (Cáritas) | `representante1.caritas@test.com` |
| Dirección | `direccion@test.com` |

## 2. Guion de recorrido

Usar una ventana de navegador y cambiar de usuario con "Cerrar sesión".

1. **Operador · Registrar emergencia.** Registrar una inundación de gravedad Alta. Mostrar la validación enviando el formulario vacío (el foco va al primer error).
2. **Centro Coordinador · Lotes.** Cargar dos lotes de Alimentos en raciones (300 y 100). Publicar la convocatoria (modal de confirmación).
3. **ONG · Ofertas.** Cruz Roja ofrece 250 en el lote de 300 (supera su stock de 200) y 100 en el de 100. **Hacerlo dentro del plazo.**
4. **Centro Coordinador · Cobertura.** Al vencer el plazo, "Actualizar cobertura": el lote de 300 queda parcial. "Resolver cobertura parcial" y escribir la justificación. Explicar que también existe "Solicitar nueva convocatoria".
5. **Operador · Seleccionar ofertas.** El lote de 300 muestra la oferta como no seleccionable por stock insuficiente, con el motivo. Seleccionar la del lote de 100.
6. **ONG · Notificaciones.** Confirmar la recepción. Bonita compromete los recursos en el Sistema Nacional.
7. **Centro Coordinador · Despliegues.** "Actualizar", registrar una novedad y "Completar monitoreo".
8. **ONG · Actividades.** "Finalizar actividad". Al actualizar, queda "Recursos liberados" y la emergencia pasa a Cerrada.
9. **Dirección · Indicadores.** Mostrar los KPI, filtrar por tipo de desastre y señalar los tiempos por etapa.

**Consorcio (opcional, +3 minutos):** un lote de 100 raciones con una oferta de 60 de Cruz Roja (principal) y otra de 40 de Cáritas (apoyo). Seleccionar ambas; el proceso las atiende una detrás de otra. El dashboard pasa a mostrar un consorcio.

## 3. Limitaciones conocidas (conviene conocerlas antes de que pregunten)

- Si una tarea automática de Bonita falla, el caso queda trabado: el Portal Community solo ofrece "Omitir", que **no** debe usarse (da la tarea por hecha sin ejecutarla). La aplicación lo muestra como incidente con el motivo. Prevención: SN despierto y stock suficiente.
- Con la convocatoria publicada, los lotes son de solo lectura. "Solicitar nueva convocatoria" reabre el plazo, pero no permite reformular los lotes.
- El control de stock usa el valor que informa el SN al evaluar las ofertas. Si otra emergencia compromete esas raciones después, el compromiso puede fallar igual.
- Los exportes JSON, CSV y Excel del dashboard no se probaron.
- El frontend no tiene tests automáticos.

## 4. Si algo falla durante la demo

| Síntoma | Qué hacer |
|---|---|
| "Evaluar ofertas" no avanza | Probablemente el SN está dormido o caído. Verificar su `health` y esperar. |
| La selección responde 409 por stock | Es el control funcionando: elegir otra oferta o explicar el motivo. |
| Despliegues dice "Incidente de integración" | Mostrar el motivo y pasar a otra emergencia: ese caso no se recupera desde la aplicación. |
| La ONG ve "Liberación pendiente" | Pulsar "Actualizar" en Actividades; si persiste, "Actualizar" en Despliegues del Centro Coordinador. |

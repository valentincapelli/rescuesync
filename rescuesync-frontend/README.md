# RescueSync – Frontend

App web de RescueSync (DSSD 2026 – Grupo 21).

**Stack:** React 18 · TypeScript · Vite

## Cómo levantarlo

1. Instalar dependencias (una sola vez):

   ```bash
   npm install
   ```

2. Backend corriendo en `:8081` (ver `../rescuesync-backend/README.md`), con Postgres
   arriba. El CORS del backend ya permite `http://localhost:5173` por defecto.

3. Levantar el servidor de desarrollo:

   ```bash
   npm run dev
   ```

   Abre en http://localhost:5173. Arriba de todo se ve si la conexión con el
   backend está OK (llama a `GET /api/info`); debajo hay una pestaña por rol
   (todavía no hay login, eso llega con E2-10):

   - **Municipio · Registrar emergencia**: formulario de alta de emergencia (E2-05).
   - **Centro Coordinador · Lotes**: desglose de una emergencia en lotes y
     publicación de la convocatoria (E2-07, ver abajo).

**Build de producción:** `npm run build` (corre `tsc -b` y falla si hay errores
de tipos). `npm run preview` sirve ese build localmente.

## Pantalla de lotes (E2-07)

Corresponde a las tareas *Revisar y desglosar emergencia* y *Publicar convocatoria*
del Centro Coordinador Regional en el BPMN.

1. Se elige una emergencia del desplegable (`GET /api/emergencias`). Las que todavía
   se pueden desglosar (`REGISTRADA` / `EN_REVISION`) aparecen primero; el botón
   **Actualizar** vuelve a pedir la lista.
2. Se muestra un resumen de la emergencia y la tabla de sus lotes
   (`GET /api/emergencias/{id}/lotes`).
3. Con el formulario se agregan lotes: tipo de recurso, cantidad, unidad y descripción.
   La unidad es texto libre, con sugerencias según el tipo de recurso. Cada lote nuevo
   queda en **Borrador** y, con el primero, la emergencia pasa a **En revisión**.
4. Mientras no se publique, cada lote se puede **Editar** (precarga el formulario,
   `PUT`) o **Borrar** (pide confirmación, `DELETE`).
5. **Publicar convocatoria** (`POST /api/emergencias/{id}/convocatoria`) pide
   confirmación y está deshabilitado si no hay lotes o si hay uno en edición. Pasa
   los lotes a **Publicado** y la emergencia a **Convocatoria publicada**; desde ahí
   la pantalla queda en solo lectura y las ONGs ya pueden ofertar.

Las validaciones del formulario replican las de `CrearLoteRequest` (descripción
≤ 300, cantidad entera > 0, unidad ≤ 30). Si el backend responde 400, el error se
marca en el campo; si responde 409 (ej. convocatoria ya publicada), se muestra
arriba como mensaje.

## Configuración

La URL del backend se lee de `VITE_API_URL` (ver `.env.example`). Por defecto
apunta a `http://localhost:8081/api`. Para pisarla, copiar `.env.example` a
`.env` y editar.

## Estructura

```
src/
├── main.tsx                   Punto de entrada, monta <App />
├── App.tsx                    Layout raíz: BackendStatus + pestañas por rol
├── components/
│   ├── BackendStatus.tsx      Chequeo de conexión con el backend (GET /api/info)
│   └── LoteForm.tsx           Formulario de alta/edición de un lote (E2-07)
├── pages/
│   ├── AltaEmergenciaPage.tsx Formulario de alta de emergencia (E2-05)
│   └── LotesPage.tsx          Desglose en lotes + publicar convocatoria (E2-07)
├── types/
│   ├── emergencia.ts          Enums, labels y tipos de emergencia (model/ y dto/)
│   └── lote.ts                TipoRecurso, EstadoLote, labels, unidades sugeridas, Lote/LoteRequest
├── api/
│   ├── client.ts               fetch wrapper genérico (apiGet, apiPost, apiPut, apiDelete, ApiError)
│   ├── info.ts                 GET /api/info
│   ├── emergencias.ts          POST/GET /api/emergencias, POST /{id}/convocatoria
│   └── lotes.ts                GET/POST/PUT/DELETE /api/emergencias/{id}/lotes
└── vite-env.d.ts              Tipado de las env vars (VITE_API_URL)
```

## Convenciones (a seguir en las próximas tareas)

- Un archivo en `api/` por recurso del backend (`lotes.ts`, `ofertas.ts`, …),
  todos usando `apiGet`/`apiPost`/`apiPut`/`apiDelete` de `client.ts`.
- Los tipos de cada recurso en `types/` reflejan los DTOs `XxxResponse`/`XxxRequest`
  del backend (`rescuesync-backend/.../dto/`), no las entidades JPA — y los enums
  deben copiar EXACTAMENTE los valores de `rescuesync-backend/.../model/*.java`
  (son los strings que viajan en el JSON).
- Una página por pantalla en `src/pages/` (o `src/features/<recurso>/` si conviene
  agrupar por dominio), y una pestaña en `App.tsx`. Próxima: E2-09 (ofertas).
- El manejo de errores del formulario usa `ApiError.fieldErrors` cuando el backend
  devuelve 400 de validación (ver `GlobalExceptionHandler`), para marcar el campo
  puntual en rojo en vez de un error genérico.

## Estado de compilación

Con los cambios de E2-07, `npm install` + `tsc -b` + `vite build` corren sin errores,
y la pantalla de lotes se probó de punta a punta contra un backend simulado.

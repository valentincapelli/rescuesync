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
   backend está OK (llama a `GET /api/info`); debajo, el formulario de alta de
   emergencia.

**Build de producción:** `npm run build` (corre `tsc -b` y falla si hay errores
de tipos). `npm run preview` sirve ese build localmente.

## Configuración

La URL del backend se lee de `VITE_API_URL` (ver `.env.example`). Por defecto
apunta a `http://localhost:8081/api`. Para pisarla, copiar `.env.example` a
`.env` y editar.

## Estructura

```
src/
├── main.tsx                   Punto de entrada, monta <App />
├── App.tsx                    Layout raíz: BackendStatus + páginas
├── components/
│   └── BackendStatus.tsx      Chequeo de conexión con el backend (GET /api/info)
├── pages/
│   └── AltaEmergenciaPage.tsx Formulario de alta de emergencia (E2-05)
├── types/
│   └── emergencia.ts          Enums y tipos que espejan el backend (model/ y dto/)
├── api/
│   ├── client.ts               fetch wrapper genérico (apiGet, apiPost, ApiError)
│   ├── info.ts                 GET /api/info
│   └── emergencias.ts          POST /api/emergencias
└── vite-env.d.ts              Tipado de las env vars (VITE_API_URL)
```

## Convenciones (a seguir en las próximas tareas)

- Un archivo en `api/` por recurso del backend (`lotes.ts`, `ofertas.ts`, …),
  todos usando `apiGet`/`apiPost` de `client.ts`.
- Los tipos de cada recurso en `types/` reflejan los DTOs `XxxResponse`/`XxxRequest`
  del backend (`rescuesync-backend/.../dto/`), no las entidades JPA — y los enums
  deben copiar EXACTAMENTE los valores de `rescuesync-backend/.../model/*.java`
  (son los strings que viajan en el JSON).
- Una página por pantalla en `src/pages/` (o `src/features/<recurso>/` si conviene
  agrupar por dominio). Próximas: E2-07 (lotes), E2-09 (ofertas).
- El manejo de errores del formulario usa `ApiError.fieldErrors` cuando el backend
  devuelve 400 de validación (ver `GlobalExceptionHandler`), para marcar el campo
  puntual en rojo en vez de un error genérico.

## Nota sobre esta entrega

El scaffold inicial (`package.json`, `vite.config.ts`, `tsconfig*.json`) se armó
a mano porque el entorno donde se generó no tenía salida a `registry.npmjs.org`
(bloqueado por política de red), así que **todavía no se corrió `npm install` ni
`npm run build`/`tsc` sobre este código** — revisado a mano pero no compilado.
Corré `npm install && npm run build` la primera vez que lo abras para confirmar
que no quedó ningún error de tipos.

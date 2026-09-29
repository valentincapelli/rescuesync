# RescueSync – Frontend

App web de RescueSync (DSSD 2026 – Grupo 21).

**Stack:** React 18 · TypeScript · Vite

## Cómo levantarlo

1. Instalar dependencias (una sola vez):

   ```bash
   npm install
   ```

2. Backend corriendo en `:8081` (ver `../rescuesync-backend/README.md`). El CORS del
   backend ya permite `http://localhost:5173` por defecto.

3. Levantar el servidor de desarrollo:

   ```bash
   npm run dev
   ```

   Abre en http://localhost:5173. La pantalla inicial llama a `GET /api/info`
   del backend y muestra si la conexión está arriba o no — sirve como smoke
   test de que front y back están bien cableados.

**Build de producción:** `npm run build` (corre `tsc -b` y falla si hay errores
de tipos). `npm run preview` sirve ese build localmente.

## Configuración

La URL del backend se lee de `VITE_API_URL` (ver `.env.example`). Por defecto
apunta a `http://localhost:8081/api`. Para pisarla, copiar `.env.example` a
`.env` y editar.

## Estructura

```
src/
├── main.tsx        Punto de entrada, monta <App />
├── App.tsx          Layout raíz + chequeo de conexión con el backend
├── api/
│   ├── client.ts     fetch wrapper genérico (apiGet) + ApiError
│   └── info.ts       Llama a /api/info (tipado igual al InfoResponse del backend)
└── vite-env.d.ts    Tipado de las env vars (VITE_API_URL)
```

## Convenciones (a seguir en las próximas tareas)

- Un archivo en `api/` por recurso del backend (`emergencias.ts`, `lotes.ts`,
  `ofertas.ts`, …), todos usando `apiGet`/futuras `apiPost`/`apiPut` de `client.ts`.
- Los tipos de cada recurso (`Emergencia`, `Lote`, `Oferta`, …) reflejan los DTOs
  `XxxResponse`/`XxxRequest` del backend (`rescuesync-backend/.../dto/`), no las
  entidades JPA.
- Componentes de pantalla completa en `src/pages/` (o `src/features/<recurso>/`
  si conviene agrupar por dominio) a medida que se agreguen las pantallas de
  E2-05 (alta de emergencia), E2-07 (lotes) y E2-09 (ofertas).

## Nota sobre esta entrega

El scaffold (`package.json`, `vite.config.ts`, `tsconfig*.json`, `src/`) se armó
a mano porque el entorno donde se generó no tenía salida a `registry.npmjs.org`
(bloqueado por política de red). Es exactamente lo que generaría
`npm create vite@latest -- --template react-ts`, sin nada de más. Al correr
`npm install` desde una terminal con acceso normal a internet no debería haber
ninguna diferencia.

# Cómo correr la demo en otra PC

Guía para preparar una PC desde cero (Windows, PowerShell). Después, las pruebas están en [`guia-pruebas.md`](guia-pruebas.md).

> **Aviso:** estos pasos salen de la configuración del repo y de la PC donde funciona la demo. **Todavía no se probaron completos en otra máquina**, en particular la importación del `.bos`. Hacerlo con tiempo y anotar lo que falle.

## Qué corre dónde

| Componente | Dónde | Puerto |
|---|---|---|
| PostgreSQL de RescueSync | Docker, en cada PC | 5433 |
| Backend | Cada PC | 8081 |
| Frontend | Cada PC | 5173 |
| Bonita Studio y su Runtime | Cada PC | 8080 |
| Sistema Nacional | **Render, compartido por todos** | — |

No hace falta levantar el Sistema Nacional ni cargarle datos: ya está en https://rescuesync-sistema-nacional-2qwh.onrender.com

## 1. Antes de empezar: Render es compartido

1. **Referencias de oferta.** El Sistema Nacional identifica cada compromiso por ONG y número de oferta. Si dos PC usan el mismo número, devuelve **el compromiso viejo como si fuera nuevo, sin descontar stock**. Por eso cada PC usa un rango propio (paso 6.3).

   | PC | Las ofertas empiezan en |
   |---|---|
   | Principal (Valentín) | su numeración actual (menos de 1000) |
   | Integrante 2 | 1000 |
   | Integrante 3 | 2000 |
   | Integrante 4 | 3000 |
   | Integrante 5 | 4000 |

   Anotar quién tiene cada rango. **Si alguien borra su base y empieza de cero, tiene que usar un rango nuevo** (por ejemplo 1500) o pedir que se limpien los compromisos de Render.

2. **Stock.** Es el mismo para todos. Un recorrido a medias deja raciones comprometidas hasta que se libere. **Terminar siempre los recorridos** y avisar al grupo antes de probar.

## 2. Programas

| Programa | Versión |
|---|---|
| Git | cualquiera reciente |
| JDK | **21 o superior** (el backend lo exige). Verificar con `mvn -v`, línea "Java version" |
| Maven | 3.9 |
| Node.js | 22 (con npm) |
| Docker Desktop | reciente |
| Bonita Studio Community | **2025.2-u0** (Runtime 10.4.0). Usar esta versión |
| Google Chrome | para los perfiles por rol |

`java -version` y Maven pueden usar JDK distintos: el que importa para compilar es el de `mvn -v` (variable `JAVA_HOME`).

Dejar **varios GB libres** en disco: con el disco lleno, Docker queda con errores de lectura y escritura.

## 3. Código

```powershell
git clone https://github.com/valentincapelli/rescuesync.git
cd rescuesync
git checkout development
git pull
```

Todos los comandos siguientes se ejecutan desde la carpeta `rescuesync`, salvo que se indique otra.

## 4. Variables de entorno de Windows

Las usan el backend y Bonita. **Configurarlas antes de abrir Bonita Studio.**

| Variable | Valor |
|---|---|
| `RESCUESYNC_BONITA_SERVICE_EMAIL` | `servicio.bonita@rescuesync.local` |
| `RESCUESYNC_BONITA_SERVICE_PASSWORD` | Una clave que elige cada uno. El backend crea el usuario con ella |
| `SISTEMA_NACIONAL_BONITA_USERNAME` | `bonita@sistemanacional.test` |
| `SISTEMA_NACIONAL_BONITA_PASSWORD` | La de esa cuenta en el Sistema Nacional (la de demo del seed `scripts/seed-sistema-nacional-demo.sql`). Si el login falla, pedírsela a Valentín |

Cómo cargarlas: buscar en Windows **"Editar las variables de entorno de esta cuenta"** → **Nueva**, una por una. Después **cerrar todas las terminales y Bonita Studio** y volver a abrirlos.

Comprobar en una terminal nueva:

```powershell
Get-ChildItem env: | Where-Object Name -match 'RESCUESYNC|SISTEMA_NACIONAL' | Select-Object Name
```

Tienen que aparecer las cuatro. No subir claves al repo.

## 5. Archivo `.env` del backend

No está en git. Crear `rescuesync-backend\.env` con este contenido, sin comillas:

```properties
PLAZO_CONVOCATORIA_MS=120000
DB_URL=jdbc:postgresql://localhost:5433/rescuesync
DB_USER=rescuesync
DB_PASSWORD=rescuesync
SERVER_PORT=8081
CORS_ORIGINS=http://localhost:5173
BONITA_URL=http://localhost:8080/bonita
BONITA_USER=walter.bates
BONITA_PASSWORD=bpm
BONITA_PROCESS=RescueSync
```

`PLAZO_CONVOCATORIA_MS=120000` es la ventana de ofertas de 2 minutos. Sin esa línea son 2 días. Guardar como `.env`, **no** como `.env.txt`.

El frontend no necesita `.env`: por defecto usa `http://localhost:8081/api`.

## 6. Base de datos

### 6.1 Levantar PostgreSQL

Con Docker Desktop abierto:

```powershell
docker compose up -d
docker ps
```

`rescuesync-postgres` tiene que figurar `healthy`.

Si la PC **ya tenía** una base de antes, no usarla como está: puede tener datos viejos o casos que no existen en su Bonita. Para borrarla y empezar de cero (**elimina todos los datos locales de RescueSync**):

```powershell
docker compose down -v
docker compose up -d
```

### 6.2 Crear las tablas y cargar los datos

1. Arrancar el backend **una vez** para que cree las tablas:
   ```powershell
   cd rescuesync-backend
   mvn spring-boot:run
   ```
   Esperar `Started RescueSyncApplication`.
2. Con el backend andando, en otra terminal desde `rescuesync`, cargar el seed **una sola vez**:
   ```powershell
   docker cp scripts\seed-backend-demo.sql rescuesync-postgres:/tmp/seed.sql
   docker exec rescuesync-postgres psql -U rescuesync -d rescuesync -v ON_ERROR_STOP=1 -f /tmp/seed.sql
   ```

**No cargar** `seed-indicadores-demo.sql` para la demo: crea emergencias sin caso de Bonita.

### 6.3 Asignar el rango de ofertas

Con el número que le toque a esa PC (sección 1). Ejemplo para el integrante 2:

```powershell
docker exec rescuesync-postgres psql -U rescuesync -d rescuesync -c "ALTER TABLE ofertas ALTER COLUMN id RESTART WITH 1000"
```

Hacerlo **antes de cargar la primera oferta**.

## 7. Conectores de Bonita

Hay que compilarlos e instalarlos en cada PC **antes de importar el proceso**: los `.jar` no incluyen sus dependencias, y Studio las resuelve desde el repositorio Maven local.

```powershell
mvn -f bonita-ofertas-consolidadas-connector\pom.xml install
mvn -f bonita-sistema-nacional-connector\pom.xml clean install
```

Los dos tienen que terminar en `BUILD SUCCESS`.

**No ejecutar** `scripts\configurar_bonita_e3_10.py`: el `.bos` del repo ya está configurado, y el script lo modificaría.

## 8. Proceso en Bonita Studio

1. Abrir Bonita Studio **después** de cargar las variables de entorno.
2. **File → Import → BOS Archive…** → elegir `rescue-sync.bos` (raíz del repo) y resolver los conflictos si los pregunta. Si ya existe un proyecto anterior de RescueSync, no mezclarlo: importar como proyecto nuevo.
3. Revisar en **Extensions** que los dos conectores estén sin errores. Si faltan, agregarlos con **Add → From Maven repository**:

   | Group ID | Artifact ID | Version |
   |---|---|---|
   | `com.grupo21.rescuesync.bonita` | `bonita-ofertas-consolidadas-connector` | `1.0.0-SNAPSHOT` |
   | `com.grupo21.rescuesync.bonita` | `bonita-sistema-nacional-connector` | `1.0.0-SNAPSHOT` |

4. En el pool **RescueSync**, comprobar que la variable `sistemaNacionalBaseUrl` tenga como valor inicial `https://rescuesync-sistema-nacional-2qwh.onrender.com`.
5. **Run / Deploy** del proceso **RescueSync 1.2**.
6. Dejar habilitada **una sola** versión de RescueSync: el backend toma la primera que encuentra por nombre.

El backend entra a Bonita con `walter.bates` / `bpm`, de la organización de ejemplo ACME que viene en el `.bos`.

**Volver a desplegar puede borrar los casos existentes.** Hacerlo antes de generar los casos que se quieran conservar.

## 9. Frontend

En una terminal aparte:

```powershell
cd rescuesync-frontend
npm install
npm run dev
```

La aplicación queda en http://localhost:5173.

## 10. Perfiles de Chrome

Uno por rol: ícono de perfil de Chrome → **Agregar perfil de Chrome** → continuar sin cuenta. La sesión se comparte entre pestañas y ventanas de un mismo perfil.

| Rol | Usuario (contraseña `123456`) |
|---|---|
| Operador | `operador1.laplata@test.com` |
| Coordinador | `centro.coordinador@test.com` |
| Cruz Roja | `representante1.cruzroja@test.com` |
| Cáritas | `representante1.caritas@test.com` |
| Dirección | `direccion@test.com` |

## 11. Orden de arranque de cada día

1. Docker Desktop → `docker compose up -d`.
2. Bonita Studio (arrancar el Runtime; **sin volver a desplegar** si hay casos que conservar).
3. Backend: `cd rescuesync-backend` y `mvn spring-boot:run`. **Siempre desde esa carpeta**, si no, no lee el `.env`.
4. Frontend: `cd rescuesync-frontend` y `npm run dev`.
5. Render: abrir https://rescuesync-sistema-nacional-2qwh.onrender.com/actuator/health y esperar la respuesta.

## 12. Verificación final

- [ ] `rescuesync-postgres` está `healthy`.
- [ ] El backend arrancó sin errores y http://localhost:8081/actuator/health pide autenticación (401): está arriba.
- [ ] Se puede iniciar sesión con los 5 usuarios.
- [ ] Bonita muestra RescueSync 1.2 habilitado, una sola versión.
- [ ] El rango de ofertas está asignado (paso 6.3).
- [ ] **P1 corta** de `guia-pruebas.md` completa hasta `Cerrada`. Es la prueba que confirma que las credenciales, los conectores y Render funcionan.

Qué confirma cada tramo de P1 corta:

| Si funciona… | Queda confirmado |
|---|---|
| Registrar la emergencia | Backend → Bonita |
| El timer vence y se puede confirmar la cobertura | Conector de ofertas y variables `RESCUESYNC_BONITA_SERVICE_*` |
| Aparecen las ofertas para seleccionar con su perfil | Conector nacional y variables `SISTEMA_NACIONAL_BONITA_*` |
| La ONG ve "Recursos liberados" | Compromiso y liberación en Render |

## 13. Problemas frecuentes

| Síntoma | Causa probable y solución |
|---|---|
| El backend no arranca y pide configurar las dos variables | Falta una de las `RESCUESYNC_BONITA_SERVICE_*`, o la terminal se abrió antes de cargarlas |
| Error de compilación por versión de Java | `JAVA_HOME` apunta a un JDK anterior a 21 |
| Puerto 5433, 8080, 8081 o 5173 ocupado | Hay otra instancia corriendo; cerrarla |
| El login falla con todos los usuarios | No se cargó el seed (6.2) |
| Studio muestra errores en los conectores o dependencias | Repetir el paso 7 y volver a agregarlos desde Maven |
| La cobertura no se puede evaluar y Bonita marca `failed` | Faltan las `RESCUESYNC_BONITA_SERVICE_*` en el proceso de Bonita, o no coinciden con las del backend. Reiniciar Studio |
| "Evaluar ofertas" falla | Revisar `SISTEMA_NACIONAL_BONITA_*` y que Render responda |
| "Evaluar ofertas" tarda varios minutos | Se vio con la prueba real (170 y 200 s). Esperar; abrir antes el `health` de Render |
| La convocatoria cierra en 2 días | El backend no leyó el `.env` |
| "No tiene permisos para realizar esta operación" | Dos roles en el mismo perfil de Chrome |
| Ofertas "no seleccionables" | El lote no es de Alimentos o la unidad no es `raciones` |

Una tarea de Bonita en `failed` deja el caso trabado: **no usar "Omitir"** en el Portal. Corregir la causa y probar con una emergencia nueva.

## 14. Para el día de la muestra

- Definir **una PC principal** y, si es posible, **una de respaldo** ya probada de punta a punta.
- La de respaldo necesita sus propios casos cerrados para el dashboard: los datos de una PC no pasan a otra.
- Avisar al grupo que nadie pruebe contra Render durante la muestra.

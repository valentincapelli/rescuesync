# Entrega 2 — Guía de despliegue y prueba End-to-End

Esta guía describe cómo levantar RescueSync desde un entorno local limpio y ejecutar las pruebas funcionales correspondientes a la Entrega 2.

## 1. Alcance de la prueba

La prueba valida:

- PostgreSQL en Docker.
- Backend Spring Boot.
- Frontend React.
- Autenticación y autorización por roles.
- Registro de emergencias.
- Creación, edición y publicación de lotes.
- Registro de ofertas por una ONG.
- Integración inicial con Bonita:
  - conexión con Bonita;
  - creación de una instancia de proceso;
  - envío de variables iniciales;
  - funcionamiento del evento temporizador.

Roles utilizados:

- `OPERADOR_MUNICIPAL`
- `CENTRO_COORDINADOR`
- `REPRESENTANTE_ONG`

> En Entrega 2, las acciones realizadas desde la aplicación web no completan automáticamente las User Tasks equivalentes en Bonita.
>
> La integración implementada para esta entrega comprende el inicio de la instancia y el envío de variables iniciales. La sincronización de las User Tasks mediante la API de Bonita queda como una posible evolución posterior.

---

## 2. Prerrequisitos

Tener instalados:

- Docker Desktop
- Java
- Maven
- Node.js / npm
- Bonita Studio

Servicios utilizados:

| Servicio | Dirección |
|---|---|
| Frontend | `http://localhost:5173` |
| Backend | `http://localhost:8081` |
| Swagger | `http://localhost:8081/swagger-ui/index.html` |
| Bonita | `http://localhost:8080/bonita` |
| PostgreSQL | `localhost:5433` |

---

## 3. Preparar PostgreSQL

Desde el proyecto:

```bash
docker compose down -v
docker compose up -d
docker compose ps
```

Esperar hasta que PostgreSQL aparezca como:

```text
healthy
```

El comando `down -v` elimina la base local anterior y debe utilizarse únicamente cuando se quiera iniciar la demostración desde una base limpia.

---

## 4. Preparar Bonita

Abrir Bonita Studio.

Importar/abrir el archivo `.bos` versionado en el repositorio y desplegar el proceso:

```text
RescueSync 1.1
```

Verificar que Bonita se encuentre disponible en:

```text
http://localhost:8080/bonita
```

Mantener Bonita ejecutándose durante las pruebas.

---

## 5. Levantar el backend

Abrir una terminal en:

```text
rescuesync-backend
```

Configurar PostgreSQL:

```bash
export DB_URL=jdbc:postgresql://localhost:5433/rescuesync
```

Levantar Spring Boot:

```bash
mvn spring-boot:run
```

Esperar:

```text
Started RescueSyncApplication
```

### Verificar esquema

En otra terminal:

```bash
docker exec -it rescuesync-postgres \
  psql -U rescuesync -d rescuesync -c "\dt"
```

Deben existir:

```text
emergencias
lotes
municipios
ofertas
ongs
usuarios
```

---

## 6. Cargar datos de demostración

Con el backend iniciado al menos una vez y el esquema creado, ejecutar desde `rescuesync-backend`:

```bash
docker exec -i rescuesync-postgres \
  psql -U rescuesync -d rescuesync \
  < scripts/seed-demo.sql
```

El script crea municipios, ONGs y usuarios de prueba.

Contraseña para todos los usuarios:

```text
123456
```

Usuarios principales para la demostración:

| Rol | Usuario |
|---|---|
| Operador Municipal | `operador1.laplata@test.com` |
| Centro Coordinador | `centro.coordinador@test.com` |
| Representante ONG | `representante1.cruzroja@test.com` |

---

## 7. Prueba de autenticación y RBAC con Swagger

Abrir:

```text
http://localhost:8081/swagger-ui/index.html
```

### 7.1 Operador Municipal

Ejecutar:

```text
POST /api/auth/login
```

```json
{
  "email": "operador1.laplata@test.com",
  "password": "123456"
}
```

Resultado esperado:

```text
200
OPERADOR_MUNICIPAL
```

Ejecutar:

```text
GET /api/auth/me
```

Resultado esperado: `200`.

Comprobar autorización:

```text
POST /api/emergencias → permitido
GET /api/emergencias  → 403 Forbidden
```

### 7.2 Centro Coordinador

Ejecutar primero:

```text
POST /api/auth/logout
```

Luego:

```text
POST /api/auth/login
```

```json
{
  "email": "centro.coordinador@test.com",
  "password": "123456"
}
```

Comprobar:

```text
GET /api/emergencias → 200
```

El Centro Coordinador debe poder consultar emergencias y administrar sus lotes.

### 7.3 Representante ONG

Cerrar la sesión anterior y autenticar:

```json
{
  "email": "representante1.cruzroja@test.com",
  "password": "123456"
}
```

Comprobar que una operación exclusiva del Centro Coordinador sea rechazada. Por ejemplo:

```text
POST /api/emergencias/{emergenciaId}/lotes
```

Resultado esperado:

```text
403 Forbidden
```

La ONG sí debe poder registrar ofertas sobre lotes publicados.

---

## 8. Levantar el frontend

Abrir otra terminal en:

```text
rescuesync-frontend
```

Instalar dependencias:

```bash
npm install
```

Iniciar Vite:

```bash
npm run dev
```

Abrir:

```text
http://localhost:5173
```

La primera pantalla debe ser el login de RescueSync.

---

## 9. Prueba funcional completa desde el frontend

### 9.1 Municipio — registrar emergencia

Ingresar con:

```text
operador1.laplata@test.com
123456
```

Registrar una emergencia, por ejemplo:

```text
Tipo: Inundación
Gravedad: Alto
Zona: Barrio Los Hornos, La Plata
Descripción:
Fuertes lluvias provocaron inundaciones en el barrio.
Se registran familias evacuadas y se requiere asistencia
inmediata con personal, alimentos y agua potable.
```

Resultado esperado:

```text
Emergencia registrada con estado REGISTRADA.
```

En el log del backend debe aparecer además:

```text
Emergencia X: instancia de Bonita creada, caseId=Y
```

Esto verifica el recorrido:

```text
Frontend
   ↓
Spring Boot
   ├── PostgreSQL
   └── Bonita → nueva instancia
```

### 9.2 Verificación en Bonita

Abrir el caso generado en Bonita Administrator.

Verificar:

- proceso `RescueSync 1.1`;
- `0` flow nodes fallidos;
- instancia iniciada;
- variable `emergenciaId` con el ID correspondiente;
- variable `plazoConvocatoria` con el plazo enviado por el backend.

No es necesario avanzar las User Tasks durante esta verificación.

### 9.3 Centro Coordinador — crear lotes

Cerrar sesión en RescueSync e ingresar con:

```text
centro.coordinador@test.com
123456
```

Seleccionar la emergencia creada.

Crear, por ejemplo:

**Lote 1**

```text
Tipo: Personal voluntario
Cantidad: 5
Unidad: personas
Descripción: Paramédicos para asistencia a familias evacuadas
```

**Lote 2**

```text
Tipo: Alimentos
Cantidad: 1000
Unidad: raciones
Descripción: Raciones de comida para personas evacuadas
```

Editar el segundo lote:

```text
1000 → 1200 raciones
```

Antes de publicar, ambos lotes deben estar:

```text
BORRADOR
```

La emergencia pasa a:

```text
EN_REVISION
```

### 9.4 Publicar convocatoria

Desde el mismo usuario del Centro Coordinador, seleccionar:

```text
Publicar convocatoria
```

Resultado esperado:

```text
Emergencia → CONVOCATORIA_PUBLICADA
Lotes      → PUBLICADO
```

Después de publicar, los lotes quedan en modo de solo lectura y ya no deben aparecer las acciones de edición/eliminación.

### 9.5 ONG — registrar ofertas

Cerrar sesión e ingresar con:

```text
representante1.cruzroja@test.com
123456
```

Seleccionar la convocatoria creada.

Registrar, por ejemplo:

**Oferta sobre Personal voluntario**

```text
Cantidad: 3
Observaciones: 3 paramédicos disponibles para traslado inmediato
```

**Oferta sobre Alimentos**

```text
Cantidad: 800
Observaciones: 800 raciones disponibles para entrega inmediata
```

Ambas ofertas deben registrarse correctamente.

La ONG de la oferta se obtiene de la identidad del usuario autenticado; no se envía como un valor libre desde el formulario.

---

## 10. Prueba técnica del Timer de Bonita

Esta prueba se realiza separadamente del flujo funcional anterior.

Para no esperar el plazo normal de la convocatoria, detener el backend y volver a iniciarlo con:

```bash
export DB_URL=jdbc:postgresql://localhost:5433/rescuesync
export PLAZO_CONVOCATORIA_MS=60000
mvn spring-boot:run
```

`60000 ms` equivale a un minuto.

Crear una nueva emergencia desde RescueSync.

Verificar en Bonita que la nueva instancia tenga:

```text
emergenciaId = <ID de la nueva emergencia>
plazoConvocatoria = 60000
```

Debido a que las User Tasks del frontend todavía no se sincronizan automáticamente con Bonita, avanzar manualmente en Bonita:

```text
Registrar emergencia
        ↓
Revisar y desglosar emergencia
        ↓
Publicar convocatoria
        ↓
Esperar / cargar / editar ofertas
```

Al llegar a:

```text
Esperar / cargar / editar ofertas
```

**NO ejecutar la tarea.**

Esperar más de 60 segundos.

El evento temporizador debe dispararse automáticamente y el proceso debe avanzar a:

```text
Evaluar cobertura de lotes
```

Esto verifica que el Timer Event de Bonita utiliza la variable `plazoConvocatoria` enviada por RescueSync.

---

## 11. Volver al plazo normal después de probar el Timer

La variable exportada permanece en la terminal actual.

Para volver al valor por defecto se puede cerrar esa terminal y abrir una nueva, o ejecutar:

```bash
unset PLAZO_CONVOCATORIA_MS
```

Luego iniciar nuevamente:

```bash
export DB_URL=jdbc:postgresql://localhost:5433/rescuesync
mvn spring-boot:run
```

Sin override, la configuración de la aplicación utiliza el plazo por defecto definido para la convocatoria.

---

## 12. Resultado esperado de Entrega 2

Al completar esta guía debe quedar validado:

```text
React
  ↓
Spring Boot + autenticación/RBAC
  ├── PostgreSQL
  │    ├── emergencias
  │    ├── lotes
  │    └── ofertas
  │
  └── Bonita REST API
       ├── autenticación
       ├── creación de instancia
       ├── variables iniciales
       └── Timer Event
```

La integración posterior —incluyendo la obtención consolidada de ofertas desde Bonita, comunicación con el Sistema Nacional y compromiso/liberación de recursos— corresponde a etapas posteriores del trabajo.

---

## 13. Observaciones conocidas

### Selector de lotes de ONG

Se detectó un problema exclusivamente visual en el selector de lotes: algunas opciones pueden mostrarse con texto blanco sobre fondo blanco al desplegar el control.

Los lotes se encuentran cargados y pueden seleccionarse; debe corregirse el estilo del componente.

### User Tasks de Bonita

Las acciones ejecutadas desde React todavía no completan automáticamente las User Tasks equivalentes del proceso Bonita.

Para la Entrega 2 se valida la integración inicial mediante creación de instancia, variables y temporizador. La sincronización automática de tareas se evaluará como evolución posterior.
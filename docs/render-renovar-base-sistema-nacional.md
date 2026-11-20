# Renovar la base free del Sistema Nacional en Render

La base PostgreSQL free de Render **vence a los 30 días** de creada y después se borra. Render permite **una sola base free por workspace**, así que no se puede crear la nueva antes de borrar la vieja. Este documento explica cómo renovarla sin perder datos.

## Historial

| Fecha | Base | Vence | Nota |
|---|---|---|---|
| 22/10/2026 | `dpg-dbd24mqfngtc73fu8j80-a` (`sistema_nacional_3wqh`) | 21/11/2026 | Creación inicial con el Blueprint. Borrada el 19/11/2026 |
| 19/11/2026 | `dpg-dbvhjjuk1f9s73cjdkf0-a` (`sistema_nacional_0n6i`) | **19/12/2026** | Recreada con el procedimiento de abajo. Datos restaurados: 3 ONGs y 5 usuarios; certificaciones, recursos y compromisos estaban vacíos |

**Próxima renovación: antes del 19/12/2026.** Render avisa en el dashboard unos días antes.

## Qué se necesita

- Acceso al workspace de Render donde está el Blueprint `rescuesync-sistema-nacional`.
- Docker, para usar `pg_dump`, `pg_restore` y `psql` desde la imagen `postgres:16` sin instalar nada.
- Opcional: una API key de Render (*Account Settings → API Keys*), si se quiere hacer por API en vez de por el dashboard. Revocarla al terminar.

## Procedimiento

Durante el proceso el Sistema Nacional queda caído unos 10 minutos y el conector de Bonita falla. Avisar al equipo antes.

### 1. Respaldo

En el panel de la base (`sistema-nacional-db`) > **Connect**, copiar la **External Database URL**. Desde una carpeta donde quede el respaldo:

```bash
docker run --rm -v "$PWD:/backup" postgres:16 \
  pg_dump "<External Database URL>?sslmode=require" -Fc --no-owner --no-acl -f /backup/sistema_nacional.dump
```

En Git Bash de Windows, anteponer `MSYS_NO_PATHCONV=1` para que no se rompa la ruta `/backup`.

### 2. Verificar el respaldo antes de borrar nada

Restaurarlo en un Postgres descartable y contar filas:

```bash
docker run --rm -e POSTGRES_PASSWORD=x -v "$PWD:/backup" postgres:16 sh -c '
  docker-entrypoint.sh postgres >/dev/null 2>&1 &
  until pg_isready -U postgres -q; do sleep 1; done; sleep 2
  createdb -U postgres t && pg_restore -U postgres -d t --no-owner /backup/sistema_nacional.dump
  for x in ongs usuarios certificaciones recursos compromisos; do
    echo "$x: $(psql -U postgres -d t -At -c "select count(*) from $x")"
  done'
```

Los números tienen que coincidir con los de la base en Render. Guardar el `.dump` en un lugar durable. **No subirlo al repo**: tiene los hashes de las contraseñas.

### 3. Borrar la base vieja

Dashboard: base `sistema-nacional-db` > **Settings** > **Delete Database**. No tiene vuelta atrás.

### 4. Recrearla con el Blueprint

Dashboard: **Blueprints** > `rescuesync-sistema-nacional` > **Manual Sync**. Render crea de nuevo `sistema-nacional-db` con el mismo nombre, reconecta las variables `DB_*` del servicio y lo redespliega solo.

Conviene recrearla así y no a mano: si se crea por fuera del Blueprint, el próximo sync puede intentar crear otra.

### 5. Restaurar

Con la **External Database URL** de la base nueva (cambia el host y el nombre de la base):

```bash
docker run --rm -v "$PWD:/backup" postgres:16 \
  pg_restore -d "<External Database URL nueva>?sslmode=require" --clean --if-exists --no-owner --no-acl /backup/sistema_nacional.dump
```

`--clean --if-exists` cubre el caso de que el servicio ya haya arrancado y Hibernate haya creado las tablas vacías.

### 6. Verificar

- `https://rescuesync-sistema-nacional-2qwh.onrender.com/actuator/health` responde `200` con `{"status":"UP"}`.
- `POST /api/auth/login` funciona con un usuario restaurado, por ejemplo `bonita@sistemanacional.test`, que es el que usa el conector de Bonita.
- Actualizar la tabla de historial de arriba con la base nueva y su fecha de vencimiento.

`JWT_SECRET` no cambia, porque el servicio no se recrea.

## Por API (alternativa)

Los mismos pasos se pueden hacer con la API de Render (`Authorization: Bearer <API key>`):

| Paso | Llamada |
|---|---|
| Ver la base y su vencimiento | `GET /v1/postgres` (campo `expiresAt`) |
| URL de conexión externa | `GET /v1/postgres/{id}/connection-info` (campo `externalConnectionString`) |
| Borrar | `DELETE /v1/postgres/{id}` (responde `204`) |
| Ver el deploy del servicio | `GET /v1/services/{serviceId}/deploys?limit=3` |

El **Manual Sync** del Blueprint (paso 4) se hace desde el dashboard.

## Si no se quiere repetir esto cada mes

- Pasar la base a un plan pago y cambiar `plan: free` en la sección `databases` de `rescuesync-sistema-nacional/render.yaml`.
- O usar Postgres de otro proveedor sin vencimiento (por ejemplo Neon). `application.yml` ya acepta `DB_URL`, que pisa `DB_HOST`/`DB_PORT`/`DB_NAME`; hay que poner `sslmode=require` y sacar el bloque `databases` y los `fromDatabase` de `render.yaml`.

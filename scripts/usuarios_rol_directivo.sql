-- Amplía usuarios_rol_check para aceptar el rol DIRECTIVO (E4, dashboard de indicadores).
--
-- Hace falta solo en bases PostgreSQL creadas antes de E4: ddl-auto=update no modifica
-- una restricción CHECK ya existente. Conserva todos los roles anteriores, no borra usuarios
-- y se puede volver a ejecutar. Reemplaza a usuarios_servicio_bonita.sql (incluye ese rol).
--
-- Uso, desde la raíz del repo:
--   psql -h localhost -p 5433 -U rescuesync -d rescuesync -v ON_ERROR_STOP=1 \
--     -f scripts/usuarios_rol_directivo.sql

BEGIN;

ALTER TABLE public.usuarios
    DROP CONSTRAINT IF EXISTS usuarios_rol_check;

ALTER TABLE public.usuarios
    ADD CONSTRAINT usuarios_rol_check
    CHECK (rol IN (
        'OPERADOR_MUNICIPAL',
        'CENTRO_COORDINADOR',
        'REPRESENTANTE_ONG',
        'SERVICIO_BONITA',
        'DIRECTIVO'
    ));

COMMIT;

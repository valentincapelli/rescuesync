-- E4-09: ejecutar en PostgreSQL antes de iniciar la versión corregida de RescueSync.
-- Aditivo e idempotente. Los avisos históricos conservan oferta_id NULL.
BEGIN;

ALTER TABLE public.emergencias
    ADD COLUMN IF NOT EXISTS decision_cobertura VARCHAR(20),
    ADD COLUMN IF NOT EXISTS operacion_cobertura VARCHAR(20),
    ADD COLUMN IF NOT EXISTS cobertura_task_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cobertura_sincronizacion_pendiente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cobertura_ultimo_intento TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS cierre_convocatoria_previsto TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolucion_cobertura VARCHAR(2000);

ALTER TABLE public.notificaciones
    ADD COLUMN IF NOT EXISTS oferta_id BIGINT REFERENCES public.ofertas(id),
    ADD COLUMN IF NOT EXISTS paso_bonita VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bonita_case_id BIGINT,
    ADD COLUMN IF NOT EXISTS bonita_task_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bonita_ultimo_intento TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS bonita_pendiente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS notificada_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS recibida_en TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_notificacion_oferta ON public.notificaciones(oferta_id);

COMMIT;

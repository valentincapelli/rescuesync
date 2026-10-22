-- E3-10. Ejecutar en la base de RescueSync, nunca en la del Sistema Nacional.
-- Es aditivo e idempotente. Solo completa las dos ONGs del seed de demostración;
-- para otras ONGs, cargar el CUIT verificado por id antes de evaluar sus ofertas.
BEGIN;

ALTER TABLE ongs ADD COLUMN IF NOT EXISTS cuit VARCHAR(11);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ongs_cuit ON ongs (cuit);

UPDATE ongs SET cuit = '30500000001', updated_at = CURRENT_TIMESTAMP
WHERE nombre = 'Cruz Roja Argentina' AND cuit IS NULL;
UPDATE ongs SET cuit = '30500000002', updated_at = CURRENT_TIMESTAMP
WHERE nombre = 'Caritas Argentina' AND cuit IS NULL;

COMMIT;

-- Las filas informadas requieren completar su identificación; no se inventa un CUIT.
SELECT id, nombre FROM ongs WHERE cuit IS NULL ORDER BY id;

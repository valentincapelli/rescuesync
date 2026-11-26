/*
  Datos de demostración para el dashboard de indicadores (E4).

  Requisitos:
    - Base PostgreSQL de RescueSync con el esquema creado (levantar el backend una vez).
    - scripts/seed-backend-demo.sql ya cargado (usa los municipios La Plata y Berisso y las
      ONGs Cruz Roja Argentina y Caritas Argentina). Agrega la ONG "Banco de Alimentos La Plata".

  Uso, desde la raíz del repo:
    psql -h localhost -p 5433 -U rescuesync -d rescuesync -v ON_ERROR_STOP=1 \
      -f scripts/seed-indicadores-demo.sql

  Qué carga: 13 emergencias repartidas en los últimos ~6 meses, en todos los tramos del flujo
  (6 cerradas,2 en ejecución, 1 adjudicada, 1 con cobertura parcial, 1 en convocatoria, 1 en
  revisión y 1 recién registrada), con lotes cubiertos por una sola ONG o por consorcios de 2 y 3,
  ofertas comprometidas, finalizadas y rechazadas, y **todas sus filas de historial_estados**.
  Las fechas son relativas al momento de ejecución, así siempre caen en la ventana por defecto
  del dashboard (últimos 12 meses).

  Se puede volver a ejecutar: primero borra lo cargado antes por este script (emergencias cuya
  descripción empieza con [DEMO-E4], con sus lotes, ofertas e historial). No toca otros datos.

  Las emergencias no tienen bonita_case_id: los tiempos por etapa salen de Bonita, y para verlos
  hay que crear emergencias desde la aplicación con Bonita levantado.
*/

BEGIN;

-- ------------------------------------------------------------------ limpieza de una carga anterior

DELETE FROM historial_estados
WHERE emergencia_id IN (SELECT id FROM emergencias WHERE descripcion LIKE '[DEMO-E4]%');

DELETE FROM ofertas
WHERE lote_id IN (SELECT l.id FROM lotes l JOIN emergencias e ON e.id = l.emergencia_id
                  WHERE e.descripcion LIKE '[DEMO-E4]%');

DELETE FROM lotes
WHERE emergencia_id IN (SELECT id FROM emergencias WHERE descripcion LIKE '[DEMO-E4]%');

DELETE FROM emergencias WHERE descripcion LIKE '[DEMO-E4]%';

INSERT INTO ongs (created_at, updated_at, nombre)
VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Banco de Alimentos La Plata')
ON CONFLICT (nombre) DO NOTHING;

-- ------------------------------------------------------------------ funciones auxiliares (temporales)

-- Instante "base - offset" días antes de ahora. base = días atrás en que se registró la emergencia.
CREATE FUNCTION pg_temp.demo_ts(base numeric, offset_dias numeric) RETURNS timestamptz
LANGUAGE sql AS $$ SELECT now() - ((base - offset_dias) * interval '1 day') $$;

-- Recorre el camino de estados hasta estado_final y escribe una fila de historial por paso.
CREATE FUNCTION pg_temp.demo_hist(tipo text, entidad bigint, emergencia bigint, base numeric,
                                  camino text[], offsets numeric[], estado_final text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    hasta int := array_position(camino, estado_final);
    i int;
BEGIN
    IF hasta IS NULL THEN
        RAISE EXCEPTION 'Estado % fuera del camino %', estado_final, camino;
    END IF;
    FOR i IN 1..hasta LOOP
        INSERT INTO historial_estados
            (created_at, updated_at, tipo_entidad, entidad_id, emergencia_id, estado_anterior, estado_nuevo, fecha_cambio)
        VALUES (now(), now(), tipo, entidad, emergencia,
                CASE WHEN i = 1 THEN NULL ELSE camino[i - 1] END, camino[i],
                pg_temp.demo_ts(base, offsets[i]));
    END LOOP;
END $$;

-- Emergencia + su historial. cierre = días después del registro en que se cerró (si llega a CERRADA).
CREATE FUNCTION pg_temp.demo_emergencia(tipo text, gravedad text, zona text, municipio text,
                                        base numeric, estado_final text, cierre numeric DEFAULT NULL)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    muni bigint;
    nueva bigint;
    camino text[];
    offsets numeric[];
BEGIN
    SELECT id INTO muni FROM municipios WHERE nombre = municipio;
    IF muni IS NULL THEN
        RAISE EXCEPTION 'No existe el municipio %: cargar antes scripts/seed-backend-demo.sql', municipio;
    END IF;

    IF estado_final = 'COBERTURA_PARCIAL' THEN
        camino  := ARRAY['REGISTRADA', 'EN_REVISION', 'CONVOCATORIA_PUBLICADA', 'COBERTURA_PARCIAL'];
        offsets := ARRAY[0, 0.3, 1, 3];
    ELSE
        camino  := ARRAY['REGISTRADA', 'EN_REVISION', 'CONVOCATORIA_PUBLICADA', 'COBERTURA_EVALUADA',
                         'ADJUDICADA', 'EN_EJECUCION', 'CERRADA'];
        offsets := ARRAY[0, 0.3, 1, 3, 3.5, 4, coalesce(cierre, 0)];
    END IF;

    INSERT INTO emergencias (created_at, updated_at, tipo_desastre, nivel_gravedad, zona_afectada, descripcion,
                             municipio_id, estado, fecha_cierre_convocatoria)
    VALUES (pg_temp.demo_ts(base, 0), now(), tipo, gravedad, zona,
            '[DEMO-E4] ' || initcap(lower(replace(tipo, '_', ' '))) || ' en ' || zona,
            muni, estado_final,
            CASE WHEN array_position(camino, estado_final) >= 3 THEN pg_temp.demo_ts(base, 3) END)
    RETURNING id INTO nueva;

    PERFORM pg_temp.demo_hist('EMERGENCIA', nueva, nueva, base, camino, offsets, estado_final);
    RETURN nueva;
END $$;

CREATE FUNCTION pg_temp.demo_lote(emergencia bigint, base numeric, tipo text, descripcion text,
                                  cantidad int, unidad text, estado_final text, cierre numeric DEFAULT NULL)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    nuevo bigint;
    camino text[];
    offsets numeric[];
BEGIN
    IF estado_final = 'PARCIALMENTE_CUBIERTO' THEN
        camino  := ARRAY['BORRADOR', 'PUBLICADO', 'PARCIALMENTE_CUBIERTO'];
        offsets := ARRAY[0.3, 1, 3];
    ELSE
        camino  := ARRAY['BORRADOR', 'PUBLICADO', 'CUBIERTO', 'CERRADO'];
        offsets := ARRAY[0.3, 1, 3, coalesce(cierre, 0)];
    END IF;

    INSERT INTO lotes (created_at, updated_at, emergencia_id, tipo_recurso, descripcion, cantidad_requerida,
                       unidad_medida, estado)
    VALUES (pg_temp.demo_ts(base, 0.3), now(), emergencia, tipo, descripcion, cantidad, unidad, estado_final)
    RETURNING id INTO nuevo;

    PERFORM pg_temp.demo_hist('LOTE', nuevo, emergencia, base, camino, offsets, estado_final);
    RETURN nuevo;
END $$;

CREATE FUNCTION pg_temp.demo_oferta(lote bigint, emergencia bigint, base numeric, ong text, cantidad int,
                                    estado_final text, cierre numeric DEFAULT NULL)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    ong_id bigint;
    nueva bigint;
    camino text[];
    offsets numeric[];
BEGIN
    SELECT id INTO ong_id FROM ongs WHERE nombre = ong;
    IF ong_id IS NULL THEN
        RAISE EXCEPTION 'No existe la ONG %: cargar antes scripts/seed-backend-demo.sql', ong;
    END IF;

    IF estado_final = 'RECHAZADA' THEN
        camino  := ARRAY['PENDIENTE', 'RECHAZADA'];
        offsets := ARRAY[1.5, 3.5];
    ELSE
        camino  := ARRAY['PENDIENTE', 'SELECCIONADA', 'COMPROMETIDA', 'FINALIZADA'];
        offsets := ARRAY[1.5, 3.5, 4, coalesce(cierre, 0)];
    END IF;

    INSERT INTO ofertas (created_at, updated_at, lote_id, ong_id, cantidad_ofrecida, observaciones, estado)
    VALUES (pg_temp.demo_ts(base, 1.5), now(), lote, ong_id, cantidad, NULL, estado_final)
    RETURNING id INTO nueva;

    PERFORM pg_temp.demo_hist('OFERTA', nueva, emergencia, base, camino, offsets, estado_final);
END $$;

-- ------------------------------------------------------------------ datos

DO $$
DECLARE
    e bigint;
    l bigint;
BEGIN
    -- 1. Inundación cerrada hace ~5 meses. Consorcio de 2 en alimentos; agua por una sola ONG.
    e := pg_temp.demo_emergencia('INUNDACION', 'ALTO', 'Tolosa', 'La Plata', 170, 'CERRADA', 20);
    l := pg_temp.demo_lote(e, 170, 'ALIMENTOS', 'Raciones diarias', 500, 'raciones', 'CERRADO', 20);
    PERFORM pg_temp.demo_oferta(l, e, 170, 'Cruz Roja Argentina', 300, 'FINALIZADA', 20);
    PERFORM pg_temp.demo_oferta(l, e, 170, 'Caritas Argentina', 250, 'FINALIZADA', 20);
    l := pg_temp.demo_lote(e, 170, 'AGUA_POTABLE', 'Bidones de agua', 1000, 'litros', 'CERRADO', 20);
    PERFORM pg_temp.demo_oferta(l, e, 170, 'Cruz Roja Argentina', 1000, 'FINALIZADA', 20);

    -- 2. Tormenta cerrada. Abrigo por una ONG; medicamentos por consorcio de 2.
    e := pg_temp.demo_emergencia('TORMENTA_SEVERA', 'MEDIO', 'Barrio Obrero', 'Berisso', 140, 'CERRADA', 12);
    l := pg_temp.demo_lote(e, 140, 'ABRIGO_Y_ROPA', 'Frazadas', 200, 'unidades', 'CERRADO', 12);
    PERFORM pg_temp.demo_oferta(l, e, 140, 'Caritas Argentina', 200, 'FINALIZADA', 12);
    l := pg_temp.demo_lote(e, 140, 'MEDICAMENTOS', 'Botiquines', 50, 'kits', 'CERRADO', 12);
    PERFORM pg_temp.demo_oferta(l, e, 140, 'Cruz Roja Argentina', 30, 'FINALIZADA', 12);
    PERFORM pg_temp.demo_oferta(l, e, 140, 'Banco de Alimentos La Plata', 25, 'FINALIZADA', 12);

    -- 3. Incendio crítico cerrado. Consorcio de 3 en voluntarios; una oferta rechazada.
    e := pg_temp.demo_emergencia('INCENDIO', 'CRITICO', 'Arana', 'La Plata', 110, 'CERRADA', 30);
    l := pg_temp.demo_lote(e, 110, 'PERSONAL_VOLUNTARIO', 'Brigadistas', 40, 'personas', 'CERRADO', 30);
    PERFORM pg_temp.demo_oferta(l, e, 110, 'Cruz Roja Argentina', 20, 'FINALIZADA', 30);
    PERFORM pg_temp.demo_oferta(l, e, 110, 'Caritas Argentina', 15, 'FINALIZADA', 30);
    PERFORM pg_temp.demo_oferta(l, e, 110, 'Banco de Alimentos La Plata', 10, 'FINALIZADA', 30);
    l := pg_temp.demo_lote(e, 110, 'TRANSPORTE', 'Camionetas', 5, 'vehículos', 'CERRADO', 30);
    PERFORM pg_temp.demo_oferta(l, e, 110, 'Cruz Roja Argentina', 5, 'FINALIZADA', 30);
    l := pg_temp.demo_lote(e, 110, 'ALIMENTOS', 'Viandas', 300, 'raciones', 'CERRADO', 30);
    PERFORM pg_temp.demo_oferta(l, e, 110, 'Banco de Alimentos La Plata', 300, 'FINALIZADA', 30);
    PERFORM pg_temp.demo_oferta(l, e, 110, 'Caritas Argentina', 80, 'RECHAZADA');

    -- 4. Inundación en ejecución: recursos comprometidos, todavía no finalizados.
    e := pg_temp.demo_emergencia('INUNDACION', 'ALTO', 'Villa Nueva', 'Berisso', 85, 'EN_EJECUCION');
    l := pg_temp.demo_lote(e, 85, 'AGUA_POTABLE', 'Agua potable', 2000, 'litros', 'CUBIERTO');
    PERFORM pg_temp.demo_oferta(l, e, 85, 'Cruz Roja Argentina', 1200, 'COMPROMETIDA');
    PERFORM pg_temp.demo_oferta(l, e, 85, 'Banco de Alimentos La Plata', 900, 'COMPROMETIDA');
    l := pg_temp.demo_lote(e, 85, 'REFUGIO_TEMPORAL', 'Módulos habitacionales', 3, 'módulos', 'CUBIERTO');
    PERFORM pg_temp.demo_oferta(l, e, 85, 'Caritas Argentina', 3, 'COMPROMETIDA');

    -- 5. Sequía en ejecución, cubierta por una sola ONG.
    e := pg_temp.demo_emergencia('SEQUIA', 'MEDIO', 'Abasto', 'La Plata', 70, 'EN_EJECUCION');
    l := pg_temp.demo_lote(e, 70, 'AGUA_POTABLE', 'Camiones cisterna', 5000, 'litros', 'CUBIERTO');
    PERFORM pg_temp.demo_oferta(l, e, 70, 'Banco de Alimentos La Plata', 5000, 'COMPROMETIDA');

    -- 6. Deslizamiento con cobertura parcial: ofertas todavía pendientes.
    e := pg_temp.demo_emergencia('DESLIZAMIENTO', 'ALTO', 'Los Hornos', 'La Plata', 50, 'COBERTURA_PARCIAL');
    l := pg_temp.demo_lote(e, 50, 'MAQUINARIA_PESADA', 'Retroexcavadoras', 2, 'unidades', 'PARCIALMENTE_CUBIERTO');
    PERFORM pg_temp.demo_oferta(l, e, 50, 'Cruz Roja Argentina', 1, 'PENDIENTE');
    l := pg_temp.demo_lote(e, 50, 'ALIMENTOS', 'Raciones', 200, 'raciones', 'PARCIALMENTE_CUBIERTO');
    PERFORM pg_temp.demo_oferta(l, e, 50, 'Caritas Argentina', 80, 'PENDIENTE');

    -- 7. Inundación adjudicada: consorcio de 3 seleccionado, todavía sin comprometer.
    e := pg_temp.demo_emergencia('INUNDACION', 'CRITICO', 'Puerto', 'Berisso', 35, 'ADJUDICADA');
    l := pg_temp.demo_lote(e, 35, 'ALIMENTOS', 'Raciones', 800, 'raciones', 'CUBIERTO');
    PERFORM pg_temp.demo_oferta(l, e, 35, 'Cruz Roja Argentina', 400, 'SELECCIONADA');
    PERFORM pg_temp.demo_oferta(l, e, 35, 'Caritas Argentina', 300, 'SELECCIONADA');
    PERFORM pg_temp.demo_oferta(l, e, 35, 'Banco de Alimentos La Plata', 150, 'SELECCIONADA');

    -- 8. Tormenta en convocatoria abierta.
    e := pg_temp.demo_emergencia('TORMENTA_SEVERA', 'BAJO', 'City Bell', 'La Plata', 20, 'CONVOCATORIA_PUBLICADA');
    l := pg_temp.demo_lote(e, 20, 'ABRIGO_Y_ROPA', 'Ropa de abrigo', 100, 'unidades', 'PUBLICADO');
    PERFORM pg_temp.demo_oferta(l, e, 20, 'Caritas Argentina', 60, 'PENDIENTE');

    -- 9. Incendio en revisión por el Centro Coordinador.
    e := pg_temp.demo_emergencia('INCENDIO', 'MEDIO', 'Isla Paulino', 'Berisso', 8, 'EN_REVISION');
    PERFORM pg_temp.demo_lote(e, 8, 'MEDICAMENTOS', 'Kits de quemaduras', 20, 'kits', 'BORRADOR');

    -- 10. Inundación recién registrada.
    PERFORM pg_temp.demo_emergencia('INUNDACION', 'BAJO', 'Ringuelet', 'La Plata', 2, 'REGISTRADA');

    -- 11 a 13. Cerradas adicionales, para que la serie mensual no tenga meses vacíos y los
    -- gráficos tengan más volumen.
    e := pg_temp.demo_emergencia('INCENDIO', 'ALTO', 'Melchor Romero', 'La Plata', 125, 'CERRADA', 15);
    l := pg_temp.demo_lote(e, 125, 'AGUA_POTABLE', 'Agua para brigadas', 1500, 'litros', 'CERRADO', 15);
    PERFORM pg_temp.demo_oferta(l, e, 125, 'Cruz Roja Argentina', 900, 'FINALIZADA', 15);
    PERFORM pg_temp.demo_oferta(l, e, 125, 'Banco de Alimentos La Plata', 600, 'FINALIZADA', 15);
    l := pg_temp.demo_lote(e, 125, 'ALIMENTOS', 'Viandas para brigadas', 250, 'raciones', 'CERRADO', 15);
    PERFORM pg_temp.demo_oferta(l, e, 125, 'Caritas Argentina', 250, 'FINALIZADA', 15);

    e := pg_temp.demo_emergencia('INUNDACION', 'CRITICO', 'Villa Elvira', 'La Plata', 52, 'CERRADA', 11);
    l := pg_temp.demo_lote(e, 52, 'ALIMENTOS', 'Raciones para evacuados', 700, 'raciones', 'CERRADO', 11);
    PERFORM pg_temp.demo_oferta(l, e, 52, 'Cruz Roja Argentina', 450, 'FINALIZADA', 11);
    PERFORM pg_temp.demo_oferta(l, e, 52, 'Banco de Alimentos La Plata', 250, 'FINALIZADA', 11);
    l := pg_temp.demo_lote(e, 52, 'AGUA_POTABLE', 'Agua potable envasada', 2500, 'litros', 'CERRADO', 11);
    PERFORM pg_temp.demo_oferta(l, e, 52, 'Caritas Argentina', 2500, 'FINALIZADA', 11);

    e := pg_temp.demo_emergencia('TORMENTA_SEVERA', 'ALTO', 'Los Talas', 'Berisso', 42, 'CERRADA', 9);
    l := pg_temp.demo_lote(e, 42, 'ABRIGO_Y_ROPA', 'Frazadas y colchones', 350, 'unidades', 'CERRADO', 9);
    PERFORM pg_temp.demo_oferta(l, e, 42, 'Caritas Argentina', 200, 'FINALIZADA', 9);
    PERFORM pg_temp.demo_oferta(l, e, 42, 'Cruz Roja Argentina', 150, 'FINALIZADA', 9);
    l := pg_temp.demo_lote(e, 42, 'MEDICAMENTOS', 'Kits de primeros auxilios', 40, 'kits', 'CERRADO', 9);
    PERFORM pg_temp.demo_oferta(l, e, 42, 'Cruz Roja Argentina', 40, 'FINALIZADA', 9);
END $$;

COMMIT;

-- ------------------------------------------------------------------ verificación

SELECT e.estado, count(*) AS emergencias
FROM emergencias e
WHERE e.descripcion LIKE '[DEMO-E4]%'
GROUP BY e.estado
ORDER BY e.estado;

SELECT tipo_entidad, count(*) AS filas_historial
FROM historial_estados
WHERE emergencia_id IN (SELECT id FROM emergencias WHERE descripcion LIKE '[DEMO-E4]%')
GROUP BY tipo_entidad
ORDER BY tipo_entidad;

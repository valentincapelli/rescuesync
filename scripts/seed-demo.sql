/*

-- Datos iniciales para entorno local/demo.
-- Ejecutar sobre una base de datos limpia.
-- No utilizar estas credenciales en producción.


## Script SQL para cargar usuarios, ongs y municipios iniciales

usuarios:

- operador1.laplata@test.com
- operador2.laplata@test.com
- operador1.berisso@test.com
- representante1.cruzroja@test.com
- representante2.cruzroja@test.com
- representante1.caritas@test.com
- centro.coordinador@test.com

contraseña para todos: 123456

*/
-- --------------------------------------------
-- MUNICIPIOS
-- --------------------------------------------

INSERT INTO municipios (
    created_at,
    updated_at,
    nombre
)
VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'La Plata'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Berisso');


-- --------------------------------------------
-- ONG
-- --------------------------------------------

INSERT INTO ongs (
    created_at,
    updated_at,
    nombre
)
VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Cruz Roja Argentina'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Caritas Argentina');


-- --------------------------------------------
-- USUARIOS
-- --------------------------------------------

-- Hash BCrypt proporcionado:
-- $2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba


-- 1. Operador Municipal 1 - La Plata
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'operador1.laplata@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'OPERADOR_MUNICIPAL',
    NULL,
    m.id
FROM municipios m
WHERE m.nombre = 'La Plata';


-- 2. Operador Municipal 2 - La Plata
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'operador2.laplata@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'OPERADOR_MUNICIPAL',
    NULL,
    m.id
FROM municipios m
WHERE m.nombre = 'La Plata';


-- 3. Operador Municipal - Berisso
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'operador1.berisso@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'OPERADOR_MUNICIPAL',
    NULL,
    m.id
FROM municipios m
WHERE m.nombre = 'Berisso';


-- 4. Representante 1 ONG - Cruz Roja
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'representante1.cruzroja@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'REPRESENTANTE_ONG',
    o.id,
    NULL
FROM ongs o
WHERE o.nombre = 'Cruz Roja Argentina';


-- 5. Representante 2 ONG - Cruz Roja
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'representante2.cruzroja@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'REPRESENTANTE_ONG',
    o.id,
    NULL
FROM ongs o
WHERE o.nombre = 'Cruz Roja Argentina';


-- 6. Representante ONG - Caritas
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'representante1.caritas@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'REPRESENTANTE_ONG',
    o.id,
    NULL
FROM ongs o
WHERE o.nombre = 'Caritas Argentina';


-- 7. Centro Coordinador Regional
INSERT INTO usuarios (
    created_at,
    updated_at,
    email,
    password_hash,
    rol,
    ong_id,
    municipio_id
)
VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'centro.coordinador@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'CENTRO_COORDINADOR',
    NULL,
    NULL
);


-- ============================================
-- VERIFICACIÓN
-- ============================================

SELECT
    u.id,
    u.email,
    u.rol,
    o.nombre AS ong,
    m.nombre AS municipio
FROM usuarios u
LEFT JOIN ongs o ON u.ong_id = o.id
LEFT JOIN municipios m ON u.municipio_id = m.id
ORDER BY u.id;
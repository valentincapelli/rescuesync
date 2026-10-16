/*
 * Datos iniciales para entorno local/demo.
 * Ejecutar sobre una base de datos limpia.
 * No utilizar estas credenciales en producción.
 *
 * Usuarios:
 * - admin@sistemanacional.test
 * - representante1.cruzroja@test.com
 * - representante1.caritas@test.com
 * - representante1.defensacivil@test.com
 * - bonita@sistemanacional.test
 *
 * Contraseña para todos: 123456
 */

-- ============================================
-- ONG
-- ============================================

INSERT INTO ongs (
    created_at,
    updated_at,
    cuit,
    razon_social,
    estado_habilitacion
)
VALUES
    (
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        '30500000001',
        'Cruz Roja Argentina',
        'HABILITADA'
    ),
    (
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        '30500000002',
        'Caritas Argentina',
        'HABILITADA'
    ),
    (
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        '30500000003',
        'Defensa Civil ONG Demo',
        'PENDIENTE'
    );


-- ============================================
-- USUARIOS
-- ============================================

-- 1. Administrador del Sistema Nacional
INSERT INTO usuarios (
    created_at,
    updated_at,
    username,
    password_hash,
    rol,
    ong_id
)
VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'admin@sistemanacional.test',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'ADMIN',
    NULL
);


-- 2. Representante de Cruz Roja Argentina
INSERT INTO usuarios (
    created_at,
    updated_at,
    username,
    password_hash,
    rol,
    ong_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'representante1.cruzroja@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'ONG',
    o.id
FROM ongs o
WHERE o.cuit = '30500000001';


-- 3. Representante de Caritas Argentina
INSERT INTO usuarios (
    created_at,
    updated_at,
    username,
    password_hash,
    rol,
    ong_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'representante1.caritas@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'ONG',
    o.id
FROM ongs o
WHERE o.cuit = '30500000002';


-- 4. Representante de la ONG de demostración
INSERT INTO usuarios (
    created_at,
    updated_at,
    username,
    password_hash,
    rol,
    ong_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'representante1.defensacivil@test.com',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'ONG',
    o.id
FROM ongs o
WHERE o.cuit = '30500000003';


-- 5. Usuario técnico para la integración con Bonita
INSERT INTO usuarios (
    created_at,
    updated_at,
    username,
    password_hash,
    rol,
    ong_id
)
VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'bonita@sistemanacional.test',
    '$2a$10$t/lBm30PnGdPv.yO2p7I1uOvVzyETP2koP05uGH5.kjpiE1Wjg0ba',
    'SERVICIO',
    NULL
);


-- ============================================
-- VERIFICACIÓN
-- ============================================

SELECT
    u.id,
    u.username,
    u.rol,
    o.cuit AS ong_cuit,
    o.razon_social AS ong,
    o.estado_habilitacion
FROM usuarios u
LEFT JOIN ongs o ON u.ong_id = o.id
ORDER BY u.id;
-- Database-backed bilingual error/event catalogs (replaces hardcoded enums / literal messages).
-- Applied on top of the Flyway baseline (V1) of the existing hand-maintained schema; purely additive.

CREATE TABLE catalog_statuses (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(60) NOT NULL UNIQUE,
    category    VARCHAR(30) NOT NULL CHECK (category IN ('SECURITY', 'FINANCIAL_BUSINESS', 'SYSTEM_TECHNICAL', 'VALIDATION')),
    http_status INTEGER NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE custom_error_responses (
    id              BIGSERIAL PRIMARY KEY,
    error_code      VARCHAR(60) NOT NULL UNIQUE,
    status_id       BIGINT NOT NULL REFERENCES catalog_statuses(id),
    image_url       VARCHAR(500),
    title_en        VARCHAR(200) NOT NULL,
    title_es        VARCHAR(200) NOT NULL,
    description_en  VARCHAR(1000) NOT NULL,
    description_es  VARCHAR(1000) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_custom_error_responses_status ON custom_error_responses(status_id);

CREATE TABLE event_catalogs (
    id              BIGSERIAL PRIMARY KEY,
    event_code      VARCHAR(60) NOT NULL UNIQUE,
    event_type      VARCHAR(60) NOT NULL,
    description_en  VARCHAR(1000) NOT NULL,
    description_es  VARCHAR(1000) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE system_properties (
    id          BIGSERIAL PRIMARY KEY,
    prop_key    VARCHAR(120) NOT NULL UNIQUE,
    prop_value  VARCHAR(1000) NOT NULL,
    description VARCHAR(500),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Status/category catalog
INSERT INTO catalog_statuses (code, category, http_status) VALUES
    ('VALIDATION_FAILED', 'VALIDATION', 400),
    ('TYPE_MISMATCH', 'VALIDATION', 400),
    ('RESOURCE_NOT_FOUND', 'FINANCIAL_BUSINESS', 404),
    ('USER_NOT_FOUND', 'FINANCIAL_BUSINESS', 404),
    ('ROUTE_NOT_FOUND', 'SYSTEM_TECHNICAL', 404),
    ('DUPLICATE_RESOURCE', 'FINANCIAL_BUSINESS', 409),
    ('SYSTEM_RESOURCE_CONFLICT', 'SYSTEM_TECHNICAL', 409),
    ('DATA_INTEGRITY_VIOLATION', 'SYSTEM_TECHNICAL', 409),
    ('BUSINESS_CONFLICT', 'FINANCIAL_BUSINESS', 409),
    ('INVALID_TOKEN', 'SECURITY', 401),
    ('AUTHENTICATION_FAILED', 'SECURITY', 401),
    ('RATE_LIMIT_EXCEEDED', 'SECURITY', 429),
    ('UNEXPECTED_ERROR', 'SYSTEM_TECHNICAL', 500);

-- Bilingual error response catalog
INSERT INTO custom_error_responses (error_code, status_id, image_url, title_en, title_es, description_en, description_es)
SELECT 'VALIDATION_FAILED', id, '/assets/errors/validation.svg', 'Validation Failed', 'Error de Validación',
       'One or more fields failed validation.', 'Uno o más campos no pasaron la validación.'
FROM catalog_statuses WHERE code = 'VALIDATION_FAILED'
UNION ALL
SELECT 'TYPE_MISMATCH', id, '/assets/errors/validation.svg', 'Invalid Parameter', 'Parámetro Inválido',
       'A request parameter could not be converted to the expected type.', 'Un parámetro de la solicitud no pudo ser convertido al tipo esperado.'
FROM catalog_statuses WHERE code = 'TYPE_MISMATCH'
UNION ALL
SELECT 'RESOURCE_NOT_FOUND', id, '/assets/errors/not-found.svg', 'Resource Not Found', 'Recurso No Encontrado',
       'The requested resource could not be found.', 'El recurso solicitado no pudo ser encontrado.'
FROM catalog_statuses WHERE code = 'RESOURCE_NOT_FOUND'
UNION ALL
SELECT 'USER_NOT_FOUND', id, '/assets/errors/not-found.svg', 'User Not Found', 'Usuario No Encontrado',
       'The requested user could not be found.', 'El usuario solicitado no pudo ser encontrado.'
FROM catalog_statuses WHERE code = 'USER_NOT_FOUND'
UNION ALL
SELECT 'ROUTE_NOT_FOUND', id, '/assets/errors/route-not-found.svg', 'Page Not Found', 'Página No Encontrada',
       'The requested endpoint does not exist on this server.', 'El endpoint solicitado no existe en este servidor.'
FROM catalog_statuses WHERE code = 'ROUTE_NOT_FOUND'
UNION ALL
SELECT 'DUPLICATE_RESOURCE', id, '/assets/errors/conflict.svg', 'Duplicate Resource', 'Recurso Duplicado',
       'A resource with the same unique attributes already exists.', 'Ya existe un recurso con los mismos atributos únicos.'
FROM catalog_statuses WHERE code = 'DUPLICATE_RESOURCE'
UNION ALL
SELECT 'SYSTEM_RESOURCE_CONFLICT', id, '/assets/errors/conflict.svg', 'System Resource Conflict', 'Conflicto de Recurso del Sistema',
       'The requested operation conflicts with the current system state.', 'La operación solicitada entra en conflicto con el estado actual del sistema.'
FROM catalog_statuses WHERE code = 'SYSTEM_RESOURCE_CONFLICT'
UNION ALL
SELECT 'DATA_INTEGRITY_VIOLATION', id, '/assets/errors/conflict.svg', 'Data Integrity Violation', 'Violación de Integridad de Datos',
       'The operation violates a data integrity constraint.', 'La operación viola una restricción de integridad de datos.'
FROM catalog_statuses WHERE code = 'DATA_INTEGRITY_VIOLATION'
UNION ALL
SELECT 'BUSINESS_CONFLICT', id, '/assets/errors/conflict.svg', 'Business Rule Conflict', 'Conflicto de Regla de Negocio',
       'The operation violates a business rule.', 'La operación viola una regla de negocio.'
FROM catalog_statuses WHERE code = 'BUSINESS_CONFLICT'
UNION ALL
SELECT 'INVALID_TOKEN', id, '/assets/errors/auth.svg', 'Invalid Token', 'Token Inválido',
       'The provided authentication token is invalid or expired.', 'El token de autenticación proporcionado es inválido o ha expirado.'
FROM catalog_statuses WHERE code = 'INVALID_TOKEN'
UNION ALL
SELECT 'AUTHENTICATION_FAILED', id, '/assets/errors/auth.svg', 'Authentication Failed', 'Autenticación Fallida',
       'Invalid credentials or missing authentication.', 'Credenciales inválidas o autenticación faltante.'
FROM catalog_statuses WHERE code = 'AUTHENTICATION_FAILED'
UNION ALL
SELECT 'RATE_LIMIT_EXCEEDED', id, '/assets/errors/rate-limit.svg', 'Too Many Requests', 'Demasiadas Solicitudes',
       'You have exceeded the allowed number of requests. Please try again later.', 'Ha excedido el número de solicitudes permitidas. Intente nuevamente más tarde.'
FROM catalog_statuses WHERE code = 'RATE_LIMIT_EXCEEDED'
UNION ALL
SELECT 'UNEXPECTED_ERROR', id, '/assets/errors/system.svg', 'Unexpected Error', 'Error Inesperado',
       'An unexpected error occurred while processing your request.', 'Ocurrió un error inesperado al procesar su solicitud.'
FROM catalog_statuses WHERE code = 'UNEXPECTED_ERROR';

-- System properties (initial seed)
INSERT INTO system_properties (prop_key, prop_value, description) VALUES
    ('default.locale', 'es', 'Default locale used when the Accept-Language header is absent or unsupported'),
    ('supported.locales', 'en,es', 'Comma-separated list of locales supported by the bilingual catalogs');

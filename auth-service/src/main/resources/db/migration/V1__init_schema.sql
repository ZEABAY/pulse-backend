-- Authentication Users Table (Local cache/reference for Keycloak)
-- Enum for user status
CREATE TYPE user_status AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED');

CREATE TABLE auth_users
(
    id          BIGINT PRIMARY KEY,           -- TSID
    keycloak_id VARCHAR(36) UNIQUE NOT NULL,  -- UUID from Keycloak is fixed 36 chars
    email       VARCHAR(255) UNIQUE NOT NULL,
    username    VARCHAR(100) UNIQUE NOT NULL,
    roles       VARCHAR(50)[]       NOT NULL DEFAULT '{ROLE_USER}',
    status      VARCHAR(50)         NOT NULL DEFAULT 'PENDING_VERIFICATION' CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at  TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(50)         NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(50)         NOT NULL DEFAULT 'system',
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(50)
);

-- Indexes for performance
CREATE INDEX idx_auth_users_email ON auth_users (email);

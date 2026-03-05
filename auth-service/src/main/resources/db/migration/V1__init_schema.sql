-- =============================================================
-- auth-service schema
-- =============================================================

-- -----------------------------------------------------------
-- auth_users
-- Master user record. Keycloak is the source of truth for
-- credentials; this table stores only the Keycloak reference
-- and application-level status/audit data.
-- -----------------------------------------------------------
CREATE TABLE auth_users (
    id          BIGINT       PRIMARY KEY,                          -- TSID (assigned by application)
    keycloak_id VARCHAR(36)  UNIQUE NOT NULL,                     -- Keycloak UUID (fixed 36 chars)
    email       VARCHAR(254) UNIQUE NOT NULL,                     -- RFC 5321 max
    username    VARCHAR(50)  UNIQUE NOT NULL,
    status      VARCHAR(24)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(50)  NOT NULL DEFAULT 'system',
    deleted_at  TIMESTAMPTZ,
    deleted_by  VARCHAR(50)
);

CREATE INDEX idx_auth_users_email    ON auth_users (email);
CREATE INDEX idx_auth_users_username ON auth_users (username);

-- -----------------------------------------------------------
-- roles
-- Application-level role definitions.
-- -----------------------------------------------------------
CREATE TABLE roles (
    id   BIGINT      PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,
    name VARCHAR(50) NOT NULL
);

INSERT INTO roles (id, code, name) VALUES (1, 'ROLE_USER', 'User');

-- -----------------------------------------------------------
-- auth_user_roles
-- Junction: many-to-many between auth_users and roles.
-- -----------------------------------------------------------
CREATE TABLE auth_user_roles (
    id           BIGSERIAL PRIMARY KEY,
    auth_user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role_id      BIGINT NOT NULL REFERENCES roles(id)      ON DELETE CASCADE,
    UNIQUE (auth_user_id, role_id)
);

CREATE INDEX idx_auth_user_roles_user ON auth_user_roles (auth_user_id);
CREATE INDEX idx_auth_user_roles_role ON auth_user_roles (role_id);

-- -----------------------------------------------------------
-- auth_verification_tokens
-- Short-lived tokens for email address verification.
-- -----------------------------------------------------------
CREATE TABLE auth_verification_tokens (
    id         BIGINT       PRIMARY KEY,                          -- TSID (assigned by application)
    user_id    BIGINT       NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token      VARCHAR(64)  UNIQUE NOT NULL,                      -- UUID hex (32) + buffer
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_tokens_token ON auth_verification_tokens (token);
CREATE INDEX idx_verification_tokens_user  ON auth_verification_tokens (user_id);

-- outbox_events: zeabay-outbox modülü tarafından otomatik oluşturulur (schema-outbox.sql).

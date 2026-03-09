-- Pulse schema: outbox_events + auth tables
-- Runs on first container start (empty volume). For existing DB, use Flyway or run manually.

\connect pulse

-- Outbox (V0)
CREATE TABLE IF NOT EXISTS outbox_events (
    id             BIGINT      PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    payload        TEXT         NOT NULL,
    trace_id       VARCHAR(64),
    status         VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events (status) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_events_created_at ON outbox_events (created_at);

-- Auth (V1)
CREATE TABLE IF NOT EXISTS auth_users (
    id          BIGINT       PRIMARY KEY,
    keycloak_id VARCHAR(36)  UNIQUE NOT NULL,
    email       VARCHAR(254) UNIQUE NOT NULL,
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
CREATE INDEX IF NOT EXISTS idx_auth_users_email ON auth_users (email);
CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users (username);

CREATE TABLE IF NOT EXISTS auth_verification_tokens (
    id         BIGINT       PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token      VARCHAR(64)  UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_verification_tokens_token ON auth_verification_tokens (token);
CREATE INDEX IF NOT EXISTS idx_verification_tokens_user ON auth_verification_tokens (user_id);

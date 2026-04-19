-- =============================================================
-- profile-service schema
-- =============================================================

-- -----------------------------------------------------------
-- user_profiles
-- Stores user profile information. Keycloak is the source of
-- truth for identity; this table stores display/social data.
-- username is a read replica synced via UserVerifiedEvent.
-- -----------------------------------------------------------
CREATE TABLE user_profiles (
    id                 BIGINT        PRIMARY KEY,
    keycloak_id        VARCHAR(36)   UNIQUE NOT NULL,
    username           VARCHAR(30)   UNIQUE NOT NULL,
    first_name         VARCHAR(50),
    last_name          VARCHAR(50),
    date_of_birth      DATE,
    phone_number       VARCHAR(20),
    avatar_url         VARCHAR(512),
    avatar_key         VARCHAR(256),
    bio                VARCHAR(500),
    gender             VARCHAR(24)
                           CHECK (gender IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
    location           VARCHAR(100),
    profile_completed  BOOLEAN       NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_by         VARCHAR(50)   NOT NULL DEFAULT 'system',
    deleted_at         TIMESTAMPTZ,
    deleted_by         VARCHAR(50)
);

CREATE INDEX idx_user_profiles_keycloak_id ON user_profiles (keycloak_id);
CREATE INDEX idx_user_profiles_username    ON user_profiles (username);

-- pulse DB: schema-per-service
-- Each service has its own schema. No cross-schema references.
-- Runs on first container start (pulse DB created by POSTGRES_DB).

\connect pulse

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS mail;
CREATE SCHEMA IF NOT EXISTS profile;

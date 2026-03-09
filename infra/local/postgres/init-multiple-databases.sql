-- Creates additional databases. Default DB (pulse) is created by POSTGRES_DB.
-- Keycloak uses its own database for realm/user data.
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

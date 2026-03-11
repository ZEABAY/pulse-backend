-- Creates additional databases. Default DB (pulse) is created by POSTGRES_DB.
-- pulse: all services use schemas (auth, mail, user, follow, recommendation)
-- keycloak: Keycloak realm/user data (separate product)
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

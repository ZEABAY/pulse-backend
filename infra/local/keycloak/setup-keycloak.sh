#!/usr/bin/env bash
# Keycloak kurulumunu tamamlar: Keycloak hazır olana kadar bekler, pulse-backend-client
# servis hesabına manage-users ve view-users rollerini atar.
# Docker Compose ile Keycloak başlatıldıktan sonra bir kez çalıştırın.
# Kullanım: ./setup-keycloak.sh [KEYCLOAK_URL] [ADMIN_USER] [ADMIN_PASSWORD]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/assign-service-account-roles.sh" "$@"

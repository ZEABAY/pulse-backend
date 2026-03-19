# Keycloak kurulumunu tamamlar (servis hesabına rol atar).
# Docker Compose ile Keycloak başlatıldıktan sonra bir kez çalıştırın.
# Usage: .\setup-keycloak.ps1 [-KeycloakUrl "http://localhost:9080"] [-AdminUser "admin"] [-AdminPassword "admin"]

param(
    [string]$KeycloakUrl = "http://localhost:9080",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$scriptDir\assign-service-account-roles.ps1" -KeycloakUrl $KeycloakUrl -AdminUser $AdminUser -AdminPassword $AdminPassword

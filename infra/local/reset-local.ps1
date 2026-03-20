# Tears down all local containers + volumes and brings everything back up fresh.
# Includes Keycloak realm import and service account role assignment.
# Usage: .\reset-local.ps1

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

Write-Host ">>> Stopping and removing all containers + volumes..."
docker compose -f "$ScriptDir\docker-compose.yml" down -v --remove-orphans

Write-Host ">>> Starting services..."
docker compose -f "$ScriptDir\docker-compose.yml" up -d

Write-Host ">>> Waiting for Keycloak container to become healthy..."
while ($true) {
    $status = docker inspect --format='{{.State.Health.Status}}' pulse-keycloak 2>$null
    if ($status -eq "healthy") {
        break
    }
    Write-Host "    Keycloak not healthy yet, retrying in 5s..."
    Start-Sleep -Seconds 5
}
Write-Host ">>> Keycloak is healthy."

Write-Host ">>> Running Keycloak setup (assigning service account roles)..."
& "$ScriptDir\keycloak\setup-keycloak.ps1"

Write-Host ""
Write-Host ">>> Local environment is ready!"
Write-Host "    Postgres:      localhost:5432"
Write-Host "    Redis:         localhost:6379"
Write-Host "    Kafka:         localhost:9092"
Write-Host "    Keycloak:      http://localhost:9080  (Admin UI: http://localhost:9080/admin)"
Write-Host "    Jaeger:        http://localhost:16686"
Write-Host "    Grafana:       http://localhost:3000"
Write-Host "    Kafka UI:      http://localhost:8090"
Write-Host "    Redis Insight:  http://localhost:5540"

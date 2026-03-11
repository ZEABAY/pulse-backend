#!/usr/bin/env bash
# Tears down all local containers + volumes and brings everything back up fresh.
# Includes Keycloak realm import and service account role assignment.
# Usage: ./reset-local.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ">>> Stopping and removing all containers + volumes..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" down -v --remove-orphans

echo ">>> Starting services..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

echo ">>> Waiting for Keycloak container to become healthy..."
until [ "$(docker inspect --format='{{.State.Health.Status}}' pulse-keycloak 2>/dev/null)" = "healthy" ]; do
  echo "    Keycloak not healthy yet, retrying in 5s..."
  sleep 5
done
echo ">>> Keycloak is healthy."

echo ">>> Running Keycloak setup (assigning service account roles)..."
"$SCRIPT_DIR/keycloak/setup-keycloak.sh"

echo ""
echo ">>> Local environment is ready!"
echo "    Postgres:      localhost:5432"
echo "    Redis:         localhost:6379"
echo "    Kafka:         localhost:9092"
echo "    Keycloak:      http://localhost:9080  (Admin UI: http://localhost:9080/admin)"
echo "    Jaeger:        http://localhost:16686"
echo "    Grafana:       http://localhost:3000"
echo "    Kafka UI:      http://localhost:8090"
echo "    Redis Insight: http://localhost:5540"

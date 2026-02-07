# Pulse Infra (Local Dev)

## Start
docker compose -f infra/docker-compose.yml up -d

## Stop
docker compose -f infra/docker-compose.yml down

## Reset (delete volumes)
docker compose -f infra/docker-compose.yml down -v

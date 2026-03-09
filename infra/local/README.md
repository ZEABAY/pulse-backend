# Local Development Environment

Bu dizin yerel geliştirme için Docker Compose konfigürasyonunu içerir.

**Hızlı başlangıç:** [../README.md](../README.md)

```bash
docker compose up -d
./reset-local.sh   # Sıfırdan başlat + Keycloak setup
```

**Dizin yapısı:**
- `docker-compose.yml` — Servis tanımları
- `keycloak/` — Realm import, setup script'leri
- `postgres/` — DB init script
- `jaeger/` — Tracing config

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
- `kafka/` — Topic init script
- `jaeger/` — Tracing config

**UI Araçları:**
- Kafka UI: http://localhost:8090 — Topic, consumer group, mesaj takibi
- Redis Insight: http://localhost:5540 — Redis veri tarayıcı (ilk açılışta `redis:6379` bağlantısı ekle)

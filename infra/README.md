# Pulse Infra

Yerel geliştirme ve test için Docker Compose altyapısı.

---

## Gereksinimler

- Docker & Docker Compose
- Bash script'leri için: `jq` (`brew install jq`)

---

## Hızlı Başlangıç

```bash
# Proje kökünden
cd infra/local
docker compose up -d
```

Keycloak ilk açılışta `realm-pulse-import.json` ile realm'i import eder. Ardından servis hesabı rollerini atamak için **bir kez** `reset-local.sh` çalıştırın veya manuel olarak `keycloak/setup-keycloak.sh`:

```bash
./reset-local.sh   # Tüm volume'ları siler, sıfırdan başlatır, Keycloak setup yapar
```

---

## Komutlar

| Komut | Açıklama |
|-------|----------|
| `docker compose -f infra/local/docker-compose.yml up -d` | Servisleri başlat (proje kökünden) |
| `docker compose -f infra/local/docker-compose.yml down` | Servisleri durdur |
| `docker compose -f infra/local/docker-compose.yml down -v` | Servisleri durdur + volume'ları sil |
| `./infra/local/reset-local.sh` | Sıfırdan başlat (down -v, up, Keycloak setup) |

---

## Servisler ve Portlar

| Servis | Port | Açıklama |
|--------|------|----------|
| PostgreSQL | 5432 | `pulse`, `keycloak` veritabanları |
| Redis | 6379 | Cache |
| Kafka | 9092 | Mesaj kuyruğu (KRaft) |
| Keycloak | 9080 | IAM (Admin: http://localhost:9080/admin) |
| Keycloak Health | 9090 | Management port (health/ready) |
| Jaeger | 16686 | Tracing UI |
| Grafana | 3000 | Metrikler |

---

## Ortam Değişkenleri

`infra/local/.env.example` dosyasını `.env` olarak kopyalayıp düzenleyebilirsiniz:

```bash
cp infra/local/.env.example infra/local/.env
```

---

## Keycloak

Realm `pulse`, client `pulse-backend-client` ve realm roller (`user`, `admin`) `realm-pulse-import.json` ile tanımlanır. Detaylı kurulum: [infra/local/keycloak/keycloak_kurulum.md](local/keycloak/keycloak_kurulum.md).

---

## Uyarı

Bu konfigürasyon **sadece yerel geliştirme** içindir. TEST/STAGING/PRODUCTION ortamlarında ayrı compose/helm konfigürasyonları kullanın (güçlü secret yönetimi, TLS, persistent storage, vb.).

# Pulse Backend — İlk Kurulum ve Çalıştırma

Bu doküman, Pulse backend projesini sıfırdan ayağa kaldırmak için gereken adımları açıklar. **macOS** ve **Windows** için geçerlidir.

> ⚠️ **Önemli:** Uygulama çalıştırılmadan **önce** altyapı (PostgreSQL, Kafka, Keycloak) Docker ile ayağa kaldırılmalıdır. Aksi halde auth-service bağlantı hatası verir.

---

## 1. Gereksinimler

| Yazılım | Minimum Sürüm | Kontrol |
|---------|----------------|---------|
| **Java (JDK)** | 25 | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Docker** | 24+ | `docker --version` |
| **Docker Compose** | v2+ | `docker compose version` |

### 1.1 Java 25 Kurulumu

**macOS (Homebrew):**
```bash
brew install openjdk@25
```

**Windows:**
- [Eclipse Temurin 25](https://adoptium.net/) veya [Oracle JDK 25](https://www.oracle.com/java/technologies/downloads/#java25) indirip kurun
- Kurulum sonrası `JAVA_HOME` ortam değişkenini ayarlayın (örn. `C:\Program Files\Eclipse Adoptium\jdk-25`)

**Doğrulama:** `java -version`

### 1.2 Maven Kurulumu

**macOS (Homebrew):**
```bash
brew install maven
```

**Windows:**
- [Maven indir](https://maven.apache.org/download.cgi), zip'i açın, `bin` klasörünü `PATH`'e ekleyin
- Veya Chocolatey: `choco install maven`

**Doğrulama:** `mvn -version`

### 1.3 Docker Kurulumu

**macOS:** [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install/)

**Windows:** [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install/) (WSL 2 önerilir)

**Doğrulama:** `docker compose version`

---

## 2. Proje Yapısı

```
pulse-backend/
├── auth-service/          # Auth servisi (port 8081)
├── zeabay-common/        # Ortak kütüphaneler
├── infra/
│   └── local/            # Docker Compose (PostgreSQL, Kafka, Keycloak, vb.)
└── docs/
    ├── api/              # API dokümantasyonu
    └── setup/            # Bu doküman
```

---

## 3. Adım 1: Altyapıyı Başlatma (Önce Yapılmalı)

Auth servisi PostgreSQL, Kafka ve Keycloak olmadan çalışmaz. **Uygulamayı başlatmadan önce** altyapıyı ayağa kaldırın.

### 3.1 Docker Compose ile Servisleri Başlat

**macOS / Linux / Git Bash (Windows):**
```bash
cd pulse-backend/infra/local
docker compose up -d
```

**Windows (PowerShell):**
```powershell
cd pulse-backend\infra\local
docker compose up -d
```

Bu komut şunları başlatır:

| Servis | Port | Açıklama |
|--------|------|----------|
| PostgreSQL | 5432 | Veritabanı (`pulse`, `keycloak`) |
| Redis | 6379 | Cache |
| Kafka | 9092 | Mesaj kuyruğu |
| Keycloak | 9080 | IAM (Admin: http://localhost:9080/admin) |
| Jaeger | 16686 | Tracing UI |
| Grafana | 3000 | Metrikler |

### 3.2 Veritabanı Şeması

`reset-local.sh` çalıştırıldığında (veya ilk `docker compose up` ile) PostgreSQL init script'leri otomatik olarak `outbox_events`, `auth_users` ve `auth_verification_tokens` tablolarını oluşturur. **Mevcut volume ile** çalışıyorsanız ve tablolar yoksa:

```bash
cd pulse-backend
docker exec -i pulse-postgres psql -U pulse -d pulse < infra/local/postgres/init-pulse-schema.sql
```

### 3.3 İlk Kurulum: Keycloak Realm ve Roller

Keycloak ilk açılışta realm'i import eder. Servis hesabı rollerini atamak için **bir kez** aşağıdaki adımlardan birini uygulayın.

**macOS / Linux / Git Bash (Windows):**
```bash
cd pulse-backend/infra/local
./reset-local.sh
```

**Windows (PowerShell):**

İlk kurulumda sadece servisleri başlatıp Keycloak setup yapın:
```powershell
cd pulse-backend\infra\local
docker compose up -d
# Keycloak healthy olana kadar 1–2 dakika bekleyin (docker compose ps)
.\keycloak\setup-keycloak.ps1
```

Sıfırdan başlamak isterseniz (tüm veriyi siler):
```powershell
docker compose down -v --remove-orphans
docker compose up -d
# 1–2 dakika bekleyin
.\keycloak\setup-keycloak.ps1
```

Bu işlem:
1. Servisleri başlatır (veya sıfırdan başlatır)
2. Keycloak sağlıklı olduktan sonra realm rollerini atar

### 3.4 Altyapıyı Doğrula

- **Keycloak Admin:** http://localhost:9080/admin (admin / admin)
- **PostgreSQL:** `docker exec -it pulse-postgres psql -U pulse -d pulse -c "\dt"` — tabloları listeler

---

## 4. Adım 2: Auth Servisini Derleme ve Çalıştırma

Altyapı çalışıyorken uygulamayı başlatın.

### 4.1 Derleme

**macOS / Linux / Windows (CMD veya PowerShell):**
```bash
cd pulse-backend
mvn -pl auth-service -am clean package -DskipTests
```

### 4.2 Çalıştırma Seçenekleri

**JAR ile:**
```bash
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
```

**Maven ile:**
```bash
mvn -pl auth-service spring-boot:run
```

**IDE ile:** `AuthServiceApplication` sınıfını bulup `main` metodunu çalıştırın.

---

## 5. Doğrulama

### 5.1 Servis Sağlık Kontrolü

**macOS / Linux / Git Bash:**
```bash
curl http://localhost:8081/actuator/health
```

**Windows (PowerShell):**
```powershell
Invoke-RestMethod -Uri http://localhost:8081/actuator/health
```

Beklenen: `{"status":"UP",...}`

### 5.2 Swagger UI

Tarayıcıda: http://localhost:8081/swagger-ui.html

### 5.3 Örnek API Çağrısı (Kayıt)

**macOS / Linux / Git Bash:**
```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"Pass1234!"}'
```

**Windows (PowerShell):**
```powershell
Invoke-RestMethod -Uri http://localhost:8081/api/v1/auth/register -Method POST `
  -ContentType "application/json" `
  -Body '{"username":"testuser","email":"test@example.com","password":"Pass1234!"}'
```

---

## 6. Sık Karşılaşılan Sorunlar

### 6.1 "Connection refused" — PostgreSQL / Kafka / Keycloak

**Sebep:** Altyapı henüz başlamamış veya sağlıklı değil.

**Çözüm:** Önce [Adım 3](#3-adım-1-altyapıyı-başlatma-önce-yapılmalı) tamamlandığından emin olun. Keycloak için 1–2 dakika bekleyin.

### 6.2 Keycloak "realm not found" veya 401

**Sebep:** Realm import edilmemiş veya servis hesabı rolleri atanmamış.

**Çözüm:** Git Bash veya WSL ile `./reset-local.sh` çalıştırın.

### 6.3 Java sürümü uyumsuz

**Hata:** `error: invalid target release: 25`

**Çözüm (macOS):**
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

**Çözüm (Windows):** `JAVA_HOME` ortam değişkenini JDK 25 kurulum yoluna ayarlayın.

### 6.4 Port zaten kullanımda

**Hata:** `Port 8081 is already in use`

**Çözüm:** 8081 kullanan uygulamayı kapatın veya `auth-service/src/main/resources/application.yml` içinde `server.port` değiştirin.

### 6.5 Windows: Keycloak setup

**Çözüm:** PowerShell ile `.\keycloak\setup-keycloak.ps1` çalıştırın. Git Bash kullanmak isterseniz `./reset-local.sh` da çalışır.

---

## 7. Özet: Sıra Önemli

1. **Altyapı (önce)** — `docker compose up -d` + Keycloak setup (`reset-local.sh` veya `setup-keycloak.ps1`)
2. **Uygulama (sonra)** — `mvn spring-boot:run` veya IDE

---

## 8. Sonraki Adımlar

- **API dokümantasyonu:** [docs/api/README.md](../api/README.md)
- **Auth Service detayları:** [auth-service/README.md](../../auth-service/README.md)
- **Altyapı detayları:** [infra/README.md](../../infra/README.md)

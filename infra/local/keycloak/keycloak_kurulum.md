# Keycloak Kurulum (Pulse)

Keycloak kurulumu script ile yapılır; realm, client, realm roller ve servis hesabı yetkileri otomatik tanımlanır. Manuel UI adımı yoktur. **Windows ve Linux/macOS** için script'ler vardır.

---

## Gereksinim

- **Linux / macOS:** `jq` yüklü olmalı (`brew install jq`)
- **Windows:** PowerShell 5.1 veya PowerShell 7+

---

## Tek Kaynak: `realm-pulse-import.json`

Tüm realm konfigürasyonu bu dosyada tanımlıdır. Docker Compose Keycloak başlarken `--import-realm` ile bu dosyayı import eder.

| Öğe | Değer |
|-----|-------|
| Realm | `pulse` |
| Realm roller | `user`, `admin` (RBAC için) |
| Client | `pulse-backend-client` |
| Client secret | `pulse-backend-secret` |
| Direct access grants | Açık (auth-service login) |
| Service accounts | Açık (auth-service kullanıcı oluşturma) |

---

## Adımlar

### 1. Keycloak'ı başlatın

```bash
cd infra/local
docker compose up -d keycloak
```

Keycloak açılırken `realm-pulse-import.json` import edilir.

### 2. Kurulum script'ini çalıştırın

Keycloak hazır olduktan sonra (ilk açılışta ~20–30 saniye) **bir kez**:

**Linux / macOS:**

```bash
cd infra/local/keycloak
chmod +x setup-keycloak.sh assign-service-account-roles.sh
./setup-keycloak.sh
```

**Windows:**

```powershell
cd infra\local\keycloak
.\setup-keycloak.ps1
```

İlk kez çalıştırıyorsanız:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Script:
- Keycloak hazır olana kadar bekler (`/health/ready`)
- `pulse-backend-client` servis hesabına **realm-management** rollerini atar: `manage-users`, `view-users`
- Kullanıcı profilinde `firstName` ve `lastName` zorunluluğunu kaldırır (profil verisi user-profile-service'te tutulacak)

Başarılı çıktı: *"Roles assigned successfully. auth-service can now create users in Keycloak."*

### 3. auth-service

auth-service'i çalıştırın; varsayılan secret `pulse-backend-secret` kullanılır. `/api/v1/auth/register` ve `/api/v1/auth/login` uçlarını test edebilirsiniz.

---

## Script Özeti

| Ne yapılıyor | Nasıl |
|--------------|--------|
| Realm `pulse` + roller + client | `realm-pulse-import.json` (Docker `--import-realm`) |
| Servis hesabı yetkileri | `assign-service-account-roles.sh` / `.ps1` |
| User profile (firstName/lastName opsiyonel) | Aynı script (Keycloak Admin API) |

---

## Değişiklik

- **Realm ayarları:** `realm-pulse-import.json` düzenleyin. Realm zaten varsa import tekrar uygulanmaz; yeni kurulum için `reset-local.sh` veya `docker compose down -v` ile volume silip yeniden başlatın.
- **Client secret:** Import'taki `secret` alanını değiştirirseniz auth-service'te `keycloak.credentials.secret` veya `KEYCLOAK_CLIENT_SECRET` ile aynı değeri kullanın.

---

## Farklı URL / Admin Bilgisi

- Bash: `./setup-keycloak.sh http://localhost:9080 admin your-password`
- PowerShell: `.\setup-keycloak.ps1 -KeycloakUrl "http://localhost:9080" -AdminUser "admin" -AdminPassword "your-password"`

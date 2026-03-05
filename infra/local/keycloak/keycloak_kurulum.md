# Keycloak Kurulum (Pulse) — Script ile

Keycloak kurulumu tamamen script ile yapılır; realm, client ve servis hesabı yetkileri otomatik tanımlanır. Manuel UI adımı yoktur. **Windows ve Linux/macOS** için script’ler vardır.

## Gereksinim

- **Linux / macOS:** `jq` yüklü olmalı (`brew install jq`).
- **Windows:** PowerShell 5.1 veya PowerShell 7+ (ekstra araç gerekmez).

## Adımlar

### 1. Keycloak'ı başlatın

```bash
cd infra/local
docker compose up -d keycloak
```

Keycloak açılırken `realm-pulse-import.json` import edilir ve şunlar oluşur:

- **Realm:** `pulse`  
  - Kullanıcı kaydı auth-service üzerinden (Keycloak self-registration kapalı).  
  - Giriş kullanıcı adı veya e-posta ile (auth-service login ile uyumlu).
- **Client:** `pulse-backend-client`  
  - Confidential client, secret: `pulse-backend-secret`  
  - Direct access grants (kullanıcı adı/şifre ile token).  
  - Service accounts (auth-service’in Keycloak Admin API ile kullanıcı oluşturması için).

### 2. Kurulum script'ini çalıştırın

Keycloak hazır olduktan sonra (ilk açılışta ~20–30 saniye sürebilir) **bir kez** aşağıdakilerden birini kullanın.

**Linux / macOS (Bash):**

```bash
cd infra/local/keycloak
chmod +x setup-keycloak.sh assign-service-account-roles.sh
./setup-keycloak.sh
```

**Windows (PowerShell):**

```powershell
cd infra\local\keycloak
.\setup-keycloak.ps1
```

İlk kez çalıştırıyorsanız execution policy gerekebilir:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Script:

- Keycloak’ın hazır olmasını bekler (`/health/ready`),
- `pulse-backend-client` servis hesabına **realm-management** rollerini atar: **manage-users**, **view-users** (kullanıcı oluşturma/görüntüleme).

Başarılı çıktı: *"Roles assigned successfully. auth-service can now create users in Keycloak."*

**Farklı URL veya admin bilgisi:**

- Bash: `./setup-keycloak.sh http://localhost:9080 admin your-admin-password`
- PowerShell: `.\setup-keycloak.ps1 -KeycloakUrl "http://localhost:9080" -AdminUser "admin" -AdminPassword "your-admin-password"`

### 3. auth-service

auth-service’i doğrudan çalıştırın; ekstra Keycloak ayarı gerekmez (varsayılan secret `pulse-backend-secret` kullanılır). `/api/v1/auth/register` ve `/api/v1/auth/login` uçlarını test edebilirsiniz.

---

## Script’lerin yaptıkları (özet)

| Ne yapılıyor | Nasıl |
|--------------|--------|
| Realm `pulse` | Docker Compose + `realm-pulse-import.json` (Keycloak `--import-realm`) |
| Client `pulse-backend-client` | Aynı import dosyası |
| Client secret | Import’ta `pulse-backend-secret` (auth-service varsayılanı) |
| Servis hesabı yetkileri | `assign-service-account-roles.sh` / `.ps1` (manage-users, view-users) |

Bunları UI’dan tekrarlamanız gerekmez.

---

## Değişiklik yapmak isterseniz

- **Realm ayarları** (token süresi, e-posta ile giriş vb.): `realm-pulse-import.json` dosyasını düzenleyin, Keycloak’ı yeniden başlatın. Realm zaten varsa import tekrar uygulanmaz; yeni kurulum için volume’u silip container’ı yeniden oluşturun.
- **Client secret:** Import’taki `secret` alanını değiştirirseniz auth-service’te `keycloak.credentials.secret` veya `KEYCLOAK_CLIENT_SECRET` ile aynı değeri kullanın.

# Pulse Platform - Backend API Dokümantasyonu

Bu doküman, frontend (Next.js vb.) uygulamaların Pulse Platformu ile haberleşirken referans alacağı gelişmiş API
spesifikasyonlarını barındırmaktadır.

---

## 🌍 Temel Bilgiler (Global Concepts)

### Base URL

Yerel ortam geliştirme için API uç noktamız Gateway üzerinden çalışır:

```text
http://localhost:8080/api/v1
```

### Standart Yanıt Modeli (ZeabayApiResponse)

Tüm başarılı ve hata dönen JSON istekleri belirli bir standart kapsülleyiciyle (`wrapper`) döner.

**Başarılı Yanıt Örneği:**

```json
{
  "timestamp": "2026-03-21T09:00:00.000Z",
  "success": true,
  "data": {
    ...
  },
  "error": null,
  "path": "/api/v1/some-endpoint",
  "traceId": "1a2b3c4d5e6f"
}
```

**Hatalı Yanıt Örneği (400, 401, 404, 500 vb.):**

```json
{
  "timestamp": "2026-03-21T09:01:00.000Z",
  "success": false,
  "data": null,
  "error": {
    "code": "BAD_REQUEST",
    "message": "Validation failed",
    "details": [
      "Password must be at least 6 characters"
    ]
  },
  "path": "/api/v1/some-endpoint",
  "traceId": "1a2b3c4d5e6f"
}
```

### Yetkilendirme (Authentication)

Giriş gerektiren (Protected) tüm uç noktalarda HTTP Header içerisinde `accessToken` gönderilmelidir.

```http
Authorization: Bearer <access_token_buraya>
```

---

## 🔐 1. Authentication Modülü (`auth-service`)

### 1.1 Yeni Kullanıcı Kaydı (Register)

Kullanıcı hesabı yaratır ve asenkron (Kafka + Outbox üzerinden) doğrulama e-postası (verification mail) gönderir.

- **URL:** `POST /api/v1/auth/register`
- **Auth Required:** Hayır
- **Request Body (JSON):**

```json
{
  "username": "john_doe",
  // 3-30 karakter, [a-zA-Z0-9_.]
  "email": "john@example.com",
  // geçerli e-posta formatı
  "password": "MyP@ss1234"
  // 8-100 karakter, güçlü şifre
}
```

- **Username Kuralları (Instagram tarzı):**

| Kural         | Açıklama                                     |
|---------------|----------------------------------------------|
| Karakter seti | Harf, rakam, alt çizgi (`_`) ve nokta (`.`)  |
| Min / Max     | 3 – 30 karakter                              |
| Nokta kısıtı  | Başta/sonda ve ardışık (`..`) nokta yasak    |
| Regex         | `^(?![.])[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*$` |

- **Password Kuralları:**

| Kural         | Açıklama                                                          |
|---------------|-------------------------------------------------------------------|
| Min / Max     | 8 – 100 karakter                                                  |
| Büyük harf    | En az 1 adet (`A-Z`)                                              |
| Küçük harf    | En az 1 adet (`a-z`)                                              |
| Rakam         | En az 1 adet (`0-9`)                                              |
| Özel karakter | En az 1 adet (`` !@#$%^&*()_+-=[]{}\|;:'",.<>?/`~ ``)             |
| Regex         | `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[özel karakter])...{8,100}$` |

- **Başarılı Yanıt (201 Created):**

```json
{
  "success": true,
  "data": {
    "id": "054kg95e3i5",
    // TSID formatında ID
    "username": "john_doe",
    "email": "john@example.com"
  }
}
```

### 1.2 Giriş Yapma (Login)

Geçerli kimlik bilgileriyle giriş yaparak Access ve Refresh token çifti üretir.

- **URL:** `POST /api/v1/auth/login`
- **Auth Required:** Hayır
- **Request Body (JSON):**

```json
{
  "username": "john_doe",
  // VEYA e-posta adresi gönderilebilir (max: 254)
  "password": "MyP@ss1234"
  // 8-100 karakter, güçlü şifre (register ile aynı kurallar)
}
```

- **Password Kuralları:** Register endpoint ile aynı (bkz. yukarı).
- **Başarılı Yanıt (200 OK):**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1Ni...",
    "refreshToken": "eyJhbG...",
    "expiresIn": 3600
    // saniye cinsinden kalan süre
  }
}
```

### 1.3 E-posta Doğrulama (Verify Email)

Kullanıcının e-posta adresine gönderilen 6 haneli OTP kodu ile hesabını doğrular ve aktive eder.

- **URL:** `POST /api/v1/auth/verify`
- **Auth Required:** Hayır
- **Request Body (JSON):**

```json
{
  "email": "john@example.com",
  "token": "123456"
  // Mail ile gelen 6 haneli doğrulama numarası
}
```

- **Başarılı Yanıt (200 OK):**

```json
{
  "success": true,
  "data": "Email verified successfully"
}
```

### 1.4 Token Yenileme (Refresh Token)

Süresi dolmak üzere olan `accessToken`'ı (`refreshToken` aracılığıyla) yenileyerek oturumun sürekli açık kalmasını
sağlar.

- **URL:** `POST /api/v1/auth/refresh`
- **Auth Required:** Hayır (Ancak geçerli bir refreshToken şarttır)
- **Request Body (JSON):**

```json
{
  "refreshToken": "eyJhbG..."
}
```

- **Başarılı Yanıt (200 OK):**

```json
{
  "success": true,
  "data": {
    "accessToken": "yeni_access_token_buraya",
    "refreshToken": "yeni_refresh_token_buraya",
    "expiresIn": 3600
  }
}
```

### 1.5 Çıkış Yapma (Logout)

Kullanıcının oturumunu Keycloak üzerinden sonlandırır ve token'ı kara listeye (`Redis Token Blacklist`) alır. **İstek
başarıyla sonuçlandığı an token geçersiz olur.**

- **URL:** `POST /api/v1/auth/logout`
- **Auth Required:** **Evet** (Bearer Token Header'da bulunmalıdır)
- **Request Body:** Boş
- **Başarılı Yanıt (204 No Content):**
  Response Body boştur. Sadece HTTP 204 durumu döner.

```text
HTTP/1.1 204 No Content
```

---

> **Geliştirici Notu:** Mevcut durumda Backend API yapısı henüz sadece `auth-service` ile temel atılmış durumdadır.
`profile-service`, `timeline-service` ve diğer servisler eklendikçe bu dokümantasyon güncellenecektir. Frontend
> geliştirirken bu URL base adresini ve `ZeabayApiResponse` data kapsülleyici yapısını merkezi bir `fetch` / `axios`
> interceptor aracılığıyla çözmeyi düşünebilirsiniz.

# 🔐 Auth Modülü — Endpoint Detayları

> **Servis:** `auth-service` · **Base Path:** `/api/v1/auth` · **Protokol:** HTTP/JSON (WebFlux — Reactive)

Bu doküman, `auth-service` altındaki tüm endpoint'lerin detaylı request/response spesifikasyonlarını içerir.

---

## İçindekiler

- [1.1 Yeni Kullanıcı Kaydı (Register)](#11-yeni-kullanıcı-kaydı-register)
- [1.2 Giriş Yapma (Login)](#12-giriş-yapma-login)
- [1.3 E-posta Doğrulama (Verify Email)](#13-e-posta-doğrulama-verify-email)
- [1.4 Token Yenileme (Refresh Token)](#14-token-yenileme-refresh-token)
- [1.5 Çıkış Yapma (Logout)](#15-çıkış-yapma-logout)
- [1.6 Şifremi Unuttum (Forgot Password)](#16-şifremi-unuttum-forgot-password)
- [1.7 OTP Doğrulama (Verify Reset OTP)](#17-otp-doğrulama-verify-reset-otp)
- [1.8 Şifre Sıfırlama (Reset Password)](#18-şifre-sıfırlama-reset-password)

---

## 1.1 Yeni Kullanıcı Kaydı (Register)

Sisteme yeni bir kullanıcı hesabı oluşturur. Başarılı kayıt sonrası Kafka Outbox mekanizması üzerinden
asenkron olarak kullanıcının e-posta adresine 6 haneli doğrulama kodu gönderilir.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/register` |
| **Auth** | Gerekli değil |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "MyP@ss1234"
}
```

#### Alan Detayları

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `username` | `string` | ✓ | 3–30 karakter. Harf (Türkçe dahil), rakam, alt çizgi (`_`) ve nokta (`.`). Başta/sonda ve ardışık (`..`) nokta yasak. |
| `email` | `string` | ✓ | Geçerli e-posta formatı (`@Email`) |
| `password` | `string` | ✓ | 8–100 karakter. En az 1 büyük harf, 1 küçük harf, 1 rakam, 1 özel karakter. Türkçe karakterler (`ÇĞİÖŞÜçğıöşü`) geçerli. |

<details>
<summary>📏 Username Kuralları (Detay)</summary>

Instagram benzeri username formatı uygulanır:

| Kural | Açıklama |
|-------|----------|
| Karakter seti | Harf (A-Z, a-z, Türkçe), rakam (0-9), alt çizgi (`_`), nokta (`.`) |
| Min / Max | 3 – 30 karakter |
| Nokta kısıtı | Başta/sonda ve ardışık (`..`) nokta yasaktır |
| Regex | `^(?![.])[a-zA-Z0-9ÇĞİÖŞÜçğıöşü_]+(?:\.[a-zA-Z0-9ÇĞİÖŞÜçğıöşü_]+)*$` |

</details>

<details>
<summary>🔒 Password Kuralları (Detay)</summary>

| Kural | Açıklama |
|-------|----------|
| Min / Max | 8 – 100 karakter |
| Büyük harf | En az 1 adet (`A-Z` veya `ÇĞİÖŞÜ`) |
| Küçük harf | En az 1 adet (`a-z` veya `çğıöşü`) |
| Rakam | En az 1 adet (`0-9`) |
| Özel karakter | En az 1 adet (`` !@#$%^&*()_+-=[]{}|;:'",.<>?/`~ ``) |
| Regex | `^(?=.*[a-zçğıöşü])(?=.*[A-ZÇĞİÖŞÜ])(?=.*\d)(?=.*[özel])...{8,100}$` |

> **Not:** Tüm password validation'ları (Register, Login, Reset Password) aynı kuralları kullanır.

</details>

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "User registered successfully",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Gönderilen alanlar validation kurallarına uymadı |
| `409` | `USER_ALREADY_EXISTS` | Bu e-posta veya kullanıcı adı zaten sistemde kayıtlı |

---

## 1.2 Giriş Yapma (Login)

Geçerli kimlik bilgileriyle Keycloak üzerinden giriş yaparak JWT Access ve Refresh token çifti döner.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/login` |
| **Auth** | Gerekli değil |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "usernameOrEmail": "john_doe",
  "password": "MyP@ss1234"
}
```

#### Alan Detayları

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `usernameOrEmail` | `string` | ✓ | Kullanıcı adı **veya** e-posta adresi gönderilebilir. Maks 254 karakter. |
| `password` | `string` | ✓ | 8–100 karakter. Register ile aynı şifre kuralları geçerlidir. |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1Ni...",
    "refreshToken": "eyJhbG...",
    "expiresIn": 3600,
    "refreshExpiresIn": 2592000
  },
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

| Response Alanı | Tip | Açıklama |
|----------------|-----|----------|
| `accessToken` | `string` | JWT erişim token'ı. Korumalı endpoint'lere `Authorization: Bearer` header'ıyla gönderilir. |
| `refreshToken` | `string` | Yenileme token'ı. `accessToken` süresi dolduğunda `/refresh` endpoint'ine gönderilir. |
| `expiresIn` | `integer` | `accessToken`'ın kalan ömrü (saniye cinsinden). |
| `refreshExpiresIn` | `integer` | `refreshToken`'ın kalan ömrü (saniye cinsinden). |

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Form alanları kurallara uymuyor |
| `401` | `INVALID_CREDENTIALS` | Kullanıcı adı/e-posta veya şifre hatalı |
| `403` | `EMAIL_NOT_VERIFIED` | Hesap henüz e-posta ile doğrulanmamış |
| `403` | `ACCOUNT_DISABLED` | Hesap pasif veya engellenmiş durumda |

---

## 1.3 E-posta Doğrulama (Verify Email)

Kullanıcının e-posta adresine gönderilen 6 haneli OTP kodunu doğrulayarak hesabı aktif hale getirir.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/verify` |
| **Auth** | Gerekli değil |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "email": "john@example.com",
  "token": "123456"
}
```

#### Alan Detayları

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `email` | `string` | ✓ | Geçerli e-posta formatı |
| `token` | `string` | ✓ | Tam olarak 6 haneli rakamsal kod (`\d{6}`) |

> **Güvenlik Notu:** `email` ve `token` birlikte gönderilmesinin sebebi, 6 haneli OTP kodunun global
> olarak benzersiz olmamasıdır — kodun doğru kullanıcıya ait olduğu bu şekilde teyit edilir.

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "Email verified successfully",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Token formatı hatalı (6 hane değil vb.) |
| `400` | `BAD_REQUEST` | Doğrulama kodu geçersiz, süresi dolmuş veya daha önce kullanılmış |

---

## 1.4 Token Yenileme (Refresh Token)

Süresi dolmak üzere olan `accessToken`'ı yenileyerek oturumun kesintisiz devam etmesini sağlar.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/refresh` |
| **Auth** | Gerekli değil (ancak geçerli `refreshToken` zorunlu) |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "refreshToken": "eyJhbG..."
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `refreshToken` | `string` | ✓ | Login veya önceki refresh'ten alınan geçerli yenileme token'ı |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": {
    "accessToken": "yeni_access_token",
    "refreshToken": "yeni_refresh_token",
    "expiresIn": 3600,
    "refreshExpiresIn": 2592000
  },
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `401` | `INVALID_TOKEN` | Refresh token geçersiz veya bozuk |
| `401` | `TOKEN_EXPIRED` | Refresh token süresi dolmuş |

---

## 1.5 Çıkış Yapma (Logout)

Kullanıcının oturumunu Keycloak üzerinden sonlandırır ve mevcut `accessToken`'ı Redis Blacklist'e alarak
geçersiz kılar. **İstek başarıyla tamamlandığında token anında geçersiz olur.**

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/logout` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Request Body** | Boş |
| **Başarılı Durum Kodu** | `204 No Content` |

### Başarılı Yanıt (`204 No Content`)

Response body boştur. Sadece HTTP `204` durum kodu döner.

```http
HTTP/1.1 204 No Content
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz (oturum açılmamış) |

---

## 1.6 Şifremi Unuttum (Forgot Password)

Şifre sıfırlama akışını başlatır. Kullanıcının kayıtlı e-posta adresine 6 haneli OTP kodu gönderilir.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/forgot-password` |
| **Auth** | Gerekli değil |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "email": "john.doe@example.com"
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `email` | `string` | ✓ | Geçerli e-posta formatı, maks 254 karakter |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "Password reset request processed",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

> ⚠️ **Güvenlik (Enumeration Attack Koruması):** E-posta adresi sistemde kayıtlı olmasa bile
> her zaman `200 OK` yanıtı dönülür. İstemci bu yanıtı alınca koşulsuz olarak
> "Kodu Girin" ekranına geçmelidir.

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Geçersiz e-posta formatı |

---

## 1.7 OTP Doğrulama (Verify Reset OTP)

Şifre sıfırlama akışının 2. adımı. Gönderilen 6 haneli OTP kodunu, yeni şifre belirleme adımına
geçmeden önce **ara doğrulama** olarak teyit eder.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/verify-reset-otp` |
| **Auth** | Gerekli değil |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "email": "john.doe@example.com",
  "otp": "123456"
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `email` | `string` | ✓ | Geçerli e-posta formatı, maks 254 karakter |
| `otp` | `string` | ✓ | Tam olarak 6 haneli rakamsal kod (`^\d{6}$`) |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "OTP is valid",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | OTP formatı hatalı (6 haneli rakam değil) |
| `400` | `BAD_REQUEST` | Doğrulama kodu geçersiz, süresi dolmuş veya daha önce kullanılmış |

---

## 1.8 Şifre Sıfırlama (Reset Password)

Şifre sıfırlama akışının 3. ve son adımı. Doğrulanmış e-posta ve OTP bilgilerini kullanarak
Keycloak üzerinde yeni şifreyi ayarlar ve kullanılmış OTP'yi geçersiz kılar (tek kullanımlık).

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/auth/reset-password` |
| **Auth** | Gerekli değil |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "email": "john.doe@example.com",
  "otp": "123456",
  "password": "NewSecret123!"
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `email` | `string` | ✓ | Geçerli e-posta formatı, maks 254 karakter |
| `otp` | `string` | ✓ | Tam olarak 6 haneli rakamsal kod (`^\d{6}$`) |
| `password` | `string` | ✓ | 8–100 karakter, Register ile aynı şifre kuralları (bkz. [1.1](#11-yeni-kullanıcı-kaydı-register)) |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "Password reset successfully",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Şifre karmaşıklık kurallarına uymuyor veya OTP formatı hatalı |
| `400` | `BAD_REQUEST` | Doğrulama kodu geçersiz, süresi dolmuş veya daha önce kullanılmış |


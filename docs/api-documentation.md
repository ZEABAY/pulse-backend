# Pulse Platform - Backend API Dokümantasyonu

Bu doküman, frontend (Next.js vb.) uygulamaların Pulse Platformu ile haberleşirken referans alacağı gelişmiş API
spesifikasyonlarını barındırmaktadır.

---

## 🚀 Auth Modülü Senaryoları (User Journeys)

Uygulamanın `auth-service` ile olan genel etkileşim akışları (senaryoları) şunlardır:

1. **Yeni Kayıt (Register) ve Aktivasyon Akışı:**
   - Frontend `POST /api/v1/auth/register` ile kullanıcı oluşturur.
   - Backend asenkron olarak (Kafka Outbox üzerinden) kayıtlı adrese 6 haneli doğrulama kodu gönderir.
   - Kullanıcı e-postadaki kodu ve e-postasını frontend formuna girdiğinde `POST /api/v1/auth/verify` çağrılır ve hesap aktifleşir.

2. **Giriş (Login) ve Oturum Yenileme (Refresh) Akışı:**
   - Onaylı kullanıcı `POST /api/v1/auth/login` atar, backend `accessToken` ve `refreshToken` döner.
   - Tüm özel kaynaklara (kapalı endpointlere) atılan isteklerde `accessToken` (`Authorization: Bearer <token>`) başlığıyla yollanır.
   - Süre dolduğunda alınan `401 Unauthorized` sonrası `POST /api/v1/auth/refresh` endpoint'ine sessizce (arka planda) `refreshToken` gönderilerek oturum kesintisiz devam ettirilir.

3. **Şifremi Unuttum (Forgot Password) 3 Adımlı Akışı:**
   - **Adım 1:** `POST /api/v1/auth/forgot-password`'e e-posta atılır, backend e-postaya 6 haneli OTP kodu atar.
   - **Adım 2 (Ara Doğrulama):** Frontend "Kodu Girin" ekranına geçer; kullanıcı e-posta ve OTP'yi girer -> `POST /api/v1/auth/verify-reset-otp` üzerinden teyit edilir.
   - **Adım 3:** Başarılı teyit sonrası "Yeni Şifre" ekranı görünür; kullanıcı e-posta, OTP ve yeni şifresini gönderir -> `POST /api/v1/auth/reset-password` ile işlem tamamlanır.

4. **Çıkış (Logout):**
   - `POST /api/v1/auth/logout` çağrılır. Backend, Keycloak oturumunu sonlandırır ve çalınma riskine karşı mevcut `accessToken`'ı süresi bitene dek geçerli kalmaması için Redis Blacklist'e (Kara Liste) alarak engeller.

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
  "data": {...},
  "error": null,
  "path": "/api/v1/some-endpoint",
  "traceId": "1a2b3c4d5e6f"
}
```

**Hatalı Yanıt Örneği (Key-Only Errors - 400, 401, 404, 500 vb.):**

```json
{
  "timestamp": "2026-03-21T09:01:00.000Z",
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "messageKey": "error.validation_failed",
    "details": [
      {
        "field": "password",
        "messageKey": "validation.password.Size"
      }
    ]
  },
  "path": "/api/v1/some-endpoint",
  "traceId": "1a2b3c4d5e6f"
}
```

> **Not:** Backend artık hardcoded hata metinleri döndürmemektedir. Geliştirilen Key-Only yapısı sayesinde i18n çevirileri frontend katmanında `messageKey` referans alınarak yapılmalıdır.

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

### 1.6 Şifremi Unuttum (Forgot Password İsteği)

Kullanıcının kayıtlı e-posta adresine 6 haneli OTP şifre sıfırlama kodu gönderilmesini tetikler.

- **URL:** `POST /api/v1/auth/forgot-password`
- **Auth Required:** Hayır
- **Request Body (JSON):**
```json
{
  "email": "john.doe@example.com"
}
```
- **Başarılı Yanıt (200 OK):**
> Güvenlik (Enumeration Attack) gereği e-posta adresi sistemde bulunmasa bile her zaman bu yanıt dönülür!
```json
{
  "success": true,
  "data": "Password reset request processed"
}
```
- **Olası Hatalı Yanıtlar:**
  - `400 Bad Request`: Geçersiz e-posta formatı (`VALIDATION_ERROR`).

### 1.7 Kodu Doğrulama (Verify Password Reset OTP)

Gönderilen 6 haneli OTP kodunu, şifre belirleme adımına geçmeden önce (ara adımda) doğrular.

- **URL:** `POST /api/v1/auth/verify-reset-otp`
- **Auth Required:** Hayır
- **Request Body (JSON):**
```json
{
  "email": "john.doe@example.com",
  "otp": "123456"
}
```
- **Başarılı Yanıt (200 OK):**
```json
{
  "success": true,
  "data": "OTP is valid"
}
```
- **Olası Hatalı Yanıtlar:**
  - `400 Bad Request`: OTP süresi dolmuş, daha önce kullanılmış veya 6 hane formatı ihlali.
  - `404 Not Found`: Belirtilen e-postaya ait kullanıcı veya ilgili token bulunamadı (`error.user_not_found`, `error.not_found`).

### 1.8 Şifre Sıfırlama (Reset Password)

Doğrulanmış e-posta ve OTP verilerini kullanarak kullanıcı için Keycloak üzerinde yeni şifreyi ayarlar ve OTP'yi sistemde yakarak tamamen geçersiz kılar.

- **URL:** `POST /api/v1/auth/reset-password`
- **Auth Required:** Hayır
- **Request Body (JSON):**
```json
{
  "email": "john.doe@example.com",
  "otp": "123456",
  "password": "NewSecret123!"
}
```
- **Başarılı Yanıt (200 OK):**
```json
{
  "success": true,
  "data": "Password reset successfully"
}
```
- **Olası Hatalı Yanıtlar:** 1.7'deki hatalara ek olarak geçersiz parola formatı hatası dönebilir (`VALIDATION_ERROR`).

---

## 🛑 Sistem Hata Kodları (Error Codes)

Tüm backend API hataları standartlaştırılarak `ErrorCode` enum değerleri üzerinden döndürülür. İlgili hatanın frontend tarafındaki i18n çeviri anahtarı her zaman `error.<error_code_kucuk_harf>` şeklinde oluşturulur.

Aşağıdaki tabloda tüm hata kodları, döndürdükleri HTTP durum kodları ve çeviri (`messageKey`) değerleri detaylandırılmıştır:

| Error Code | HTTP Status | Message Key | Açıklama |
|------------|-------------|-------------|----------|
| `BAD_REQUEST` | 400 | `error.bad_request` | Geçersiz veya hatalı yapılandırılmış API isteği. |
| `VALIDATION_ERROR` | 400 | `error.validation_error` | Form veya veri doğrulama hatası (detaylar `details` dizisi içindedir). |
| `BUSINESS_ERROR` | 400 | `error.business_error` | İş kuralı ihlali (Örn: geçersiz mantıksal durum). |
| `INVALID_INPUT` | 400 | `error.invalid_input` | Geçersiz veri girişi. |
| `UNAUTHORIZED` | 401 | `error.unauthorized` | Kaynağa erişmek için geçerli bir kimlik doğrulaması gerekiyor. |
| `TOKEN_EXPIRED` | 401 | `error.token_expired` | Gönderilen yetki token'ının süresi dolmuş. |
| `INVALID_TOKEN` | 401 | `error.invalid_token` | Gönderilen token geçersiz, bozuk veya manipüle edilmiş. |
| `INVALID_CREDENTIALS` | 401 | `error.invalid_credentials` | E-posta, kullanıcı adı veya parola hatalı. |
| `FORBIDDEN` | 403 | `error.forbidden` | İlgili kaynağa erişim yetkiniz/rolünüz yok. |
| `EMAIL_NOT_VERIFIED` | 403 | `error.email_not_verified` | E-posta doğrulanmadığı için hesaba erişim reddedildi. |
| `ACCOUNT_DISABLED` | 403 | `error.account_disabled` | Kullanıcı hesabı pasif veya engellenmiş durumda. |
| `NOT_FOUND` | 404 | `error.not_found` | İstenen kaynak, URL veya ID sistemde bulunamadı. |
| `USER_NOT_FOUND` | 404 | `error.user_not_found` | İşlem yapılmak istenen kullanıcı sistemde mevcut değil. |
| `CONFLICT` | 409 | `error.conflict` | Kaynak çakışması yaşandı. |
| `USER_ALREADY_EXISTS` | 409 | `error.user_already_exists` | Bu e-posta veya kullanıcı adı zaten sisteme kayıtlı. |
| `DUPLICATE_ENTRY` | 409 | `error.duplicate_entry` | Mükerrer (tekrar eden) kayıt işlemi girilmeye çalışıldı. |
| `RATE_LIMIT_EXCEEDED` | 429 | `error.rate_limit_exceeded` | Çok kısa sürede çok fazla API isteği gönderildi (Rate Limit). |
| `INTERNAL_ERROR` | 500 | `error.internal_error` | Sunucu kaynaklı beklenmeyen bir teknik hata meydana geldi. |
| `SERVICE_UNAVAILABLE` | 503 | `error.service_unavailable` | Erişmek istediğiniz servis şu anda geçici olarak hizmet dışı. |
| `GATEWAY_TIMEOUT` | 504 | `error.gateway_timeout` | Arka plandaki mikroservislerden zaman aşımı nedeniyle yanıt alınamadı. |

### 📋 Form Doğrulama (Validation) Hatalarının Detayları

Form doğrulamasından geçemeyen isteklerde ana sistem hata kodu `VALIDATION_ERROR` döner ancak hangi alanın neden geçersiz olduğu `details` listesi içinde belirtilir. Backend, Jakarta Validation standartlarını otomatik olarak spesifik `messageKey` değerlerine dönüştürür. 

**Format şu şekildedir:** `validation.<alan_adi>.<KuralIsmi>`

**Sık Karşılaşılan Validation Hata Anahtarları:**

| Hatalı Alan (Field) | İhlal Edilen Kural | Üretilen `messageKey` | Ne Anlama Geliyor? |
|---------------------|--------------------|-----------------------|--------------------|
| `username` | `@Size` | `validation.username.Size` | Kullanıcı adı belirlenen karakter sınırlarına uymuyor (Örn: 3'ten kısa). |
| `username` | `@NotBlank` | `validation.username.NotBlank` | Kullanıcı adı alanı boş bırakılamaz. |
| `username` | `@Pattern` | `validation.username.Pattern` | Kullanıcı adında desteklenmeyen karakter var veya format hatalı. |
| `password` | `@Size` | `validation.password.Size` | Parola uzunluğu kurallara uymuyor (Örn: 8'den kısa). |
| `password` | `@Pattern` | `validation.password.Pattern` | Parola karmaşıklık kurallarına (büyük harf, küçük harf, rakam, sembol) uymuyor. |
| `email` | `@Email` | `validation.email.Email` | Geçersiz bir e-posta formatı. |
| `email` | `@NotBlank` | `validation.email.NotBlank` | E-posta alanı boş bırakılamaz. |
| `token` | `@Size` | `validation.token.Size` | (Örn: OTP) Doğrulama kodu beklenen uzunlukta değil. |
| `token` | `@Pattern` | `validation.token.Pattern` | Kod tamamen rakamlardan oluşmuyor (veya formatı bozuk). |

> Frontend katmanında formun üzerinde (input altında) göstermeniz gereken kırmızı hata loglarını, bu gelen `messageKey`'leri alıp `tr.json` / `en.json` dosyalarınızdan okuyarak dinamik bir şekilde gösterebilirsiniz.

---

> **Geliştirici Notu:** Mevcut durumda Backend API yapısı henüz sadece `auth-service` ile temel atılmış durumdadır.
`profile-service`, `timeline-service` ve diğer servisler eklendikçe bu dokümantasyon güncellenecektir. Frontend
geliştirirken bu URL base adresini ve `ZeabayApiResponse` data kapsülleyici yapısını merkezi bir `fetch` / `axios`
interceptor aracılığıyla çözmeyi düşünebilirsiniz.

# 🛑 Hata Kodları ve Doğrulama Kuralları

> **Kaynak Kod:** `zeabay-common/zeabay-core` → `ErrorCode.java`  
> **Exception Handler:** `zeabay-common/zeabay-webflux` → `ZeabayGlobalExceptionHandler.java`

Bu doküman, Pulse Platform backend'inin döndürdüğü tüm standart hata kodlarını ve form doğrulama
(validation) kurallarını detaylandırır.

---

## Sistem Hata Kodları (`ErrorCode` Enum)

Tüm backend API hataları, `ErrorCode` enum değerleri üzerinden standartlaştırılmıştır.
İstemci tarafındaki i18n çeviri anahtarı şu formül ile üretilir:

```
messageKey = "error." + ErrorCode.name().toLowerCase()
```

> **Örnek:** `PROFILE_NOT_FOUND` → `error.profile_not_found`

### Tam Hata Kodu Tablosu

#### 400 Bad Request

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `BAD_REQUEST` | `error.bad_request` | Geçersiz veya hatalı yapılandırılmış API isteği |
| `VALIDATION_ERROR` | `error.validation`¹ | Form veya veri doğrulama hatası. Detaylar `validationErrors` dizisindedir |
| `BUSINESS_ERROR` | `error.business_error` | İş kuralı ihlali (geçersiz mantıksal durum) |
| `INVALID_INPUT` | `error.invalid_input` | Geçersiz veri girişi |
| `EMAIL_MISMATCH` | `error.email_mismatch` | İstekteki e-posta, kayıtlı e-posta ile uyuşmuyor |

> ¹ **Dikkat:** `VALIDATION_ERROR` için messageKey `error.validation_error` **değil**, `error.validation` olarak dönmektedir.
> Bu, `ZeabayGlobalExceptionHandler`'daki özel haritalama sebebiyledir.

#### 401 Unauthorized

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `UNAUTHORIZED` | `error.unauthorized` | Kaynağa erişmek için geçerli kimlik doğrulama gerekiyor |
| `TOKEN_EXPIRED` | `error.token_expired` | Gönderilen yetki/doğrulama/sıfırlama token'ının süresi dolmuş |
| `INVALID_TOKEN` | `error.invalid_token` | Gönderilen token bozuk, manipüle edilmiş veya tanınmıyor |
| `INVALID_CREDENTIALS` | `error.invalid_credentials` | Kullanıcı adı, e-posta veya parola hatalı |
| `TOKEN_ALREADY_USED` | `error.token_already_used` | Doğrulama veya sıfırlama token'ı daha önce kullanılmış |

#### 403 Forbidden

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `FORBIDDEN` | `error.forbidden` | İlgili kaynağa erişim yetkiniz / rolünüz yok |
| `EMAIL_NOT_VERIFIED` | `error.email_not_verified` | E-posta doğrulanmadığı için hesaba erişim reddedildi |
| `ACCOUNT_DISABLED` | `error.account_disabled` | Kullanıcı hesabı pasif veya engellenmiş durumda |

#### 404 Not Found

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `NOT_FOUND` | `error.not_found` | İstenen kaynak, URL veya ID sistemde bulunamadı |
| `USER_NOT_FOUND` | `error.user_not_found` | İşlem yapılmak istenen kullanıcı sistemde mevcut değil |
| `PROFILE_NOT_FOUND` | `error.profile_not_found` | Kullanıcıya ait profil kaydı bulunamadı |
| `PROFILE_NOT_COMPLETED` | `error.profile_not_completed` | Profil kaydı mevcut ancak henüz tamamlanmamış |
| `VERIFICATION_TOKEN_NOT_FOUND` | `error.verification_token_not_found` | E-posta doğrulama token'ı bulunamadı |
| `RESET_TOKEN_NOT_FOUND` | `error.reset_token_not_found` | Şifre sıfırlama token'ı bulunamadı |

#### 409 Conflict

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `CONFLICT` | `error.conflict` | Kaynak çakışması yaşandı |
| `USER_ALREADY_EXISTS` | `error.user_already_exists` | Bu e-posta veya kullanıcı adı zaten sisteme kayıtlı |
| `DUPLICATE_ENTRY` | `error.duplicate_entry` | Mükerrer (tekrar eden) kayıt girilmeye çalışıldı |

#### 422 Unprocessable Entity

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `AVATAR_INVALID_OWNERSHIP` | `error.avatar_invalid_ownership` | Avatar anahtarı kullanıcıya ait değil (IDOR koruması) |
| `AVATAR_UNSUPPORTED_TYPE` | `error.avatar_unsupported_type` | Desteklenmeyen avatar dosya türü yüklendi |
| `AVATAR_FILE_TOO_LARGE` | `error.avatar_file_too_large` | Avatar dosyası izin verilen maksimum boyutu aşıyor |

#### 429 Too Many Requests

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `RATE_LIMIT_EXCEEDED` | `error.rate_limit_exceeded` | Çok kısa sürede çok fazla API isteği gönderildi |

#### 500 Internal Server Error

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `INTERNAL_ERROR` | `error.internal_error` | Sunucu kaynaklı beklenmeyen bir teknik hata meydana geldi |

#### 502 Bad Gateway

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `IDENTITY_PROVIDER_ERROR` | `error.identity_provider_error` | Keycloak gibi upstream kimlik sağlayıcısından hata alındı |

#### 503 Service Unavailable

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `SERVICE_UNAVAILABLE` | `error.service_unavailable` | Erişilmek istenen servis geçici olarak hizmet dışı |

#### 504 Gateway Timeout

| Error Code | Message Key | Açıklama |
|------------|-------------|----------|
| `GATEWAY_TIMEOUT` | `error.gateway_timeout` | Arka plandaki mikroservislerden zaman aşımı nedeniyle yanıt alınamadı |

---

## Exception Handler Davranışları

`ZeabayGlobalExceptionHandler` sınıfı, farklı exception tiplerini aşağıdaki gibi işler:

| Exception Tipi | Üretilen ErrorCode | messageKey Oluşturma |
|----------------|--------------------|--------------------|
| `WebExchangeBindException` | `VALIDATION_ERROR` | Sabit: `error.validation` |
| `BusinessException` | `ex.getErrorCode()` | Dinamik: `error.` + `errorCode.name().toLowerCase()` |
| `ResponseStatusException` | HTTP durum adı | Dinamik: `error.` + `httpStatus.name().toLowerCase()` |
| `Exception` (catch-all) | `INTERNAL_ERROR` | Sabit: `error.internal_error` |

---

## Servis Bazlı Hata Kodu Kullanım Haritası

### auth-service

| Senaryo | ErrorCode | Örnek Mesaj |
|---------|-----------|-------------|
| E-posta zaten kayıtlı | `USER_ALREADY_EXISTS` | Email is already registered |
| Yanlış kimlik bilgileri | `UNAUTHORIZED` / `INVALID_CREDENTIALS` | Invalid credentials |
| E-posta doğrulanmamış | `EMAIL_NOT_VERIFIED` | Email not verified |
| Doğrulama token'ı bulunamadı | `VERIFICATION_TOKEN_NOT_FOUND` | Verification token not found for email=... |
| Doğrulama token süresi dolmuş | `TOKEN_EXPIRED` | Email verification token has expired |
| Doğrulama token kullanılmış | `TOKEN_ALREADY_USED` | Email verification token has already been used |
| E-posta eşleşmiyor | `EMAIL_MISMATCH` | Email mismatch for userId=... |
| Sıfırlama token'ı bulunamadı | `RESET_TOKEN_NOT_FOUND` | User or reset token not found for email=... |
| Sıfırlama token süresi dolmuş | `TOKEN_EXPIRED` | Password reset token has expired |
| Sıfırlama token kullanılmış | `TOKEN_ALREADY_USED` | Password reset token has already been used |
| Kullanıcı bulunamadı | `USER_NOT_FOUND` | User not found for userId=... |
| Oturum açılmamış | `UNAUTHORIZED` | Not authenticated |
| Refresh token geçersiz | `UNAUTHORIZED` | Invalid or expired refresh token |

### profile-service

| Senaryo | ErrorCode | Örnek Mesaj |
|---------|-----------|-------------|
| Profil bulunamadı | `PROFILE_NOT_FOUND` | No profile exists for keycloakId='...' |
| Profil tamamlanmamış | `PROFILE_NOT_COMPLETED` | Profile for username='...' exists but has not been completed yet |
| Avatar anahtarı sahipliği hatalı | `AVATAR_INVALID_OWNERSHIP` | Invalid avatar key ownership: key='...' does not match... |
| Desteklenmeyen dosya türü | `AVATAR_UNSUPPORTED_TYPE` | Unsupported file type 'image/gif'. Allowed: [...] |
| Dosya boyutu aşıldı | `AVATAR_FILE_TOO_LARGE` | File size 12345678 bytes exceeds maximum of 5 MB |

### zeabay-keycloak (common)

| Senaryo | ErrorCode | Örnek Mesaj |
|---------|-----------|-------------|
| Keycloak kullanıcı oluşturma hatası | `IDENTITY_PROVIDER_ERROR` | Keycloak user creation failed: ... |
| Keycloak giriş hatası | `INVALID_CREDENTIALS` | Keycloak authentication failed: ... |

---

## Form Doğrulama (Validation) Hata Anahtarları

Form doğrulamasından geçemeyen isteklerde ana hata kodu **`VALIDATION_ERROR`** döner. Hangi alanın
neden geçersiz olduğu `error.validationErrors` listesinde belirtilir.

### messageKey Format Kuralı

Backend, Jakarta Validation anotasyonlarını otomatik olarak i18n çeviri anahtarlarına dönüştürür:

```
validation.<alan_adı>.<AnotasyonAdı>
```

**Örnek:** `password` alanı `@Size` kuralını ihlal ederse → `validation.password.Size`

### Auth Modülü — Sık Karşılaşılan Validation Hataları

#### `username` Alanı (Register)

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.username.NotBlank` | Kullanıcı adı boş bırakılamaz |
| `@Size` | `validation.username.Size` | Uzunluk 3–30 karakter sınırına uymuyor |
| `@Pattern` | `validation.username.Pattern` | Format hatalı (desteklenmeyen karakter veya ardışık nokta) |

#### `usernameOrEmail` Alanı (Login)

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.usernameOrEmail.NotBlank` | Kullanıcı adı veya e-posta boş bırakılamaz |
| `@Size` | `validation.usernameOrEmail.Size` | Maks 254 karakter sınırına uymuyor |

#### `email` Alanı

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.email.NotBlank` | E-posta alanı boş bırakılamaz |
| `@Email` | `validation.email.Email` | Geçersiz e-posta formatı |
| `@Size` | `validation.email.Size` | E-posta 254 karakterden uzun |

#### `password` Alanı

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.password.NotBlank` | Şifre alanı boş bırakılamaz |
| `@Size` | `validation.password.Size` | Uzunluk 8–100 karakter sınırına uymuyor |
| `@Pattern` | `validation.password.Pattern` | Karmaşıklık kurallarına uymuyor (büyük/küçük harf, rakam, özel karakter) |

#### `token` Alanı (E-posta Doğrulama)

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.token.NotBlank` | Doğrulama kodu boş bırakılamaz |
| `@Size` | `validation.token.Size` | Kod tam olarak 6 haneli olmalıdır |
| `@Pattern` | `validation.token.Pattern` | Kod sadece rakamlardan oluşmalıdır |

#### `otp` Alanı (Şifre Sıfırlama OTP)

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.otp.NotBlank` | OTP kodu boş bırakılamaz |
| `@Size` | `validation.otp.Size` | OTP tam olarak 6 haneli olmalıdır |
| `@Pattern` | `validation.otp.Pattern` | OTP sadece rakamlardan oluşmalıdır (`^\d{6}$`) |

#### `refreshToken` Alanı

| İhlal Edilen Kural | Üretilen messageKey | Ne Anlama Geliyor? |
|--------------------|--------------------|--------------------|
| `@NotBlank` | `validation.refreshToken.NotBlank` | Refresh token boş bırakılamaz |

---

## İstemci i18n Entegrasyonu

Aşağıda, tüm hata anahtarlarının istemci tarafındaki `tr.json` / `en.json` gibi i18n dosyalarına nasıl eklenmesi gerektiğini
gösteren **minimum i18n şablonu** bulunmaktadır:

<details>
<summary>📄 tr.json — Sistem Hata Mesajları Şablonu</summary>

```json
{
  "error.bad_request": "Geçersiz istek. Lütfen bilgileri kontrol edin.",
  "error.validation": "Formda hata(lar) var. Lütfen aşağıdaki alanları düzeltin.",
  "error.business_error": "İş kuralı ihlali oluştu.",
  "error.invalid_input": "Girilen veriler geçersiz.",
  "error.email_mismatch": "E-posta adresi eşleşmiyor.",
  "error.unauthorized": "Bu işlem için giriş yapmanız gerekiyor.",
  "error.token_expired": "Oturumunuzun süresi doldu. Lütfen tekrar giriş yapın.",
  "error.invalid_token": "Geçersiz veya süresi dolmuş bir token kullanıldı.",
  "error.invalid_credentials": "Kullanıcı adı veya şifre hatalı.",
  "error.token_already_used": "Bu kod daha önce kullanılmış.",
  "error.forbidden": "Bu işlemi gerçekleştirme yetkiniz yok.",
  "error.email_not_verified": "Hesabınızı kullanabilmek için e-posta adresinizi doğrulayın.",
  "error.account_disabled": "Hesabınız askıya alınmış.",
  "error.not_found": "Aradığınız kaynak bulunamadı.",
  "error.user_not_found": "Kullanıcı bulunamadı.",
  "error.profile_not_found": "Profil bulunamadı.",
  "error.profile_not_completed": "Bu profil henüz tamamlanmamış.",
  "error.verification_token_not_found": "Doğrulama kodu bulunamadı veya geçersiz.",
  "error.reset_token_not_found": "Şifre sıfırlama kodu bulunamadı veya geçersiz.",
  "error.conflict": "Kaynak çakışması oluştu.",
  "error.user_already_exists": "Bu e-posta veya kullanıcı adı zaten kayıtlı.",
  "error.duplicate_entry": "Bu kayıt zaten mevcut.",
  "error.avatar_invalid_ownership": "Bu avatar size ait değil.",
  "error.avatar_unsupported_type": "Desteklenmeyen dosya türü.",
  "error.avatar_file_too_large": "Avatar dosyası çok büyük.",
  "error.rate_limit_exceeded": "Çok fazla istek gönderdiniz. Lütfen biraz bekleyin.",
  "error.internal_error": "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin.",
  "error.service_unavailable": "Servis şu anda kullanılamıyor.",
  "error.identity_provider_error": "Kimlik doğrulama servisi hatası. Lütfen tekrar deneyin.",
  "error.gateway_timeout": "Sunucu yanıt vermiyor. Lütfen daha sonra tekrar deneyin."
}
```

</details>

<details>
<summary>📄 tr.json — Validation Hata Mesajları Şablonu</summary>

```json
{
  "validation.username.NotBlank": "Kullanıcı adı boş bırakılamaz.",
  "validation.username.Size": "Kullanıcı adı 3–30 karakter arasında olmalıdır.",
  "validation.username.Pattern": "Kullanıcı adı yalnızca harf, rakam, alt çizgi ve nokta içerebilir.",
  "validation.usernameOrEmail.NotBlank": "Kullanıcı adı veya e-posta boş bırakılamaz.",
  "validation.usernameOrEmail.Size": "Kullanıcı adı veya e-posta çok uzun.",
  "validation.email.NotBlank": "E-posta adresi boş bırakılamaz.",
  "validation.email.Email": "Geçerli bir e-posta adresi giriniz.",
  "validation.email.Size": "E-posta adresi çok uzun.",
  "validation.password.NotBlank": "Şifre boş bırakılamaz.",
  "validation.password.Size": "Şifre 8–100 karakter arasında olmalıdır.",
  "validation.password.Pattern": "Şifre en az bir büyük harf, bir küçük harf, bir rakam ve bir özel karakter içermelidir.",
  "validation.token.NotBlank": "Doğrulama kodu boş bırakılamaz.",
  "validation.token.Size": "Doğrulama kodu 6 haneli olmalıdır.",
  "validation.token.Pattern": "Doğrulama kodu sadece rakamlardan oluşmalıdır.",
  "validation.otp.NotBlank": "Doğrulama kodu boş bırakılamaz.",
  "validation.otp.Size": "Doğrulama kodu 6 haneli olmalıdır.",
  "validation.otp.Pattern": "Doğrulama kodu sadece rakamlardan oluşmalıdır.",
  "validation.refreshToken.NotBlank": "Yenileme token'ı boş bırakılamaz."
}
```

</details>

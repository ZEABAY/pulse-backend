# 📦 Yanıt Modeli — `ZeabayApiResponse` Spesifikasyonu

> **Kaynak Kod:** `zeabay-common/zeabay-webflux` modülü

Bu doküman, tüm Pulse Platform API yanıtlarının sarmalandığı standart kapsüleyici (wrapper) yapıları detaylandırır.

---

## Genel Bakış

Tüm başarılı ve hatalı JSON yanıtları **aynı üst düzey yapıyla** dönülür. Bu sayede istemci tarafında
tek bir response interceptor ile tüm yanıtlar tutarlı şekilde işlenebilir.

```
ZeabayApiResponse<T>
├── success        boolean
├── data           T | null
├── error          ErrorResponse | null  
├── traceId        string
└── timestamp      ISO-8601 string
```

---

## `ZeabayApiResponse<T>` (Üst Düzey Wrapper)

Tüm API yanıtlarını sarmalayan ana record yapısı.

**Java Tanımı:**
```java
public record ZeabayApiResponse<T>(
    boolean success,
    T data,
    ErrorResponse error,
    String traceId,
    Instant timestamp
)
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `success` | `boolean` | İstek başarılı mı? `true` = başarılı, `false` = hata |
| `data` | `T \| null` | Başarılı yanıtlarda veri nesnesi. Hata durumunda `null`. |
| `error` | `ErrorResponse \| null` | Hata durumunda hata detayları. Başarılı yanıtlarda `null`. |
| `traceId` | `string` | İsteğin takibi için benzersiz iz kimliği (distributed tracing). |
| `timestamp` | `string` | Yanıtın üretildiği zaman damgası (ISO-8601 formatında). |

> **Önemli:** `success = true` ise `error` her zaman `null`'dır. `success = false` ise `data` her zaman `null`'dır.

---

## Başarılı Yanıt Örnekleri

### Veri dönen başarılı yanıt (`200 OK` / `201 Created`)

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5c...",
    "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5c...",
    "expiresIn": 3600
  },
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### String mesaj dönen başarılı yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "Email verified successfully",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-03-31T09:00:00.000Z"
}
```

### Body'siz başarılı yanıt (`204 No Content`)

`204` dönen endpoint'lerde (`/logout` gibi) response body **boştur**. `ZeabayApiResponse` wrapper'ı kullanılmaz.

```http
HTTP/1.1 204 No Content
```

---

## Hata Yanıt Yapısı

### `ErrorResponse`

Hata durumunda `ZeabayApiResponse.error` alanı içinde yer alan detay nesnesi.

**Java Tanımı:**
```java
public record ErrorResponse(
    String code,
    String messageKey,
    String path,
    Instant timestamp,
    List<ValidationError> validationErrors
)
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `code` | `string` | Standart hata kodu (ErrorCode enum değeri). Örn: `VALIDATION_ERROR`, `UNAUTHORIZED` |
| `messageKey` | `string` | İstemci i18n çeviri anahtarı. Format: `error.<error_code_küçük_harf>` |
| `path` | `string` | Hatanın oluştuğu endpoint yolu. Örn: `/api/v1/auth/login` |
| `timestamp` | `string` | Hatanın oluştuğu zaman damgası (ISO-8601) |
| `validationErrors` | `array` | Form doğrulama hatalarının listesi. Validation hatası yoksa boş dizi (`[]`). |

### `ValidationError`

Her bir alan-düzeyindeki doğrulama ihlalini temsil eden yapı.

**Java Tanımı:**
```java
public record ValidationError(
    String field,
    String messageKey
)
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `field` | `string` | Hatalı form alanının adı. Örn: `username`, `password`, `email` |
| `messageKey` | `string` | Alan-spesifik i18n çeviri anahtarı. Format: `validation.<alan>.<Kural>` |

---

## Hata Yanıt Örnekleri

### Genel İş Hatası (Business Error)

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "messageKey": "error.invalid_credentials",
    "path": "/api/v1/auth/login",
    "timestamp": "2026-03-31T09:01:00.000Z",
    "validationErrors": []
  },
  "traceId": "x9y8z7w6v5u4",
  "timestamp": "2026-03-31T09:01:00.000Z"
}
```

### Form Doğrulama Hatası (Validation Error)

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "messageKey": "error.validation",
    "path": "/api/v1/auth/register",
    "timestamp": "2026-03-31T09:01:00.000Z",
    "validationErrors": [
      {
        "field": "password",
        "messageKey": "validation.password.Size"
      },
      {
        "field": "username",
        "messageKey": "validation.username.Pattern"
      }
    ]
  },
  "traceId": "x9y8z7w6v5u4",
  "timestamp": "2026-03-31T09:01:00.000Z"
}
```

> `validationErrors` dizisinde birden fazla alan hatası aynı anda dönebilir. İstemci bu listeyi
> iterate ederek ilgili form alanlarının altında hata mesajları göstermelidir.

---

## İstemci Entegrasyon Kılavuzu

### Önerilen Response Interceptor Yapısı

```
// Pseudo-kod: HTTP Response Interceptor
onResponse(response):
  if response.status == 204:
    return response                      // Body yok, direkt dön

  body = response.data                   // ZeabayApiResponse
  return body.data                       // Sadece data'yı ilet

onError(error):
  body = error.response?.data

  if body?.error?.code == "VALIDATION_ERROR":
    // Alan bazlı hata gösterimi
    for each ve in body.error.validationErrors:
      show(ve.field, translate(ve.messageKey))
    return reject("validation", fieldErrors)

  if body?.error?.code == "TOKEN_EXPIRED":
    // Silent refresh akışını tetikle
    return refreshAndRetry(error.config)

  // Genel hata mesajı
  message = translate(body?.error?.messageKey || "error.internal_error")
  return reject("general", message)
```

### i18n Anahtarı Çözümleme Kuralları

| Hata Tipi | messageKey Formatı | Örnek |
|-----------|-------------------|-------|
| Sistem/İş hatası | `error.<error_code_küçük_harf>` | `error.invalid_credentials` |
| Validation hatası (ana) | `error.validation` | — |
| Validation hatası (alan) | `validation.<alan_adı>.<KuralAdı>` | `validation.password.Size` |

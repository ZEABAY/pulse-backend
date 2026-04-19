# 👤 Profile Modülü — Endpoint Detayları

> **Servis:** `profile-service` · **Base Path:** `/api/v1/profiles` · **Port:** 8083 · **Protokol:** HTTP/JSON (WebFlux — Reactive)

Bu doküman, `profile-service` altındaki tüm endpoint'lerin detaylı request/response spesifikasyonlarını içerir.

---

## İçindekiler

- [2.1 Kendi Profilimi Getir (Get My Profile)](#21-kendi-profilimi-getir-get-my-profile)
- [2.2 Profilimi Tamamla (Complete Profile)](#22-profilimi-tamamla-complete-profile)
- [2.3 Profilimi Güncelle (Partial Update Profile)](#23-profilimi-güncelle-partial-update-profile)
- [2.4 Avatar Upload URL'i Al (Get Avatar Upload URL)](#24-avatar-upload-urli-al-get-avatar-upload-url)
- [2.5 Avatarımı Sil (Delete Avatar)](#25-avatarımı-sil-delete-avatar)
- [2.6 Public Profil Görüntüle (Get Public Profile)](#26-public-profil-görüntüle-get-public-profile)

---

## Genel Bilgiler

### Event-Driven Profil Oluşturma

Profil kaydı **otomatik** olarak oluşturulur. `auth-service`'te e-posta doğrulama (`POST /auth/verify`) başarıyla
tamamlandığında, Kafka üzerinden `UserVerifiedEvent` publish edilir. `profile-service` bu event'i consume ederek
`profileCompleted = false` olan bir **skeleton profil** oluşturur.

Frontend, kullanıcının ilk login'i sonrasında `GET /profiles/me` ile profili kontrol eder. `profileCompleted: false`
ise kullanıcıyı profil doldurma formuna yönlendirir.

### Yetkilendirme

Tüm endpoint'ler **JWT Bearer Token** gerektirir. Token'dan `sub` claim'i çekilerek `keycloakId` elde edilir.

```http
Authorization: Bearer <access_token>
```

### Avatar Yükleme Mimarisi (Presigned URL)

Avatar dosyaları **frontend'den doğrudan MinIO'ya** yüklenir (sunucu üzerinden geçmez):

```
1. Client → POST /profiles/me/avatar {fileName, contentType, fileSize}
2. Server → Presigned PUT URL üret → Client'a dön
3. Client → PUT <presigned_url> (binary dosya) → MinIO'ya direkt upload
4. Client → PUT /profiles/me {avatarKey: "..."} → Profili güncelle
```

> **Not:** Presigned URL'in süresi 5 dakikadır. İzin verilen dosya tipleri: `image/jpeg`, `image/png`, `image/webp`. Maksimum dosya boyutu: 5 MB.

---

## 2.1 Kendi Profilimi Getir (Get My Profile)

JWT token'dan kimlik çözülerek, oturum açmış kullanıcının tam profilini döner.

| Özellik | Değer |
|---------|-------|
| **URL** | `GET /api/v1/profiles/me` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Request Body** | Yok |
| **Başarılı Durum Kodu** | `200 OK` |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": {
    "username": "john_doe",
    "firstName": "John",
    "lastName": "Doe",
    "dateOfBirth": "1995-06-15",
    "phoneNumber": "+905551234567",
    "avatarUrl": "http://localhost:9000/pulse-avatars/avatars/uuid/img.webp",
    "bio": "Software developer from Istanbul",
    "gender": "MALE",
    "location": "İstanbul, Türkiye",
    "profileCompleted": true
  },
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-04-13T09:00:00.000Z"
}
```

| Response Alanı | Tip | Açıklama |
|----------------|-----|----------|
| `username` | `string` | Kullanıcı adı (auth-service'ten event ile sync edilir) |
| `firstName` | `string` | İsim (`null` olabilir — profil tamamlanmamışsa) |
| `lastName` | `string` | Soyisim |
| `dateOfBirth` | `date` | Doğum tarihi (ISO 8601) |
| `phoneNumber` | `string` | Telefon numarası (E.164 format) |
| `avatarUrl` | `string` | Profil fotoğrafı tam URL'i (MinIO) |
| `bio` | `string` | Biyografi |
| `gender` | `string` | `MALE`, `FEMALE`, `OTHER` veya `PREFER_NOT_TO_SAY` |
| `location` | `string` | Şehir/ülke |
| `profileCompleted` | `boolean` | Zorunlu alanlar (firstName, lastName, dateOfBirth) dolduruldu mu? |

> **İpucu:** İlk login'de `profileCompleted: false` dönecektir. Frontend bu durumda kullanıcıyı profil
> oluşturma formuna yönlendirmelidir.

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz |
| `404` | `NOT_FOUND` | Profil bulunamadı (henüz UserVerifiedEvent alınmamış olabilir) |

---

## 2.2 Profilimi Tamamla (Complete Profile)

İlk kayıt (onboarding) sırasında kullanıcının profilini tamamlar. Tüm zorunlu alanlar (firstName, lastName, dateOfBirth)
gönderilmesi **zorunludur**. İşlem sonrası `profileCompleted` otomatik olarak `true` yapılır.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/profiles/me/complete` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1995-06-15",
  "phoneNumber": "+905551234567",
  "bio": "Software developer from Istanbul",
  "gender": "MALE",
  "location": "İstanbul, Türkiye",
  "avatarKey": "avatars/uuid/img.webp"
}
```

#### Alan Detayları

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `firstName` | `string` | ✓ | 2–50 karakter |
| `lastName` | `string` | ✓ | 2–50 karakter |
| `dateOfBirth` | `date` | ✓ | ISO 8601 formatı. Geçmiş bir tarih olmalı (min 13 yaş) |
| `phoneNumber` | `string` | ✗ | Maks 20 karakter, E.164 format önerilir |
| `bio` | `string` | ✗ | Maks 500 karakter |
| `gender` | `string` | ✗ | `MALE`, `FEMALE`, `OTHER` veya `PREFER_NOT_TO_SAY` |
| `location` | `string` | ✗ | Maks 100 karakter |
| `avatarKey` | `string` | ✗ | `POST /profiles/me/avatar`'den dönen `objectKey` değeri |

### Başarılı Yanıt (`200 OK`)

Tamamlanmış tam profil objesi (2.1 ile aynı format).

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Alan kısıtlamalarına uyulmuyor veya zorunlu bilgi eksik |
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz |
| `404` | `NOT_FOUND` | Profil bulunamadı |

---

## 2.3 Profilimi Güncelle (Partial Update Profile)

Zaten tamamlanmış bir profili **kısmi** olarak günceller. İstediğiniz herhangi bir alanı gönderebilirsiniz (hiçbirisi zorunlu değildir).

| Özellik | Değer |
|---------|-------|
| **URL** | `PATCH /api/v1/profiles/me` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1995-06-15",
  "phoneNumber": "+905551234567",
  "bio": "Software developer from Istanbul",
  "gender": "MALE",
  "location": "İstanbul, Türkiye",
  "avatarKey": "avatars/uuid/img.webp"
}
```

#### Alan Detayları

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `firstName` | `string` | ✗ | 2–50 karakter |
| `lastName` | `string` | ✗ | 2–50 karakter |
| `dateOfBirth` | `date` | ✗ | ISO 8601 formatı. Geçmiş bir tarih olmalı |
| `phoneNumber` | `string` | ✗ | Maks 20 karakter |
| `bio` | `string` | ✗ | Maks 500 karakter |
| `gender` | `string` | ✗ | `MALE`, `FEMALE`, `OTHER` veya `PREFER_NOT_TO_SAY` |
| `location` | `string` | ✗ | Maks 100 karakter |
| `avatarKey` | `string` | ✗ | `objectKey` değeri |

### Başarılı Yanıt (`200 OK`)

Güncellenmiş tam profil objesi (2.1 ile aynı format).

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Alan kısıtlamalarına uyulmuyor (isim çok kısa, geçersiz gender vb.) |
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz |
| `404` | `NOT_FOUND` | Profil bulunamadı |

---

## 2.4 Avatar Upload URL'i Al (Get Avatar Upload URL)

MinIO'ya doğrudan dosya yüklemek için **presigned PUT URL** üretir. Client bu URL'e binary dosyayı
direkt PUT ile gönderir — sunucu (profile-service) dosya trafiğinden geçmez.

| Özellik | Değer |
|---------|-------|
| **URL** | `POST /api/v1/profiles/me/avatar` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Content-Type** | `application/json` |
| **Başarılı Durum Kodu** | `200 OK` |

### Request Body

```json
{
  "fileName": "photo.jpg",
  "contentType": "image/jpeg",
  "fileSize": 2048576
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `fileName` | `string` | ✓ | Orijinal dosya adı (uzantı çıkarımı için) |
| `contentType` | `string` | ✓ | `image/jpeg`, `image/png` veya `image/webp` |
| `fileSize` | `long` | ✓ | Dosya boyutu bayt cinsinden. Maks 5 MB (5.242.880 bayt) |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": {
    "uploadUrl": "http://localhost:9000/pulse-avatars/avatars/keycloak-id/uuid.jpg?X-Amz-Algorithm=...",
    "objectKey": "avatars/keycloak-id/uuid.jpg"
  },
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-04-13T09:00:00.000Z"
}
```

| Response Alanı | Tip | Açıklama |
|----------------|-----|----------|
| `uploadUrl` | `string` | 5 dakika geçerli presigned PUT URL'i. Client bu URL'e dosyayı doğrudan yükler. |
| `objectKey` | `string` | MinIO object key. `PUT /profiles/me` isteğinde `avatarKey` olarak gönderilir. |

### Avatar Yükleme Adımları

```
┌────────┐                    ┌────────────────┐              ┌───────┐
│ Client │                    │profile-service │              │ MinIO │
└───┬────┘                    └───────┬────────┘              └───┬───┘
    │                                 │                           │
    │ 1. POST /profiles/me/avatar     │                           │
    │────────────────────────────────▶│                           │
    │ 2. {uploadUrl, objectKey}       │                           │
    │◀────────────────────────────────│                           │
    │                                 │                           │
    │ 3. PUT <uploadUrl>              │                           │
    │   Content-Type: image/jpeg      │                           │
    │   [binary body]                 │                           │
    │────────────────────────────────────────────────────────────▶│
    │ 4. 200 OK                       │                           │
    │◀────────────────────────────────────────────────────────────│
    │                                 │                           │
    │ 5. PUT /profiles/me             │                           │
    │   {avatarKey: "objectKey"}      │                           │
    │────────────────────────────────▶│                           │
    │ 6. 200 {avatarUrl: "..."}       │                           │
    │◀────────────────────────────────│                           │
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `400` | `VALIDATION_ERROR` | Desteklenmeyen dosya tipi veya dosya boyutu 5 MB'ı aşıyor |
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz |

---

## 2.5 Avatarımı Sil (Delete Avatar)

Kullanıcının mevcut avatarını MinIO'dan siler ve profildeki referansı temizler.

| Özellik | Değer |
|---------|-------|
| **URL** | `DELETE /api/v1/profiles/me/avatar` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Request Body** | Yok |
| **Başarılı Durum Kodu** | `200 OK` |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": "Avatar deleted successfully",
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-04-13T09:00:00.000Z"
}
```

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz |
| `404` | `NOT_FOUND` | Profil bulunamadı |

---

## 2.6 Public Profil Görüntüle (Get Public Profile)

Belirtilen kullanıcı adıyla eşleşen tamamlanmış profilin **public alanlarını** döner.
Hassas bilgiler (doğum tarihi, telefon numarası, cinsiyet) bu endpoint'ten **dönmez**.

| Özellik | Değer |
|---------|-------|
| **URL** | `GET /api/v1/profiles/{username}` |
| **Auth** | **Evet** — `Authorization: Bearer <access_token>` zorunlu |
| **Request Body** | Yok |
| **Başarılı Durum Kodu** | `200 OK` |

### Path Parameter

| Parametre | Tip | Zorunlu | Açıklama |
|-----------|-----|---------|----------|
| `username` | `string` | ✓ | Profili görüntülenecek kullanıcının kullanıcı adı |

### Başarılı Yanıt (`200 OK`)

```json
{
  "success": true,
  "data": {
    "username": "john_doe",
    "firstName": "John",
    "lastName": "Doe",
    "avatarUrl": "http://localhost:9000/pulse-avatars/avatars/uuid/img.webp",
    "bio": "Software developer from Istanbul",
    "location": "İstanbul, Türkiye"
  },
  "error": null,
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-04-13T09:00:00.000Z"
}
```

> ⚠️ **Güvenlik:** `dateOfBirth`, `phoneNumber`, `gender` alanları public profilde **yer almaz**.

### Olası Hatalar

| HTTP | ErrorCode | Açıklama |
|------|-----------|----------|
| `401` | `UNAUTHORIZED` | Bearer token eksik veya geçersiz |
| `404` | `NOT_FOUND` | Kullanıcı bulunamadı veya profili henüz tamamlanmamış |

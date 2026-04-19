# Pulse Platform — Backend API Dokümantasyonu

> **Sürüm:** 1.2.0 · **Son Güncelleme:** 2026-04-13 · **Ortam:** Yerel Geliştirme

Bu doküman, Pulse Platform mikroservis altyapısıyla haberleşen istemcilerin (web, mobil, 3. parti vb.) referans
alacağı **kapsamlı API spesifikasyonlarını** barındırmaktadır.

---

## 📚 Dokümantasyon Haritası

| Doküman | Açıklama | Bağlantı |
|---------|----------|----------|
| **Ana Dokümantasyon** | Genel mimarî kavramlar ve Base URL | Bu dosya |
| **Auth Modülü** | Kayıt, giriş, çıkış, şifre sıfırlama endpoint'leri | [→ auth-endpoints.md](./api/auth-endpoints.md) |
| **Profile Modülü** | Profil CRUD, avatar yükleme, public profil | [→ profile-endpoints.md](./api/profile-endpoints.md) |
| **Yanıt Modeli** | `ZeabayApiResponse` wrapper ve hata yapıları | [→ response-model.md](./api/response-model.md) |
| **Hata Kodları** | ErrorCode enum tablosu ve validation kuralları | [→ error-codes.md](./api/error-codes.md) |

---

## 🌍 Genel Kavramlar

### Base URL

Yerel ortam geliştirme için API uç noktaları **API Gateway** üzerinden sunulmaktadır:

```
http://localhost:8080/api/v1
```

> Tüm endpoint bağıl yolları bu base adrese göre tanımlanmıştır.

### Mimari Genel Bakış

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Client    │────▶│  Pulse Gateway   │────▶│  auth-service   │
│ (Web/Mobil) │     │  (Spring Cloud)  │──┐  │  (WebFlux)      │
└─────────────┘     └──────────────────┘  │  └───────┬─────────┘
                                          │          │ UserVerifiedEvent
                                          │  ┌───────▼─────────┐
                                          └─▶│profile-service  │
                                             │  (WebFlux)      │
                                             └───────┬─────────┘
                                                     │
                    ┌───────┬───────────┬─────────┬──┴──────┐
                    │       │           │         │         │
               ┌────▼──┐┌───▼───┐ ┌────▼───┐ ┌───▼───┐ ┌───▼───┐
               │Keycloak││ Redis │ │ Kafka  │ │Postgre│ │ MinIO │
               │ (IAM) ││(Cache)│ │(Events)│ │  SQL  │ │(S3)   │
               └───────┘└───────┘ └────────┘ └───────┘ └───────┘
```

### Yetkilendirme (Authentication)

Giriş gerektiren (**Protected**) tüm endpoint'lerde HTTP Header içerisinde `accessToken` gönderilmelidir:

```http
Authorization: Bearer <access_token>
```

> `accessToken` süresi dolduğunda `/api/v1/auth/refresh` endpoint'iyle sessizce yenilenmelidir.

---

## 🔐 Aktif Modüller

### 1. Authentication (`auth-service`)

Kullanıcı kayıt, giriş, e-posta doğrulama, token yönetimi ve şifre sıfırlama akışlarını yönetir.

**Endpoint Özet Tablosu:**

| # | Endpoint | Method | Auth | Açıklama |
|---|----------|--------|------|----------|
| 1.1 | `/api/v1/auth/register` | `POST` | ✗ | Yeni kullanıcı kaydı |
| 1.2 | `/api/v1/auth/login` | `POST` | ✗ | Giriş yapma |
| 1.3 | `/api/v1/auth/verify` | `POST` | ✗ | E-posta doğrulama |
| 1.4 | `/api/v1/auth/refresh` | `POST` | ✗¹ | Token yenileme |
| 1.5 | `/api/v1/auth/logout` | `POST` | ✓ | Oturum sonlandırma |
| 1.6 | `/api/v1/auth/forgot-password` | `POST` | ✗ | Şifre sıfırlama talebi |
| 1.7 | `/api/v1/auth/verify-reset-otp` | `POST` | ✗ | OTP doğrulama |
| 1.8 | `/api/v1/auth/reset-password` | `POST` | ✗ | Yeni şifre belirleme |

> ¹ Bearer token gerekmez ancak geçerli bir `refreshToken` zorunludur.

**Detaylı döküman:** [→ auth-endpoints.md](./api/auth-endpoints.md)

---

### 2. Profile (`profile-service`)

Kullanıcı profil bilgilerini (isim, soyisim, doğum tarihi, cinsiyet, konum, biyografi) yönetir.
Avatar yükleme, MinIO üzerinden Presigned URL ile yapılır. Profiller Redis ile cache'lenir.

**Endpoint Özet Tablosu:**

| # | Endpoint | Method | Auth | Açıklama |
|---|----------|--------|------|----------|
| 2.1 | `/api/v1/profiles/me` | `GET` | ✓ | Kendi profilimi getir |
| 2.2 | `/api/v1/profiles/me/complete` | `POST` | ✓ | Profilimi tamamla (İlk Kayıt) |
| 2.3 | `/api/v1/profiles/me` | `PATCH` | ✓ | Profilimi güncelle (Kısmi) |
| 2.4 | `/api/v1/profiles/me/avatar` | `POST` | ✓ | Avatar upload URL'i al |
| 2.5 | `/api/v1/profiles/me/avatar` | `DELETE` | ✓ | Avatarımı sil |
| 2.6 | `/api/v1/profiles/{username}` | `GET` | ✓ | Public profil görüntüle |

**Detaylı döküman:** [→ profile-endpoints.md](./api/profile-endpoints.md)

---

## 🚀 Kullanıcı Akışları (User Journeys)

### Akış 1: Yeni Kayıt, Aktivasyon ve Profil Oluşturma

```
┌────────┐                ┌────────────┐   ┌────────┐   ┌────────────────┐
│ Client │                │auth-service│   │  Mail  │   │profile-service │
└───┬────┘                └─────┬──────┘   └───┬────┘   └───────┬────────┘
    │                           │               │               │
    │  POST /auth/register      │               │               │
    │──────────────────────────▶│               │               │
    │  200 "User registered"    │               │               │
    │◀──────────────────────────│               │               │
    │                           │ Kafka → OTP   │               │
    │                           │──────────────▶│               │
    │                           │               │──▶ 📧          │
    │  POST /auth/verify        │               │               │
    │──────────────────────────▶│               │               │
    │  200 "Email verified"     │               │               │
    │◀──────────────────────────│               │               │
    │                           │ Kafka → UserVerifiedEvent      │
    │                           │──────────────────────────────▶│
    │                           │               │  skeleton     │
    │                           │               │  profile      │
    │                           │               │  created      │
    │  POST /auth/login         │               │               │
    │──────────────────────────▶│               │               │
    │  200 {accessToken, ...}   │               │               │
    │◀──────────────────────────│               │               │
    │                           │               │               │
    │  GET /profiles/me         │               │               │
    │───────────────────────────────────────────────────────────▶│
    │  200 {profileCompleted: false}                             │
    │◀───────────────────────────────────────────────────────────│
    │                                                            │
    │  POST /profiles/me/complete {firstName, lastName, dob, ...}│
    │───────────────────────────────────────────────────────────▶│
    │  200 {profileCompleted: true}                              │
    │◀───────────────────────────────────────────────────────────│
```

### Akış 2: Giriş ve Oturum Yenileme

```
┌────────┐                        ┌────────────┐
│ Client │                        │auth-service│
└───┬────┘                        └─────┬──────┘
    │                                   │
    │  POST /auth/login                 │
    │──────────────────────────────────▶│
    │  200 {accessToken, refreshToken}  │
    │◀──────────────────────────────────│
    │                                   │
    │  -- Token suresi dolunca 401 --   │
    │                                   │
    │  POST /auth/refresh {refreshToken}│
    │──────────────────────────────────▶│
    │  200 {yeni accessToken, ...}      │
    │◀──────────────────────────────────│
```

### Akış 3: Şifremi Unuttum (3 Adım)

```
┌────────┐                        ┌────────────┐
│ Client │                        │auth-service│
└───┬────┘                        └─────┬──────┘
    │                                   │
    │  1. POST /auth/forgot-password    │
    │──────────────────────────────────▶│ -> OTP gonderilir
    │  200 "processed"                  │
    │◀──────────────────────────────────│
    │                                   │
    │  2. POST /auth/verify-reset-otp   │
    │──────────────────────────────────▶│ -> OTP dogrulanir
    │  200 "OTP is valid"               │
    │◀──────────────────────────────────│
    │                                   │
    │  3. POST /auth/reset-password     │
    │──────────────────────────────────▶│ -> Sifre guncellenir
    │  200 "Password reset successful"  │
    │◀──────────────────────────────────│
```

### Akış 4: Çıkış

```
┌────────┐                        ┌────────────┐
│ Client │                        │auth-service│
└───┬────┘                        └─────┬──────┘
    │                                   │
    │  POST /auth/logout                │
    │  Authorization: Bearer <token>    │
    │──────────────────────────────────▶│
    │                                   │ -> Keycloak oturumu sonlandirilir
    │                                   │ -> Token blacklist'e alinir
    │  204 No Content                   │
    │◀──────────────────────────────────│
```

---

## 🛠 Servis Port ve Teknoloji Tablosu

| Servis | Port | Teknoloji |
|--------|------|-----------|
| `pulse-gateway` | 8080 | Spring Cloud Gateway, Redis (Rate Limit) |
| `mail-service` | 8081 | WebFlux, Kafka, SMTP |
| `auth-service` | 8082 | WebFlux, R2DBC, Kafka (Outbox), Keycloak, Redis |
| `profile-service` | 8083 | WebFlux, R2DBC, Kafka (Inbox), MinIO (S3), Redis |

**Altyapı Servisleri:**

| Servis | Port | Açıklama |
|--------|------|----------|
| PostgreSQL | 5432 | Ana veritabanı (schema-per-service) |
| Redis | 6379 | Cache, Token Blacklist, Rate Limiting |
| Kafka | 9092 | Event-driven mesajlaşma |
| Keycloak | 9080 | IAM / Identity Provider |
| Consul | 8500 | Service Discovery |
| MinIO API | 9000 | S3-compatible Object Storage |
| MinIO Console | 9001 | MinIO Web UI |

---

## 📖 Kaynaklar

- **Yanıt Modeli ve Hata Yapıları:** [response-model.md](./api/response-model.md)
- **Hata Kodları ve Validation Kuralları:** [error-codes.md](./api/error-codes.md)
- **Auth Endpoint Detayları:** [auth-endpoints.md](./api/auth-endpoints.md)
- **Profile Endpoint Detayları:** [profile-endpoints.md](./api/profile-endpoints.md)

---

> **Geliştirici Notu:** İstemci geliştirirken bu Base URL'i ve `ZeabayApiResponse` wrapper yapısını
> merkezi bir HTTP interceptor aracılığıyla çözmeniz önerilir.

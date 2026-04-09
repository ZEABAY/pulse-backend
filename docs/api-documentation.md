# Pulse Platform — Backend API Dokümantasyonu

> **Sürüm:** 1.1.0 · **Son Güncelleme:** 2026-03-31 · **Ortam:** Yerel Geliştirme

Bu doküman, Pulse Platform mikroservis altyapısıyla haberleşen istemcilerin (web, mobil, 3. parti vb.) referans
alacağı **kapsamlı API spesifikasyonlarını** barındırmaktadır.

---

## 📚 Dokümantasyon Haritası

| Doküman | Açıklama | Bağlantı |
|---------|----------|----------|
| **Ana Dokümantasyon** | Genel mimarî kavramlar ve Base URL | Bu dosya |
| **Auth Modülü** | Kayıt, giriş, çıkış, şifre sıfırlama endpoint'leri | [→ auth-endpoints.md](./api/auth-endpoints.md) |
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
┌─────────────┐     ┌──────────────────┐     ┌────────────────┐
│   Client    │────▶│  Pulse Gateway   │────▶│  auth-service  │
│ (Web/Mobil) │     │  (Spring Cloud)  │     │  (WebFlux)     │
└─────────────┘     └──────────────────┘     └───────┬────────┘
                                                     │
                              ┌───────────┬──────────┼──────────┐
                              │           │          │          │
                         ┌────▼───┐  ┌────▼───┐ ┌───▼────┐ ┌───▼─────┐
                         │Keycloak│  │ Redis  │ │  Kafka │ │PostgreSQL│
                         │ (IAM)  │  │(Cache) │ │(Events)│ │  (DB)   │
                         └────────┘  └────────┘ └────────┘ └─────────┘
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

## 🚀 Kullanıcı Akışları (User Journeys)

### Akış 1: Yeni Kayıt ve Aktivasyon

```
┌────────┐                        ┌────────────┐    ┌────────┐
│ Client │                        │auth-service│    │  Mail  │
└───┬────┘                        └─────┬──────┘    └───┬────┘
    │                                   │               │
    │  POST /auth/register              │               │
    │──────────────────────────────────▶│               │
    │  200 "User registered"            │               │
    │◀──────────────────────────────────│               │
    │                                   │ Kafka -> OTP  │
    │                                   │──────────────▶│
    │                                   │               │──▶ 📧
    │  POST /auth/verify {email, token} │               │
    │──────────────────────────────────▶│               │
    │  200 "Email verified"             │               │
    │◀──────────────────────────────────│               │
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

## 📖 Kaynaklar

- **Yanıt Modeli ve Hata Yapıları:** [response-model.md](./api/response-model.md)
- **Hata Kodları ve Validation Kuralları:** [error-codes.md](./api/error-codes.md)
- **Auth Endpoint Detayları:** [auth-endpoints.md](./api/auth-endpoints.md)

---

> **Geliştirici Notu:** Backend API yapısı şu an yalnızca `auth-service` modülünü kapsamaktadır.
> `profile-service`, `timeline-service` ve diğer servisler eklendikçe bu dokümantasyon genişletilecektir.
> İstemci geliştirirken bu Base URL'i ve `ZeabayApiResponse` wrapper yapısını merkezi bir HTTP
> interceptor aracılığıyla çözmeniz önerilir.

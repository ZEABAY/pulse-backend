# Pulse Platform Genel Bakış

Pulse, mikroservis mimarisinde çalışan bir backend platformudur. Frontend ve mobil uygulamalar bu servisler üzerinden API çağrıları yapar.

---

## Mimari

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Web / Mobile   │────▶│   auth-service   │     │  user-service   │
│   (React, etc.)  │     │   Port: 8081     │     │  Port: 8082     │
└─────────────────┘     └────────┬────────┘     └────────┬────────┘
                                 │                       │
                                 ▼                       ▼
                          ┌─────────────┐          ┌─────────────┐
                          │  Keycloak   │          │ PostgreSQL │
                          │  PostgreSQL │          │   Kafka    │
                          └─────────────┘          └─────────────┘
```

---

## Servis Listesi

| Servis | Port | Base URL | Swagger | Durum |
|--------|------|----------|---------|-------|
| auth-service | 8081 | `http://localhost:8081` | [Swagger UI](http://localhost:8081/swagger-ui.html) | ✅ Aktif |
| user-service | 8082 | `http://localhost:8082` | — | 📋 Planlanıyor |

---

## Ortak Özellikler

- **Response formatı:** Tüm servisler `ZeabayApiResponse<T>` zarfı kullanır (logout hariç)
- **Kimlik doğrulama:** JWT Bearer (Keycloak realm `pulse`)
- **API versiyonu:** `/api/v1/`
- **JSON:** camelCase, ISO-8601 tarih, ID'ler string (TSID)
- **Swagger:** Her serviste `/swagger-ui.html`
- **OpenAPI:** Her serviste `/v3/api-docs`

---

## Geliştirme Ortamı

| Özellik | Değer |
|---------|-------|
| Local base | `http://localhost:{port}` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` |
| Actuator health | `http://localhost:8081/actuator/health` |

---

## Doküman Bağlantıları

- [Ortak kurallar](./00-ortak.md) — Response formatı, hata kodları, auth, TypeScript/Flutter
- [Auth Service](./auth-service.md) — Kayıt, giriş, token, doğrulama
- [User Service](./user-service.md) — *(planlanıyor)*

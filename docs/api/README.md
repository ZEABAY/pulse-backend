# Pulse API Dokümantasyonu

Frontend (React, Vue, Angular) ve mobil (React Native, Flutter, iOS, Android) geliştiriciler için Pulse backend API referansı.

> **Backend geliştiriciler:** Projeyi ilk kez ayağa kaldırmak için [setup/GETTING_STARTED.md](../setup/GETTING_STARTED.md) dokümanına bakın. Tüm bilgiler bu klasörde toplanmıştır.

---

## Doküman Yapısı

| Dosya | İçerik |
|-------|--------|
| [pulse-overview.md](./pulse-overview.md) | Platform mimarisi, servis listesi |
| [00-ortak.md](./00-ortak.md) | Response formatı, hata kodları, auth, Public/Protected paths, TypeScript/Flutter tipleri |
| [auth-service.md](./auth-service.md) | Auth Service — kayıt, giriş, token, doğrulama (tüm endpoint detayları) |
| [user-service.md](./user-service.md) | User Service — *(planlanıyor)* |

---

## Genel Bilgiler

### Base URL ve Portlar

| Servis | Port | Base URL | Swagger UI |
|--------|------|----------|------------|
| auth-service | 8081 | `http://localhost:8081` | [Swagger](http://localhost:8081/swagger-ui.html) |

### API Versiyonu

Tüm endpoint'ler `/api/v1/` prefix'i ile başlar.

### İçerik Türü

- **Request:** `Content-Type: application/json`
- **Response:** `Content-Type: application/json`
- **Karakter seti:** UTF-8

### Trace ID

Her istekte tekil bir `traceId` üretilir veya gelen header'lardan alınır:

| Header | Açıklama |
|--------|----------|
| `traceparent` | W3C Trace Context — varsa `traceId` segmenti kullanılır |
| `X-Trace-Id` | Özel trace ID — max 64 karakter, alphanumeric + `_-` |

Response header olarak her zaman `X-Trace-Id` dönülür. JSON body içinde de `traceId` alanı bulunur.

### Swagger / OpenAPI

- **Swagger UI:** `http://localhost:8081/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8081/v3/api-docs`
- **Bearer Auth:** Swagger UI'da "Authorize" ile JWT girilebilir (bearerAuth scheme)

### Actuator Endpoints

| Endpoint | Açıklama |
|----------|----------|
| `/actuator/health` | Sağlık kontrolü |
| `/actuator/info` | Uygulama bilgisi |
| `/actuator/prometheus` | Prometheus metrics |

---

## Hızlı Başlangıç

### İlk İstek (Login)

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Pass1234!"}'
```

### Frontend Örnek (fetch)

```typescript
const res = await fetch('http://localhost:8081/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'testuser', password: 'Pass1234!' }),
});
const json = await res.json();
if (json.success) {
  const { accessToken, refreshToken, expiresIn } = json.data;
  // Token'ı sakla, sonraki isteklerde Authorization header kullan
} else {
  console.error(json.error.code, json.error.message);
}
```

---

## Ortak Kurallar

- **Response:** Tüm yanıtlar (logout hariç) `ZeabayApiResponse<T>` zarfında
- **Auth:** `Authorization: Bearer <accessToken>`
- **Tarih:** ISO-8601 (`2026-03-05T10:00:00.123456789Z`)
- **ID:** String (TSID, 13 karakter) — JavaScript `Number` overflow önlemi
- **Property isimleri:** camelCase
- **Null:** `null` alanlar dahil edilir (explicit contract)

Detaylar için [00-ortak.md](./00-ortak.md).

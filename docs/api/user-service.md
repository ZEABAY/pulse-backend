# User Service API

> ⚠️ **Planlama aşamasında** — Bu servis henüz implemente edilmedi. Endpoint'ler ve veri modelleri taslak niteliğindedir.

---

## Genel Bilgiler (Tahmini)

| Özellik | Değer |
|---------|-------|
| Base URL | `http://localhost:8082` *(tahmini)* |
| API Prefix | `/api/v1/users` |
| Swagger UI | http://localhost:8082/swagger-ui.html |

---

## Planlanan Endpoint'ler

| Method | Path | Auth | Açıklama |
|--------|------|------|----------|
| GET | `/api/v1/users/me` | ✅ | Giriş yapan kullanıcı bilgisi |
| GET | `/api/v1/users/{id}` | ✅ | Kullanıcı detayı |
| PATCH | `/api/v1/users/me` | ✅ | Profil güncelleme |
| ... | ... | ... | ... |

---

## Notlar

- User Service eklendiğinde bu doküman güncellenecektir.
- Ortak response formatı ve hata kodları [00-ortak.md](./00-ortak.md) dosyasında tanımlıdır.

# ADR-001: Pulse Platform Temel Mimari Kararları

**Tarih:** 2026-02-08  
**Güncelleme:** 2026-03-05  
**Yazar:** Zeynel Abiddin Aydar  
**Durum:** Kabul Edildi (Accepted)

---

## 1. Bağlam (Context)

Pulse, Twitter benzeri bir sosyal medya platformudur. Backend, birden fazla bağımsız mikroservis olarak geliştirilecektir. Her mikroservis bağımsız deploy edilebilmeli, ölçeklenebilmeli ve geliştirilebilmelidir.

Bu ADR, tüm servisler için geçerli olan temel mimari kararları ve standartları belgeler.

---

## 2. Kararlar

### 2.1 Repo Stratejisi

- **Multi-repo:** Monorepo kullanılmayacaktır.
- `pulse-backend` → tüm mikroservisler + `zeabay-common` (git submodule)
- `pulse-web` → Next.js frontend (ortak kod `src/lib` içinde)
- `pulse-infra` → Docker Compose, deploy manifestleri (opsiyonel, gerektiğinde ayrılır)

**Gerekçe:** Servisler bağımsız versiyonlanabilmeli ve deploy edilebilmelidir. Monorepo build sürelerini ve bağımlılık karmaşasını artırır.

---

### 2.2 Teknoloji Stack

| Katman | Teknoloji | Versiyon |
|---|---|---|
| Backend dili | Java | 25 |
| Framework | Spring Boot | 4.0.x |
| Reaktif runtime | Spring WebFlux + Project Reactor | Spring Boot yönetimli |
| Veritabanı erişimi | R2DBC (reaktif) | Spring Boot yönetimli |
| Schema yönetimi | Flyway (JDBC üzerinden) | 10.x |
| IAM | Keycloak | 26.x |
| Mesajlaşma | Apache Kafka | 3.9.x (KRaft, ZooKeeper'sız) |
| Cache | Redis | 7.x |
| Arama | Elasticsearch | (ileriki sprint) |
| Service Discovery | Consul | (ileriki sprint) |
| API Gateway | Spring Cloud Gateway | (ileriki sprint) |
| Observability | Micrometer Tracing + OpenTelemetry → Jaeger | Spring Boot yönetimli |
| Metrikler | Prometheus + Grafana | — |

**Blocking IO yasak:** JPA/Hibernate kullanılmaz. Tüm DB erişimi R2DBC üzerinden reaktif olacaktır.

**Flyway istisnası:** Flyway JDBC gerektirir; R2DBC bununla uyumsuzdur. `zeabay-outbox` modülü, `spring.flyway.enabled=true` olduğunda Flyway'i çalıştırır: önce `outbox_events` tablosunu oluşturur, sonra JDBC URL'ini `spring.r2dbc.url`'den türetip (`r2dbc:` → `jdbc:`) Flyway migrate çalıştırır (`baselineOnMigrate` ile). Outbox kullanmayan servisler kendi Flyway konfigürasyonunu yapar.

---

### 2.3 zeabay-common — Merkezi Altyapı Platformu

Servisler arası tekrar eden kod ve standartlar `zeabay-common` git submodule'ünde toplanır. Spring Boot AutoConfiguration mekanizmasıyla sıfır konfigürasyonla aktive olur.

**Kapsam kuralı:** Yalnızca cross-cutting ve domain-agnostic bileşenler. Sosyal medya domain kuralları (timeline algoritması, follow graph vb.) common'a alınmaz.

**Modüller:**

| Modül | Sorumluluk |
|---|---|
| `zeabay-bom` | BOM — tüm modül versiyonlarını tek noktadan yönetir |
| `zeabay-core` | TSID generator (`TsidGenerator`), `ErrorCode`, `BusinessException`, `ZeabayConstants` |
| `zeabay-webflux` | CORS, global exception handler, TraceId filtresi, MDC hook, WebClient, `ZeabayApiResponse` |
| `zeabay-openapi` | Swagger UI + JWT Bearer kutusu otomatik konfigürasyonu |
| `zeabay-ops` | Actuator/Prometheus varsayılanları (`EnvironmentPostProcessor`) |
| `zeabay-logging` | `@Loggable` AOP — Mono/Flux farkında; giriş/çıkış/hata loglama |
| `zeabay-r2dbc` | `BaseEntity` (TSID + audit alanları), `BeforeConvertCallback`, `@EnableR2dbcAuditing`. BaseEntity için `zeabayTsidBeforeConvertCallback`; BaseEntity dışındaki entity'ler (örn. `AuthVerificationToken`) için `zeabayGenericTsidBeforeConvertCallback` ile `@Id Long` alanına TSID atanır |
| `zeabay-security` | `SecurityWebFilterChain` varsayılanı, `ReactiveAuditorAware`, güvenlik özellikleri |
| `zeabay-validation` | `ZeabayValidator.validate()` programatik Bean Validation |
| `zeabay-kafka` | `KafkaTemplate`, producer/consumer factory, `BaseEvent`, domain event sınıfları |
| `zeabay-outbox` | Transactional Outbox pattern: `OutboxEvent`, `OutboxPublisher`, `V0__outbox_events.sql` ile tablo init. `spring.flyway.enabled=true` ise Flyway migration ile, değilse ConnectionFactoryInitializer ile oluşturulur |
| `zeabay-keycloak` | Keycloak Admin SDK wrapper + token endpoint WebClient |

**Yayın stratejisi:** SNAPSHOT sürümler local `~/.m2` veya GitHub Packages. Release sürümler Maven Central'a (`com.zeabay` namespace).

---

### 2.4 Entity ID Standardı: TSID

- **DB:** `BIGINT` (64-bit, zaman sıralı)
- **API response / Kafka event payload:** `String` (13 karakter lowercase Crockford Base32)

**Neden String API'de:** JavaScript `Number` tipi maksimum `2^53 - 1 ≈ 9×10^15` tam sayıyı güvenli temsil edebilir. TSID değerleri 2026 itibarıyla `~8×10^17` mertebesindedir — doğrudan JSON `number` olarak gönderilirse frontend'de son haneler bozulur.

**Generator:** `TsidGenerator.newLongId()` (DB için), `TsidGenerator.newId()` veya `TsidCreator.getTsid().toString().toLowerCase()` (API/event için). `zeabay-r2dbc` modülünde `BeforeConvertCallback` ile `BaseEntity` ve uyumlu entity'lere otomatik TSID atanır.

---

### 2.5 Trace ID Standardı

Her inbound HTTP isteğinde tekil bir `traceId` belirlenir. Öncelik sırası:

1. `traceparent` header (W3C Trace Context) — varsa `traceId` segmenti (lowercase hex, 32 karakter) alınır
2. `X-Trace-Id` header — varsa sanitize edilir (max 64 karakter, alphanumeric + `_-`)
3. Hiçbiri yoksa yeni UUID üretilir (tire olmadan)

**Kurallar:**
- Response header olarak her zaman `X-Trace-Id` dönülür
- `traceId` Reactor Context'e yazılır (`ZeabayConstants.TRACE_ID_CTX_KEY`)
- `MdcLifter` hook'u ile her log operasyonunda MDC'ye kopyalanır → WebFlux thread geçişlerinde bile loglar traceId içerir
- Outbound WebClient çağrılarında `X-Trace-Id` otomatik propagate edilir
- Kafka event'lerinde (`BaseEvent.traceId`) ve `outbox_events.trace_id` kolonunda da taşınır

---

### 2.6 API Yanıt Zarfı

Tüm başarılı ve hatalı API yanıtları `ZeabayApiResponse<T>` ile sarmalanır:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00Z"
}
```

Hata yanıtlarında `error` alanı zorunlu alanlar içerir:
- `code` — standart hata kodu (`ErrorCode` enum'dan)
- `message` — insan okunabilir açıklama
- `path` — request path
- `timestamp`
- `validationErrors` — alan bazlı validasyon hataları (varsa)

**ErrorCode → HTTP durum eşlemesi (`ZeabayGlobalExceptionHandler`):**

| ErrorCode | HTTP |
|---|---|
| NOT_FOUND | 404 |
| USER_ALREADY_EXISTS | 409 |
| UNAUTHORIZED | 401 |
| FORBIDDEN | 403 |
| INTERNAL_ERROR | 500 |
| VALIDATION_ERROR, BAD_REQUEST, diğerleri | 400 |

---

### 2.7 IAM: Keycloak

Kullanıcı kimlik yönetimi Keycloak üzerinden yapılır. Auth-service kendi credential tablosu tutmaz; Keycloak master kaynaktır.

**Kayıt akışı:**
- Keycloak Admin SDK ile kullanıcı oluşturulur
- `auth_users` tablosuna `keycloak_id` + `status` kaydı yazılır
- Kayıt sonrası varsayılan realm rolü (`user`) Keycloak Admin API ile atanır

**Login/Refresh:** Keycloak token endpoint'ine proxy.

**Email doğrulama:** Keycloak'ta `email_verified=true` güncellenir; `auth_users.status` → `ACTIVE`.

**Logout:** Keycloak Admin SDK ile kullanıcının tüm session'ları sonlandırılır.

**RBAC — Keycloak-native:**
- Rol tanımları Keycloak realm'da (`realm-pulse-import.json`): `user`, `admin`
- Lokal rol tabloları yok; Keycloak tek kaynak (single source of truth)
- JWT claim `realm_access.roles` → Spring Security authorities (`ROLE_USER`, `ROLE_ADMIN`)
- Servisler `@PreAuthorize("hasRole('USER')")` ile metod seviyesinde yetkilendirme yapabilir

**Servis hesabı:** `pulse-backend-client` service account'una `realm-management` üzerinden `manage-users` ve `view-users` rolleri verilir. Kurulum: `infra/local/keycloak/setup-keycloak.sh` (içeride `assign-service-account-roles.sh` çağrılır).

---

### 2.8 Transactional Outbox Pattern

2PC (Two-Phase Commit) kullanılmaz. Servisler arası eventual consistency Outbox + idempotent consumer + DLQ ile sağlanır.

**Akış:**
1. Servis, DB'ye iş verisi + `outbox_events` tablosuna event kaydını aynı transaction'da yazar
2. `OutboxPublisher` (scheduled, default `zeabay.outbox.polling-interval-ms` ms) pending event'leri Kafka'ya gönderir
3. Başarı → `status=PUBLISHED`, hata → `retry_count++`, max retry'da `status=FAILED`

**Tablo yönetimi:** `outbox_events` tablosu `zeabay-outbox` modülü tarafından `V0__outbox_events.sql` ile oluşturulur (Flyway açıkken migration, kapalıyken ConnectionFactoryInitializer). Servisler kendi Flyway migration'larında bu tabloyu tanımlamaz.

**Kullanılan event'ler (auth-service):**

| Event | Topic | Consumer (ileriki sprint) |
|---|---|---|
| `UserRegisteredEvent` | `pulse.auth.user-registered` | `user-profile-service` |
| `EmailVerificationRequestedEvent` | `pulse.auth.email-verification` | `mail-service` |

**Edge case'ler:**
- Kafka send başarısız → retry (max 3), sonra `FAILED`
- Duplicate delivery → consumer tarafında `inbox_events` ile idempotency (ileriki sprint)
- Partition key → `aggregateId` (AuthUser ID)

---

### 2.9 Güvenlik Varsayılanları

- `actuator/**` → public
- `v3/api-docs/**`, `swagger-ui/**`, `swagger-ui.html`, `webjars/**` → public
- Auth endpoint'leri (`/api/v1/auth/register`, `/login`, `/verify`, `/refresh`) → public
- `/api/v1/auth/logout` → Bearer JWT gerektirir
- Tüm diğer endpoint'ler → Bearer JWT gerektirir

JWT doğrulama: Keycloak JWKS endpoint'inden (`{keycloak.auth-server-url}/realms/{realm}/protocol/openid-connect/certs`) Spring Security OAuth2 Resource Server ile yapılır.

Rol çıkarımı: JWT `realm_access.roles` claim'i `ReactiveJwtAuthenticationConverter` ile `SimpleGrantedAuthority` olarak map edilir (`ROLE_` prefix ile).

---

## 3. Sonuçlar

### Olumlu
- Her mikroservis bağımlılıklarını ekleyerek trace/log/hata/ops standartlarını sıfır konfigürasyonla kazanır
- TSID sayesinde DB insert sıralaması ve index performansı UUID'ye göre üstündür
- Outbox garantisi: uygulama çökmüş olsa bile event kaybı yoktur
- Keycloak-native RBAC ile rol yönetimi tek merkezde; lokal tablo senkronizasyonu gerekmez

### Dikkat Edilmesi Gerekenler
- `zeabay-common` değişiklikleri tüm servisleri etkiler; semantic versioning ve geriye uyumluluk zorunludur
- Keycloak Admin SDK blocking'dir; tüm çağrılar `Schedulers.boundedElastic()` üzerinde çalıştırılır
- `ZeabayCorsAutoConfiguration` (`zeabay-webflux`) development amaçlıdır (`allowedOriginPatterns: *`); production'da API Gateway seviyesinde kısıtlanacaktır

---

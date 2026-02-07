# ADR-0001 — zeabay-common Foundation: Modules, TraceId, TSID Standard

- Status: ACCEPTED
- Date: 2026-02-07
- Owners: Pulse backend team (Zeynel)

## Context

Pulse backend microservice mimarisi event-driven ve WebFlux (reactive) zorunluluğu ile başlayacak.
Cross-cutting ihtiyaçlar (trace/correlation, hata standardı, ID standardı, ops, OpenAPI) her serviste tekrar edeceği
için
`zeabay-common` altında modüler bir “platform starter” yaklaşımı seçiyoruz.

Bu ADR; modül sınırlarını, TraceId standardını ve TSID format standardını tanımlar.

## Decision

### 1) Module boundaries (zeabay-common)

`zeabay-common` şu modüllerden oluşur:

- `zeabay-common-core-api`
    - ApiResponse / ErrorResponse / ValidationError
    - ErrorCode dictionary + BusinessException
- `zeabay-common-webflux-core`
    - Reactive global exception handling (WebFlux)
    - TraceId extraction + response header
    - Reactor Context propagation
    - MDC enrichment (structured logging için)
    - WebClient outbound trace header propagation
- `zeabay-common-tsid`
    - TSID generator + (ileride) parse/codec yardımcıları
- `zeabay-common-ops`
    - Actuator defaults (health/readiness/liveness) + build-info/version
- `zeabay-common-openapi`
    - OpenAPI “bearerAuth” security scheme defaults
- `zeabay-common-spring-boot-starter`
    - Yukarıdaki modülleri “plug-and-play” şekilde bir araya getirir
    - Servisler sadece starter ekleyerek default davranışları alır
    - Override: servis kendi Bean’ini sağlarsa starter geri çekilir (@ConditionalOnMissingBean)

Bu modüler yaklaşım, dependency hijyenini korur ve ileride sadece gereken parçaların seçilmesine izin verir.

### 2) TraceId standard (MUST)

**Amaç:** Her inbound request için tek bir canonical traceId üretmek ve bütün log/response/çağrılarda taşımak.

Canonical anahtarlar:

- Header: `traceparent` (W3C Trace Context) desteklenir.
- Header: `X-Trace-Id` (fallback / internal standard)
- Reactor Context key: `traceId`
- Log MDC key: `traceId`

Kurallar:

1. Eğer request’te `traceparent` varsa:
    - Regex valid ise **trace-id (32 hex)** canonical traceId olarak alınır (lowercase).
2. Aksi halde `X-Trace-Id` varsa:
    - sanitize edilir (CRLF temizlenir, sadece `[a-zA-Z0-9_-]`, max 64).
3. İkisi de yoksa:
    - random 32-hex UUID (dash’siz) üretilir.

Outbound:

- Response header’a her zaman `X-Trace-Id: <traceId>` yazılır.
- WebClient outbound isteklerine `X-Trace-Id` eklenir (zaten varsa override edilmez).
- Loglarda `%X{traceId}` ile görünür olmalıdır (reactive pipeline içinde log atılması önerilir).

### 3) TSID (Entity ID) standard (MUST)

**Amaç:** Bütün aggregate/entity ID’leri TSID olacak.

API boundary:

- ID’ler **String** taşınır.
- Format: **Crockford Base32, 13 karakter, lowercase canonical**
    - Inbound: case-insensitive kabul edilir.
    - Outbound: her zaman lowercase döndürülür.

Generator:

- zeabay-common içinde tek bir generator sağlanır.
- İleride ek yardımcılar:
    - `parse(String) -> long` ve `format(long) -> String` (DB BIGINT kullanımı için)

DB storage guidance (future decision, not enforced in Sprint 0):

- Postgres’te ideal saklama tipi `BIGINT` (8 byte) + API’de string map.
- Bu sayede index/PK daha kompakt olur, JS/KMP tarafında 64-bit numeric riskleri API’de taşınmaz.

## Consequences

- Servisler starter ile hızlı başlar; TraceId ve hata standardı her serviste aynı olur.
- TSID formatı sabitlenir; web/mobile/client tarafında parsing/validation basitleşir.
- İleride Micrometer Tracing + OpenTelemetry eklendiğinde mevcut TraceId standardı üzerine oturtulabilir.

## Alternatives considered

1) UUID v4:

- Index locality kötü, storage büyük (16 byte) ve write amplification yüksek.

2) ULID / KSUID:

- String uzunluğu daha fazla; TSID (64-bit) kadar kompakt değil.

3) TraceId için sadece UUID:

- W3C traceparent ekosistemini ve APM entegrasyonunu zayıflatır.

## Notes

Bu ADR Sprint 0’ın “foundation” kararlarını sabitler.
Yeni ihtiyaçlar çıktıkça ADR’ler ile kararlar güncellenmelidir.

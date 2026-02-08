# ADR 001: Mikroservis Ekosistemi için Ortak Altyapı ve İzlenebilirlik Standartları

**Tarih:** 2026-02-08 04:26 A.M.
**Yazar:** Zeynel Abiddin Aydar  
**Durum:** Kabul Edildi (Accepted)

---

## 1. Bağlam (Context)

“Pulse” projesi kapsamında geliştirilen mikroservislerin (örn. `hello-service`) tutarlı biçimde yönetilmesi, hata
yönetiminin standartlaştırılması ve servisler arası trafiğin uçtan uca izlenebilir olması gerekmektedir.

Hedefler:

- Servisler bağımsız geliştirilebilsin (deploy/scale/iterate).
- Ortak standartlar (kod temizliği, hata formatı, log/trace, ops varsayılanları) her serviste aynı şekilde uygulansın.
- Yeni servis ayağa kaldırma süresi minimum olsun (zero-config / auto-configuration).

---

## 2. Kararlar (Decisions)

### 2.1 Merkezi Ortak Altyapı (`zeabay-common`)

- Tüm servislerin ortak kullanacağı altyapı bileşenleri `zeabay-common-spring-boot-starter` altında toplanacaktır:
    - Loglama & trace enrichment
    - Global hata yakalama & standart API response
    - WebFlux / WebClient filtreleri (trace yayılımı)
    - TSID üretimi standardı
    - Ops varsayılanları (Actuator/Health/Prometheus)

- Servisler bu starter’ı ekleyerek **sıfır konfigürasyon** (Spring Boot Auto-Configuration) ile standart özellikleri
  kazanacaktır.

---

### 2.2 İzlenebilirlik ve Trace Standartları

#### 2.2.1 Trace ID Üretimi ve Taşıma Sırası

Her inbound istek için tekil bir `traceId` belirlenecektir. Aşağıdaki öncelik sırası uygulanacaktır:

1. `traceparent` (W3C Trace Context) başlığından parse edilir (varsa).
2. Yoksa `X-Trace-Id` başlığı kullanılır (varsa).
3. O da yoksa yeni bir `UUID` üretilir.

Kurallar:

- Response header olarak **daima** `X-Trace-Id` dönülecektir.
- Hata yanıtlarında `traceId` alanı **zorunlu** olacaktır.

#### 2.2.2 MDC Entegrasyonu (WebFlux uyumlu)

- `traceId` değeri reactive akışlarda da loglarda görünecek şekilde Reactor Context üzerinden taşınacak,
- `MdcLifter` (veya eşdeğer bir mekanizma) ile log yazımı sırasında MDC’ye yansıtılacaktır.

> Amaç: WebFlux’ta thread değişse bile her log satırında `traceId` bulunması.

#### 2.2.3 Downstream Yayılımı (Propagation)

- `WebClient` ile yapılan tüm outbound çağrılarda `traceId`, otomatik olarak `X-Trace-Id` header’ı ile bir sonraki
  servise aktarılacaktır.
- (İleride OpenTelemetry geçişi düşünülürse) `traceparent` üretimi/propagation desteği ayrıca ele alınabilir; mevcut
  standart en az `X-Trace-Id` zincirini garanti eder.

---

### 2.3 Kimlik Belirleme (Identity) — TSID Standardı

- Veritabanı varlıkları ve iş mantığı seviyesinde benzersiz kimlikler için **TSID (Time-Sorted Unique Identifier)**
  kullanılacaktır.
- ID format standardı:
    - **String** olarak taşınacaktır (API ve event payload’larında stabil olması için).
    - **13 karakter**, **küçük harf**, **canonical** gösterim kullanılacaktır.
- Bu standart, indeksleme performansı ve zaman-sıralı (time-ordered) anahtarlar için tercih edilmiştir.

---

### 2.4 Hata Yönetimi ve API Yanıt Standardı

- Tüm istisnalar merkezi bir handler (örn. `ZeabayGlobalExceptionHandler`) tarafından yakalanacaktır.
- Tüm API yanıtları `ApiResponse<T>` formatında dönecektir.

Hata yanıtlarında şu alanlar **zorunludur**:

- `success: false`
- `error.code` (standart hata kodu)
- `error.path` (request path)
- `traceId`

> Not: Validation hataları da aynı şemaya uymalıdır (field errors dahil).

---

### 2.5 Operasyonel Standartlar (Ops Defaults)

- **Java Sürümü:** Proje Java **25** tabanlı olacaktır.
- **Actuator:** Liveness ve Readiness probları varsayılan olarak açık gelecektir.
- **Metrikler:** Prometheus endpoint’i tüm servislerde standart olarak sunulacaktır.

---

## 3. Sonuçlar (Consequences)

### 3.1 Olumlu Sonuçlar

- Yeni mikroservis oluştururken log/trace/hata yönetimi için ekstra “boilerplate” yazılmasına gerek kalmayacaktır.
- Loglar üzerinden sistem genelinde uçtan uca iz sürme (traceability) mümkün olacaktır.
- TSID kullanımı sayesinde zaman-sıralı anahtarlarla indeks/insert performansı iyileşecektir.

### 3.2 Dikkat Edilmesi Gerekenler

- `zeabay-common` üzerinde yapılacak değişiklikler tüm mikroservisleri etkileyebileceğinden:
    - Geriye uyumluluk gözetilecektir (semver yaklaşımı).
    - Test kapsamı yüksek tutulacaktır (özellikle starter auto-config davranışları için).
    - Mümkün olduğunda “opt-in” özellik bayrakları (properties) ile kontrollü geçiş sağlanacaktır.

---

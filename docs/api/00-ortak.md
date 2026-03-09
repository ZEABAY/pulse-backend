# Ortak API Kuralları

Tüm Pulse servislerinde geçerli response formatı, hata yapısı, kimlik doğrulama ve path erişim kuralları.

---

## 1. Response Zarfı (ZeabayApiResponse)

Her endpoint (logout hariç) aşağıdaki yapıda yanıt döner.

### 1.1 Başarılı Yanıt

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00.123456789Z"
}
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `success` | boolean | `true` |
| `data` | object \| string \| number \| array | Endpoint'e özgü payload |
| `error` | null | Başarıda her zaman `null` |
| `traceId` | string | İstek takip ID'si (debug için) |
| `timestamp` | string | ISO-8601 |

### 1.2 Hata Yanıtı

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "path": "/api/v1/auth/register",
    "timestamp": "2026-03-05T10:00:00.123456789Z",
    "validationErrors": [
      { "field": "email", "message": "Email should be valid" },
      { "field": "username", "message": "Username must be between 3 and 50 characters" }
    ]
  },
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00.123456789Z"
}
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `success` | boolean | `false` |
| `data` | null | Hatada her zaman `null` |
| `error` | object | Hata detayları |
| `error.code` | string | Standart hata kodu |
| `error.message` | string | Kullanıcıya gösterilebilecek mesaj |
| `error.path` | string | İstek path'i |
| `error.timestamp` | string | ISO-8601 |
| `error.validationErrors` | array \| null | Alan bazlı hatalar (sadece VALIDATION_ERROR) |

### 1.3 ValidationError (Alan Bazlı)

```json
{
  "field": "email",
  "message": "Email should be valid"
}
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `field` | string | Hatalı alan adı |
| `message` | string | Hata mesajı |

---

## 2. Hata Kodları ve HTTP Status

| error.code | HTTP | Açıklama |
|------------|------|----------|
| `VALIDATION_ERROR` | 400 | Request body validasyon hatası |
| `BUSINESS_ERROR` | 400 | İş kuralı ihlali |
| `BAD_REQUEST` | 400 | Geçersiz istek |
| `UNAUTHORIZED` | 401 | Token yok/geçersiz veya kimlik doğrulama başarısız |
| `FORBIDDEN` | 403 | Yetki yok |
| `NOT_FOUND` | 404 | Kaynak bulunamadı |
| `USER_ALREADY_EXISTS` | 409 | E-posta zaten kayıtlı |
| `INTERNAL_ERROR` | 500 | Sunucu hatası |

---

## 3. Kimlik Doğrulama

Korumalı endpoint'ler için:

```
Authorization: Bearer <accessToken>
```

- JWT Keycloak realm `pulse` üzerinden doğrulanır
- `realm_access.roles` claim'i Spring Security `ROLE_*` authority'lerine map edilir
- Token süresi dolunca `401` döner → `POST /api/v1/auth/refresh` ile yeni token alınmalı

---

## 4. Public vs Protected Paths

| Path | Erişim |
|------|--------|
| `/api/v1/auth/register` | Public |
| `/api/v1/auth/login` | Public |
| `/api/v1/auth/verify` | Public |
| `/api/v1/auth/refresh` | Public |
| `/api/v1/auth/logout` | **Authenticated** |
| `/actuator/**` | Public |
| `/v3/api-docs/**` | Public |
| `/swagger-ui/**` | Public |
| `/swagger-ui.html` | Public |
| Diğer tüm path'ler | **Authenticated** |

---

## 5. Trace ID

| Header | Açıklama |
|--------|----------|
| `traceparent` | W3C Trace Context (opsiyonel) |
| `X-Trace-Id` | Özel trace ID (opsiyonel) |

Response header'da `X-Trace-Id` her zaman dönülür. Hata raporlarken `traceId` veya `X-Trace-Id` kullanılabilir.

---

## 6. JSON Konvansiyonları

- **Tarih/saat:** ISO-8601 (`2026-03-05T10:00:00.123456789Z`)
- **ID'ler:** String (TSID, Crockford Base32, 13 karakter) — JavaScript `Number` overflow önlemi
- **Property isimleri:** camelCase
- **Null:** `null` alanlar dahil edilir (explicit contract)

---

## 7. TypeScript Tipleri

```typescript
// Ortak response zarfı
interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ErrorResponse | null;
  traceId: string;
  timestamp: string; // ISO-8601
}

interface ErrorResponse {
  code: string;
  message: string;
  path: string;
  timestamp: string;
  validationErrors?: ValidationError[] | null;
}

interface ValidationError {
  field: string;
  message: string;
}

// API client helper
async function apiClient<T>(
  url: string,
  options?: RequestInit & { body?: object }
): Promise<ApiResponse<T>> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options?.headers,
  };
  const token = localStorage.getItem('accessToken'); // veya secure storage
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(url, {
    ...options,
    headers,
    body: options?.body ? JSON.stringify(options.body) : options?.body,
  });

  const json = await res.json();

  if (!res.ok && !json.success) {
    throw new ApiError(json.error, res.status);
  }
  return json;
}

class ApiError extends Error {
  constructor(
    public error: ErrorResponse,
    public status: number
  ) {
    super(error.message);
    this.name = 'ApiError';
  }
}
```

---

## 8. React / React Native Örnek

```typescript
// useAuth hook
const login = async (username: string, password: string) => {
  const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json = await res.json();

  if (!json.success) {
    throw new Error(json.error?.message ?? 'Login failed');
  }

  const { accessToken, refreshToken, expiresIn } = json.data;
  await SecureStore.setItemAsync('accessToken', accessToken);
  await SecureStore.setItemAsync('refreshToken', refreshToken);
  return json.data;
};
```

---

## 9. Flutter / Dart Örnek

```dart
class ApiResponse<T> {
  final bool success;
  final T? data;
  final ErrorResponse? error;
  final String traceId;
  final String timestamp;

  ApiResponse({
    required this.success,
    this.data,
    this.error,
    required this.traceId,
    required this.timestamp,
  });

  factory ApiResponse.fromJson(Map<String, dynamic> json, T Function(dynamic)? fromJsonT) {
    return ApiResponse(
      success: json['success'] as bool,
      data: json['data'] != null ? fromJsonT!(json['data']) : null,
      error: json['error'] != null ? ErrorResponse.fromJson(json['error']) : null,
      traceId: json['traceId'] as String,
      timestamp: json['timestamp'] as String,
    );
  }
}

class ErrorResponse {
  final String code;
  final String message;
  final String path;
  final List<ValidationError>? validationErrors;

  ErrorResponse({required this.code, required this.message, required this.path, this.validationErrors});
  factory ErrorResponse.fromJson(Map<String, dynamic> json) => ErrorResponse(
    code: json['code'],
    message: json['message'],
    path: json['path'],
    validationErrors: (json['validationErrors'] as List?)?.map((e) => ValidationError.fromJson(e)).toList(),
  );
}

class ValidationError {
  final String field;
  final String message;
  ValidationError({required this.field, required this.message});
  factory ValidationError.fromJson(Map<String, dynamic> json) =>
    ValidationError(field: json['field'], message: json['message']);
}
```

---

## 10. Özel Durum: Logout (204 No Content)

`POST /api/v1/auth/logout` başarılı olduğunda **body dönmez**. HTTP status `204 No Content`'tir. ZeabayApiResponse zarfı kullanılmaz.

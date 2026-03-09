# Auth Service API

Kimlik doğrulama, kayıt, giriş, token yönetimi ve e-posta doğrulama endpoint'leri.

---

## Genel Bilgiler

| Özellik | Değer |
|---------|-------|
| Base URL | `http://localhost:8081` |
| API Prefix | `/api/v1/auth` |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |

---

## Endpoint Özeti

| Method | Path | Auth | Açıklama |
|--------|------|------|----------|
| POST | `/api/v1/auth/register` | ❌ | Yeni kullanıcı kaydı |
| POST | `/api/v1/auth/login` | ❌ | Giriş, token al |
| GET | `/api/v1/auth/verify` | ❌ | E-posta doğrulama |
| POST | `/api/v1/auth/refresh` | ❌ | Token yenile |
| POST | `/api/v1/auth/logout` | ✅ | Çıkış |

---

## 1. POST /api/v1/auth/register

Yeni kullanıcı kaydı. Keycloak'ta kullanıcı oluşturulur, e-posta doğrulama linki outbox üzerinden gönderilir.

### Request

```
POST /api/v1/auth/register
Content-Type: application/json
```

**Body:**

```json
{
  "username": "zeyneltest",
  "email": "zeynel@zeabay.com",
  "password": "Pass1234!"
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `username` | string | Evet | 3–50 karakter |
| `email` | string | Evet | Geçerli e-posta formatı |
| `password` | string | Evet | 6–100 karakter |

### Success Response

**Status:** `201 Created`  
**Header:** `Location: /api/v1/auth/users/{id}`

```json
{
  "success": true,
  "data": {
    "id": "0ar8k2p3x7n",
    "username": "zeyneltest",
    "email": "zeynel@zeabay.com"
  },
  "error": null,
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00Z"
}
```

| data alanı | Tip | Açıklama |
|------------|-----|----------|
| `id` | string | TSID, kullanıcı ID'si |
| `username` | string | Kullanıcı adı |
| `email` | string | E-posta |

### Error Responses

| HTTP | error.code | Mesaj örneği |
|------|------------|--------------|
| 400 | VALIDATION_ERROR | Validation failed: email Email should be valid |
| 409 | USER_ALREADY_EXISTS | Email is already registered |

### Frontend Örnek

```typescript
const register = async (username: string, email: string, password: string) => {
  const res = await fetch(`${API_BASE}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password }),
  });
  const json = await res.json();

  if (!json.success) {
    if (json.error?.code === 'USER_ALREADY_EXISTS') {
      throw new Error('Bu e-posta adresi zaten kayıtlı.');
    }
    if (json.error?.validationErrors?.length) {
      const first = json.error.validationErrors[0];
      throw new Error(`${first.field}: ${first.message}`);
    }
    throw new Error(json.error?.message ?? 'Kayıt başarısız');
  }

  return json.data; // { id, username, email }
};
```

---

## 2. POST /api/v1/auth/login

Kullanıcı girişi. Keycloak ile kimlik doğrulama, JWT token döner.

### Request

```
POST /api/v1/auth/login
Content-Type: application/json
```

**Body:**

```json
{
  "username": "zeyneltest",
  "password": "Pass1234!"
}
```

| Alan | Tip | Zorunlu | Kısıtlamalar |
|------|-----|---------|--------------|
| `username` | string | Evet | Kullanıcı adı veya e-posta, max 254 karakter |
| `password` | string | Evet | Max 100 karakter |

### Success Response

**Status:** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 300
  },
  "error": null,
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00Z"
}
```

| data alanı | Tip | Açıklama |
|------------|-----|----------|
| `accessToken` | string | JWT access token |
| `refreshToken` | string | JWT refresh token |
| `expiresIn` | number | Access token süresi (saniye) |

### Error Responses

| HTTP | error.code | Mesaj örneği |
|------|------------|--------------|
| 400 | VALIDATION_ERROR | Validation failed |
| 401 | UNAUTHORIZED | Invalid credentials |
| 401 | UNAUTHORIZED | Email not verified. Please check your inbox and verify your account. |

### Frontend Örnek

```typescript
interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

const login = async (username: string, password: string): Promise<AuthTokens> => {
  const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json = await res.json();

  if (!json.success) {
    throw new Error(json.error?.message ?? 'Giriş başarısız');
  }

  return json.data;
};
```

---

## 3. GET /api/v1/auth/verify

E-posta doğrulama. Mail ile gelen token ile hesabı aktifleştirir.

### Request

```
GET /api/v1/auth/verify?token={token}
```

| Parametre | Tip | Zorunlu | Açıklama |
|-----------|-----|---------|----------|
| `token` | string | Evet | Doğrulama token'ı (mail linkinde) |

**Örnek URL:** `GET /api/v1/auth/verify?token=abc123def456`

### Success Response

**Status:** `200 OK`

```json
{
  "success": true,
  "data": "Email verified successfully",
  "error": null,
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00Z"
}
```

`data` bu endpoint'te string'dir.

### Error Responses

| HTTP | error.code | Mesaj örneği |
|------|------------|--------------|
| 400 | BAD_REQUEST | Verification token has expired |
| 400 | BAD_REQUEST | Token has already been used |
| 404 | NOT_FOUND | Invalid verification token |

### Frontend Örnek

```typescript
// Mail linkinden gelen token ile (örn. ?token=xxx)
const verifyEmail = async (token: string) => {
  const res = await fetch(`${API_BASE}/api/v1/auth/verify?token=${encodeURIComponent(token)}`);
  const json = await res.json();

  if (!json.success) {
    throw new Error(json.error?.message ?? 'Doğrulama başarısız');
  }

  return json.data; // "Email verified successfully"
};
```

---

## 4. POST /api/v1/auth/refresh

Refresh token ile yeni access/refresh token çifti alır.

### Request

```
POST /api/v1/auth/refresh
Content-Type: application/json
```

**Body:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|---------|----------|
| `refreshToken` | string | Evet | Geçerli refresh token |

### Success Response

**Status:** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 300
  },
  "error": null,
  "traceId": "0ar8k2p3x7n",
  "timestamp": "2026-03-05T10:00:00Z"
}
```

### Error Responses

| HTTP | error.code | Mesaj örneği |
|------|------------|--------------|
| 400 | VALIDATION_ERROR | refreshToken is required |
| 401 | UNAUTHORIZED | Invalid or expired refresh token |

### Frontend Örnek (Token Yenileme)

```typescript
const refreshTokens = async (refreshToken: string): Promise<AuthTokens> => {
  const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  const json = await res.json();

  if (!json.success) {
    throw new Error('Oturum süresi doldu. Tekrar giriş yapın.');
  }

  return json.data;
};

// Axios interceptor örneği
axios.interceptors.response.use(
  (res) => res,
  async (err) => {
    if (err.response?.status === 401 && !err.config._retry) {
      err.config._retry = true;
      const refreshToken = await SecureStore.getItemAsync('refreshToken');
      if (refreshToken) {
        const tokens = await refreshTokens(refreshToken);
        await SecureStore.setItemAsync('accessToken', tokens.accessToken);
        await SecureStore.setItemAsync('refreshToken', tokens.refreshToken);
        err.config.headers.Authorization = `Bearer ${tokens.accessToken}`;
        return axios(err.config);
      }
    }
    return Promise.reject(err);
  }
);
```

---

## 5. POST /api/v1/auth/logout

Oturumu sonlandırır. Keycloak'ta kullanıcının tüm session'ları iptal edilir.

### Request

```
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
```

**Body:** Yok

### Success Response

**Status:** `204 No Content`

Body dönmez. ZeabayApiResponse zarfı kullanılmaz.

### Error Responses

| HTTP | Açıklama |
|------|----------|
| 401 | Token yok veya geçersiz |

### Frontend Örnek

```typescript
const logout = async () => {
  const token = await SecureStore.getItemAsync('accessToken');
  await fetch(`${API_BASE}/api/v1/auth/logout`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  await SecureStore.deleteItemAsync('accessToken');
  await SecureStore.deleteItemAsync('refreshToken');
};
```

---

## Akış Diyagramları

### Kayıt Akışı

```
1. POST /register → 201 + { id, username, email }
2. Kullanıcı maildeki linke tıklar
3. GET /verify?token=xxx → 200 + "Email verified successfully"
4. POST /login → 200 + { accessToken, refreshToken, expiresIn }
```

### Giriş / Token Yenileme

```
1. POST /login → accessToken, refreshToken
2. Her istekte: Authorization: Bearer {accessToken}
3. 401 alırsa: POST /refresh { refreshToken } → yeni token'lar
4. Refresh da 401 ise: kullanıcıyı login ekranına yönlendir
```

---

## cURL Örnekleri

```bash
# Register
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"Pass1234!"}'

# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Pass1234!"}'

# Verify
curl "http://localhost:8081/api/v1/auth/verify?token=YOUR_TOKEN"

# Refresh
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"YOUR_REFRESH_TOKEN"}'

# Logout
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

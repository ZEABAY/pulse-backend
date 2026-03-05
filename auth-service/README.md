# Auth Service

[![Java Version](https://img.shields.io/badge/Java-25-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk25-relnotes.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Port](https://img.shields.io/badge/Port-8081-lightgrey.svg)]()

Authentication and identity gateway for the Pulse platform. Manages user registration, email verification, login, token refresh, and logout — delegating credential storage to Keycloak and persisting domain state in PostgreSQL via R2DBC.

---

## 🏗️ Architecture

Hexagonal (Ports & Adapters) over a reactive stack (WebFlux + R2DBC):

```
HTTP Request
     │
     ▼
AuthController          ← API layer  (api/rest)
     │  MapStruct
     ▼
AuthServiceImpl         ← Application layer (application/service)
     │
     ├── AuthUserRepository          ← R2DBC (domain/repository)
     ├── RoleRepository              ← R2DBC
     ├── AuthVerificationTokenRepository ← R2DBC
     ├── OutboxEventRepository       ← zeabay-outbox
     └── IdentityProviderPort        ← Outbound port (application/port)
              │
              ▼
         KeycloakAdapter             ← Infrastructure adapter (infrastructure/adapter)
              │
              ▼
         ZeabayKeycloakClient        ← zeabay-keycloak
              │
              ▼
           Keycloak
```

**Key design decisions:**
- Credentials are never stored locally — Keycloak is the authority.
- Domain events are published via the **Transactional Outbox** pattern (zeabay-outbox), never directly to Kafka.
- Flyway runs on a separate blocking JDBC connection; the rest of the stack is fully non-blocking.
- IDs are **TSID** (64-bit, time-sorted) in the database and **String** at the API level (JavaScript `Number` safe).

---

## 🌐 REST API

Base path: `/api/v1/auth`  
All responses are wrapped in `ZeabayApiResponse<T>`:
```json
{
  "success": true | false,
  "data": { ... } | null,
  "error": null | { "code": "...", "message": "...", "path": "...", "timestamp": "..." },
  "traceId": "4c015ac129fc4fc3950a6e0d699389b2",
  "timestamp": "2026-03-05T14:51:09.118092Z"
}
```

---

### `POST /api/v1/auth/register`

Registers a new user and sends an email verification link via the outbox.

**Request**
```json
{
  "username": "zeyneltest",
  "email": "zeynel@zeabay.com",
  "password": "Pass1234!"
}
```

| Field | Constraint |
|---|---|
| `username` | `@NotBlank`, 3–50 characters |
| `email` | `@NotBlank`, valid e-mail format |
| `password` | `@NotBlank`, 6–100 characters |

**Response — `201 Created`**
```json
{
  "success": true,
  "data": {
    "id": "817408902493679116",
    "username": "zeyneltest",
    "email": "zeynel@zeabay.com"
  }
}
```

**Error cases**

| Condition | HTTP | `error.code` |
|---|---|---|
| Validation failure | 400 | `VALIDATION_ERROR` |
| E-mail already registered | 409 | `USER_ALREADY_EXISTS` |

---

### `POST /api/v1/auth/login`

Authenticates a user and returns a JWT token pair.

**Request**
```json
{
  "username": "zeyneltest",
  "password": "Pass1234!"
}
```

The `username` field accepts both a username and an e-mail address.

**Response — `200 OK`**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "expiresIn": 300
  }
}
```

**Error cases**

| Condition | HTTP | `error.code` |
|---|---|---|
| Unknown user or wrong password | 401 | `UNAUTHORIZED` |
| E-mail not verified yet | 401 | `UNAUTHORIZED` |

---

### `GET /api/v1/auth/verify?token={token}`

Verifies an e-mail address using the one-time token sent during registration.

**Response — `200 OK`**
```json
{
  "success": true,
  "data": "Email verified successfully"
}
```

**Error cases**

| Condition | HTTP | `error.code` |
|---|---|---|
| Token not found | 404 | `NOT_FOUND` |
| Token expired (> 24 h) | 400 | `BAD_REQUEST` |
| Token already used | 400 | `BAD_REQUEST` |

---

### `POST /api/v1/auth/refresh`

Exchanges a refresh token for a new token pair.

**Request**
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response — `200 OK`** — same shape as `/login`.

**Error cases**

| Condition | HTTP | `error.code` |
|---|---|---|
| Invalid or expired refresh token | 401 | `UNAUTHORIZED` |

---

### `POST /api/v1/auth/logout`

Invalidates all sessions for the authenticated user in Keycloak.

**Headers:** `Authorization: Bearer <accessToken>`

**Response — `204 No Content`**

---

## 📦 Domain Model

### `AuthUser` (table: `auth_users`)

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | TSID primary key |
| `keycloak_id` | `VARCHAR(36)` | Keycloak UUID, unique |
| `email` | `VARCHAR(254)` | Unique |
| `username` | `VARCHAR(50)` | Unique |
| `status` | `VARCHAR(24)` | Default `PENDING_VERIFICATION` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | Audit (from `BaseEntity`) |
| `created_by` / `updated_by` | `VARCHAR(50)` | Audit (from `BaseEntity`) |
| `deleted_at` / `deleted_by` | `TIMESTAMPTZ` / `VARCHAR(50)` | Soft delete |

**`AuthUserStatus` enum:** `PENDING_VERIFICATION` → `ACTIVE` → `SUSPENDED` / `DELETED`

### `AuthVerificationToken` (table: `auth_verification_tokens`)

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | TSID, auto-assigned by `zeabay-r2dbc` |
| `user_id` | `BIGINT` | FK → `auth_users` |
| `token` | `VARCHAR(64)` | UUID hex, unique |
| `expires_at` | `TIMESTAMPTZ` | 24 h from creation |
| `used_at` | `TIMESTAMPTZ` | Null until consumed |
| `created_at` | `TIMESTAMPTZ` | |

Domain helpers: `isExpired()`, `isUsed()`.

### `Role` + `AuthUserRole` (tables: `roles`, `auth_user_roles`)

Seeded roles: `ROLE_USER`. Junction table maps users to roles.

---

## 📩 Outbox Events

Two events are published on every successful registration via `zeabay-outbox` (guaranteed at-least-once delivery to Kafka):

| Event | Topic | Consumer |
|---|---|---|
| `UserRegisteredEvent` | `pulse.auth.user-registered` | user-profile-service *(future sprint)* |
| `EmailVerificationRequestedEvent` | `pulse.auth.email-verification` | mail-service *(future sprint)* |

---

## ⚙️ Configuration

### Environment variables

| Variable | Default | Required |
|---|---|---|
| `POSTGRES_USER` | `pulse` | No |
| `POSTGRES_PASSWORD` | `pulse` | No |
| `KEYCLOAK_CLIENT_SECRET` | `pulse-backend-secret` | **Yes (prod)** |
| `KEYCLOAK_ADMIN_USER` | `admin` | No |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | No |

### `application.yml` highlights

```yaml
server:
  port: 8081

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/pulse
    username: ${POSTGRES_USER:pulse}
    password: ${POSTGRES_PASSWORD:pulse}
  flyway:
    enabled: false          # Flyway is managed manually via FlywayConfig (JDBC)
  kafka:
    bootstrap-servers: localhost:9092
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${keycloak.auth-server-url}/realms/${keycloak.realm}/protocol/openid-connect/certs

keycloak:
  auth-server-url: http://localhost:9080
  realm: pulse
  resource: pulse-backend-client
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET:pulse-backend-secret}

zeabay:
  outbox:
    polling-interval-ms: 10000
```

### Public endpoints (no JWT required)

`SecurityConfig` permits without authentication:

```
/api/v1/auth/register
/api/v1/auth/login
/api/v1/auth/verify
/api/v1/auth/refresh
/actuator/**
/v3/api-docs/**
/swagger-ui/**
/webjars/**
```

---

## 🚀 Running Locally

### Prerequisites
- Docker + Docker Compose
- JDK 25
- Maven 3.9+

### 1. Start infrastructure

```bash
cd pulse-backend/infra/local
./reset-local.sh
```

This starts PostgreSQL, Kafka, Keycloak, Jaeger, and Grafana, then runs the Keycloak realm import and role configuration.

### 2. Build and run

```bash
cd pulse-backend
mvn -pl auth-service -am clean package -DskipTests
java -jar auth-service/target/auth-service-*.jar
```

Or from the IDE, run `AuthServiceApplication`.

### 3. Swagger UI

```
http://localhost:8081/swagger-ui.html
```

---

## 🧪 Tests

```bash
cd pulse-backend
mvn -pl auth-service -am test
```

| Test class | Type | What it covers |
|---|---|---|
| `AuthControllerTest` | `@WebFluxTest` slice | HTTP layer: status codes, request validation, error response shapes |
| `AuthServiceImplTest` | Pure unit (Mockito) | Business logic: registration, login, email verification flows |
| `AuthVerificationTokenTest` | Pure unit | Domain methods: `isExpired()`, `isUsed()` |
| `KeycloakAdapterTest` | Pure unit (Mockito) | `WebClientResponseException` → `BusinessException(UNAUTHORIZED)` mapping |

`@WebFluxTest` excludes `ReactiveOAuth2ResourceServerAutoConfiguration` (no Keycloak needed) and imports `ZeabayGlobalExceptionHandler` to validate error response shapes.

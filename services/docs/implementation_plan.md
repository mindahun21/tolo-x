# Tolox Platform — Implementation Plan

> **Principle:** Build blocking dependencies first. No temporary stubs, no orphan services, no incomplete data flows.
> Every stage produces a working, testable subsystem before the next stage begins.

---

## Platform Architecture — Service Dependency Map

```
                                ┌─────────────────────────────────────────────────────────────┐
                                │                    STAGE 0 — INFRASTRUCTURE                  │
                                │                                                             │
                                │   ┌──────────┐  ┌──────────┐  ┌──────┐  ┌──────┐  ┌─────┐ │
                                │   │PostgreSQL │  │  Redis   │  │Kafka │  │Vault │  │ CA  │ │
                                │   │(per-svc)  │  │(cluster) │  │      │  │      │  │(mTLS│ │
                                │   └──────────┘  └──────────┘  └──────┘  └──────┘  └─────┘ │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 1 — FOUNDATION                      │
                                │                                                              │
                                │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐ │
                                │   │ Discovery │  │  Config  │  │   Key    │  │  tolox-    │ │
                                │   │ Service   │  │  Service │  │  Mgmt    │  │  common    │ │
                                │   │          │  │          │  │  Service  │  │  (library) │ │
                                │   └──────────┘  └──────────┘  └──────────┘  └────────────┘ │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 2 — IDENTITY CORE                    │
                                │                                                              │
                                │   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
                                │   │ User         │  │ Client       │  │ API Gateway      │  │
                                │   │ Directory    │  │ Registry     │  │ (refactored)     │  │
                                │   │ Service      │  │ Service      │  │                  │  │
                                │   └──────────────┘  └──────────────┘  └──────────────────┘  │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 3 — AUTH LAYER                      │
                                │                                                              │
                                │   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
                                │   │ Session      │  │Authentication│  │ Consent          │  │
                                │   │ Service      │  │ Service      │  │ Service          │  │
                                │   └──────────────┘  └──────────────┘  └──────────────────┘  │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 4 — AUTHORIZATION                    │
                                │                                                              │
                                │               ┌──────────────────────────┐                   │
                                │               │ Authorization Service    │                   │
                                │               │ (OAuth 2.0 / OIDC core) │                   │
                                │               └──────────────────────────┘                   │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 5 — OBSERVABILITY                    │
                                │                                                              │
                                │               ┌──────────────────────────┐                   │
                                │               │ Audit & Event Service    │                   │
                                │               └──────────────────────────┘                   │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 6 — NOTIFICATIONS                    │
                                │                                                              │
                                │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐ │
                                │   │ Template │  │Preference│  │ Notif    │  │ Notif      │ │
                                │   │ Service  │  │ Service  │  │ API Svc  │  │ Processor  │ │
                                │   └──────────┘  └──────────┘  └──────────┘  └────────────┘ │
                                └────────────────────────┬────────────────────────────────────┘
                                                         │
                                ┌────────────────────────▼────────────────────────────────────┐
                                │                    STAGE 7 — CAMPAIGN + POLISH                │
                                │                                                              │
                                │   ┌──────────────┐  ┌──────────────────────────────────────┐ │
                                │   │ Campaign     │  │ Production hardening, monitoring,    │ │
                                │   │ Service      │  │ load testing, security audit         │ │
                                │   └──────────────┘  └──────────────────────────────────────┘ │
                                └─────────────────────────────────────────────────────────────┘
```

---

## Why This Order?

| Stage | Why It Must Come First |
|:---:|---|
| **0** | Every service needs a database, cache, message bus, and secrets store. Without infra, nothing runs. |
| **1** | Discovery + Config are needed by every other service to boot. Key Management is needed to sign/verify any JWT — internal or external. `tolox-common` library is shared by all services. |
| **2** | User Directory is the single source of truth for identity. Client Registry defines what apps can request tokens. Gateway is the entry point. Nothing auth-related works without users and clients existing. |
| **3** | Sessions must exist before authentication can create them. Authentication must work before authorization can reference it. Consent must exist before authorization can check it. |
| **4** | Authorization Service (OAuth 2.0/OIDC) depends on ALL prior services: it calls User Directory, Client Registry, Session, Consent, and Key Management. It is the capstone of the IdP. |
| **5** | Audit consumes events from all services. It can only be meaningful once the event producers exist. |
| **6** | Notifications depend on Kafka events from Auth, User, Session, etc. The entire IdP must produce events before notifications can consume them. |
| **7** | Campaigns are built on top of the full notification pipeline + user data. Polish comes last. |

---

---

# STAGE 0 — Infrastructure Setup

> **Goal:** Every dependency is running, reachable, and tested before any service code is written.

---

### What to Build

| Component | Details |
|---|---|
| **PostgreSQL instances** | 9 isolated databases: `user_directory_db`, `authentication_db`, `authorization_db`, `session_db`, `consent_db`, `client_registry_db`, `key_management_db`, `audit_db`, `notification_api_db`, `notification_preference_db`, `template_db`, `notification_processor_db`, `campaign_db` |
| **Redis cluster** | Shared Redis for all services — caching, rate limiting, ephemeral state |
| **Kafka** | Already in docker-compose — configure topics: `tolox.auth.*`, `tolox.user.*`, `tolox.session.*`, `tolox.consent.*`, `tolox.token.*`, `notification.*`, `campaign.*` |
| **HashiCorp Vault** | Dev mode for local, HA mode for prod — store DB passwords, JWT signing keys, OAuth provider secrets, internal CA certificates |
| **Internal CA** | Generate root CA + per-service mTLS certificates for gRPC |
| **docker-compose.yml** | Update with all above components |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| Nothing yet. The website does not change in this stage. |

### Stage 0 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 0.1 | Connect to each PostgreSQL instance from host | `psql` connects, can create/drop test table |
| 0.2 | Connect to Redis, SET/GET a key | `redis-cli PING` → `PONG` |
| 0.3 | Produce and consume a Kafka message | `kafka-console-producer` → `kafka-console-consumer` reads it |
| 0.4 | Read a secret from Vault | `vault kv get secret/test` → returns value |
| 0.5 | Generate mTLS cert from Internal CA | `openssl verify -CAfile ca.crt server.crt` → OK |
| 0.6 | All containers healthy | `docker-compose ps` — all services UP, health checks passing |

---

---

# STAGE 1 — Foundation Services

> **Goal:** Service discovery, centralized config, cryptographic key management, and shared library — the base everything else builds on.

---

### 1A. `tolox-common` Shared Library

| Item | Details |
|---|---|
| **Type** | Maven module (JAR), depended on by all services |
| **Contents** | gRPC interceptors (correlation ID propagation, internal JWT injection), Redis configuration, Kafka producer/consumer config, Argon2id password encoder, constant-time string comparison utility, error mapping (gRPC Status ↔ HTTP), base Flyway configuration, health check contracts |

### 1B. Discovery Service *(existing — minimal changes)*

| Item | Details |
|---|---|
| **Current state** | ✅ Working Eureka server |
| **Changes needed** | Add Vault integration for credentials, add `tolox-common` dependency, containerize with mTLS support |

### 1C. Config Service *(existing — enhance)*

| Item | Details |
|---|---|
| **Current state** | ✅ Working Spring Cloud Config with local files |
| **Changes needed** | Move secret values to Vault references (`{vault}` placeholders), add per-service config files for all new services, add `tolox-common` dependency |

### 1D. Key Management Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Generate/store/rotate RS256 asymmetric key pairs, sign tokens, serve JWKS endpoint, verify internal JWTs |
| **Database** | `key_management_db` |
| **Protocol** | gRPC (internal) + HTTP (`/.well-known/jwks.json`) |
| **Vault integration** | Private keys stored in Vault Transit engine |
| **Critical because** | Every JWT (access token, ID token, internal service JWT) is signed by this service. Nothing auth-related works without it. |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Set up the frontend project** if not already done: Next.js (or your preferred framework), TailwindCSS, project structure, routing, basic layout shell. |
| Create a **"Platform Status" page** (`/status`) that pings Discovery Service and shows which services are registered and healthy. This becomes a living dashboard throughout development. |

### Stage 1 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 1.1 | Discovery Service starts + registers itself | Eureka dashboard shows `DISCOVERY-SERVICE` |
| 1.2 | Config Service starts + serves config for each service name | `curl http://localhost:8888/user-service/dev` → returns YAML |
| 1.3 | Config Service resolves Vault secrets | Config response contains actual DB password (not `{vault}` placeholder) |
| 1.4 | Key Management Service generates RS256 key pair | gRPC call `GenerateKeyPair` → returns keyId |
| 1.5 | KMS serves JWKS endpoint | `curl http://localhost:{port}/.well-known/jwks.json` → returns JSON with `keys[]` containing RSA public key |
| 1.6 | KMS signs a test JWT | gRPC call `SignToken({claims})` → returns signed JWT string |
| 1.7 | KMS verifies a signed JWT | gRPC call `VerifyToken({jwt})` → returns claims |
| 1.8 | KMS rejects expired/tampered JWT | `VerifyToken({tamperedJwt})` → INVALID |
| 1.9 | Key rotation works | `RotateKeys` → new key appears in JWKS, old key still present (for validation of in-flight tokens) |
| 1.10 | `tolox-common` builds as Maven module | `mvn install` succeeds, other services can import it |
| 1.11 | **tolo-x.com `/status` page** | Page loads, shows Discovery Service as healthy, other services as "not yet deployed" |

---

---

# STAGE 2 — Identity Core

> **Goal:** Users exist, clients (apps) are registered, and the gateway routes traffic. The data foundation for all auth flows.

---

### 2A. User Directory Service *(refactor existing `user` service)*

| Item | Details |
|---|---|
| **Current state** | Basic JPA entity with `Long id`, `email`, `password`, `roles`, Feign REST endpoints |
| **Changes** | `Long id` → `UUID id`, new schema: `users` + `user_profiles` + `user_addresses` + `social_connections` + `email_verifications` + `password_reset_tokens` + `scim_external_ids`. Replace REST controllers with gRPC service. Keep REST only for SCIM 2.0 endpoints. Add Flyway migrations. Switch password hashing to Argon2id (dual-hash for migration: try Argon2id first, if fail try BCrypt, if BCrypt matches → rehash with Argon2id). Add Kafka producers (`tolox.user.created`, `tolox.user.deleted`, `tolox.user.password_changed`, `tolox.user.suspended`). |
| **Database** | `user_directory_db` (new, isolated) |
| **Data migration** | Migrate existing `app_user` table data to new schema |

### 2B. Client Registry Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Register OAuth 2.0 clients (apps), define scopes, manage secrets, validate clients during auth flows |
| **Database** | `client_registry_db` |
| **Seed data** | Register `tolo-x-website` as a public OAuth client with `openid profile email` scopes, PKCE required, redirect URI `https://tolo-x.com/callback` |
| **Protocol** | gRPC (internal) + REST (Developer Console admin API) |

### 2C. API Gateway *(refactor existing)*

| Item | Details |
|---|---|
| **Current state** | Spring Cloud Gateway with JWT validation, header injection, static internal token |
| **Changes** | **Remove:** JWT validation logic, `X-User-Email`/`X-User-Roles` header injection. **Add:** `X-Correlation-ID` injection (UUID per request), Redis-backed rate limiting (per IP + per client_id), internal service JWT signing (call KMS or use local cached key), route configuration for all new service endpoints. **Keep:** Eureka-based routing, TLS termination. |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Create the user registration page** (`/register`) — form with: name, email, phone (optional), password, confirm password. Amharic/English toggle. Calls User Directory Service (through Gateway) to create user. Shows success → "Check your email to verify." |
| **Create the user profile page** (`/profile`) — protected route. Displays and edits: name, name_am, email, phone, locale, timezone, addresses. Calls User Directory Service gRPC/REST. |
| **Create the Developer Console page** (`/developer`) — protected admin route. Lists registered OAuth clients. Create new client form: name, type (public/confidential), redirect URIs, scopes. Calls Client Registry REST API. |
| The registration page does NOT do login yet — it only creates the user record. Login comes in Stage 3. |

### Stage 2 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 2.1 | User Directory registers with Eureka | Discovery dashboard shows `USER-DIRECTORY-SERVICE` |
| 2.2 | Create user via gRPC | `CreateUser({email, password, name})` → returns UUID |
| 2.3 | Lookup user by email via gRPC | `GetUserByEmail({email})` → returns user with UUID id |
| 2.4 | Password stored as Argon2id hash | DB query shows `$argon2id$` prefix in `password_hash` column |
| 2.5 | Old BCrypt passwords auto-migrate | Login with old BCrypt hash → succeeds → DB now shows Argon2id hash |
| 2.6 | Kafka event on user creation | Consumer on `tolox.user.created` receives event with userId |
| 2.7 | User soft-delete works | Delete user → `deleted_at` set, `status` = `DELETED`, PII anonymized |
| 2.8 | Client Registry creates OAuth client | REST `POST /admin/clients` → client created with `client_id` |
| 2.9 | Client Registry validates client | gRPC `ValidateClient({clientId, redirectUri})` → OK |
| 2.10 | Client secret verification | `AuthenticateClient({clientId, clientSecret})` → Argon2id hash match |
| 2.11 | Gateway routes to User Directory | `curl https://localhost:8222/users/...` → routed correctly |
| 2.12 | Gateway injects correlation ID | Response headers contain `X-Correlation-ID` |
| 2.13 | Gateway rate limiting | 100+ rapid requests → `429 Too Many Requests` after threshold |
| 2.14 | **tolo-x.com `/register`** | Fill form → submit → user created in DB → "Check your email" message displayed |
| 2.15 | **tolo-x.com `/developer`** | Create a test OAuth client → appears in client list → `client_id` and `client_secret` shown once |
| 2.16 | **tolo-x.com `/status`** | Shows Discovery, Config, KMS, User Directory, Client Registry, Gateway as healthy |

---

---

# STAGE 3 — Auth Layer

> **Goal:** Users can log in (password + social + MFA), sessions are managed server-side, and consent is tracked. Still no OAuth tokens — that's Stage 4.

---

### 3A. Session Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | SSO session lifecycle: create, validate, revoke, concurrent session limits, back-channel logout |
| **Database** | `session_db` |
| **Redis** | Session cache (`session:{sessionId}`) |
| **Kafka** | Consumer: `tolox.user.deleted`, `tolox.user.suspended`, `tolox.user.password_changed`. Producer: `tolox.session.revoked` |
| **Must be built before Authentication** | Because Authentication Service creates sessions after successful login — it calls `SessionService.CreateSession()` via gRPC |

### 3B. Authentication Service *(refactor existing `auth` service)*

| Item | Details |
|---|---|
| **Current state** | Monolithic: registration + login + JWT issuance + social login (Google only) |
| **Remove** | User registration (moved to User Directory), JWT token issuance (moved to Authorization Service), direct token validation |
| **Keep + enhance** | Password login (switch BCrypt → Argon2id via `tolox-common`), Google social login |
| **Add** | MFA (TOTP, SMS OTP, email OTP), GitHub + Facebook social login, magic link (passwordless), brute-force protection (Redis rate counters), step-up authentication, trusted device management |
| **New behavior** | On successful login → call `SessionService.CreateSession()` → return `session_id` to caller (NOT a JWT). The session_id is what the Authorization Service uses to issue tokens. |
| **Database** | `authentication_db` (MFA enrollments, login attempts, account lockouts, magic links, social connections, trusted devices) |
| **Kafka** | Producer: `tolox.auth.login.success`, `tolox.auth.login.failed`, `tolox.auth.mfa.triggered`, `tolox.auth.magic_link.requested` |

### 3C. Consent Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Store per-user/per-client consent records, drive the consent screen, handle revocation |
| **Database** | `consent_db` |
| **Kafka** | Consumer: `tolox.user.deleted`. Producer: `tolox.consent.granted`, `tolox.consent.revoked` |
| **Must be built before Authorization** | Because the authorize endpoint must check consent before issuing an auth code |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Create the login page** (`/login`) — email + password form. On success: receives `session_id`, stores it in an HttpOnly cookie. Amharic/English toggle. Shows "incorrect credentials" on failure. |
| **Create the social login buttons** on `/login` — "Continue with Google", "Continue with GitHub", "Continue with Facebook". Redirects to Authentication Service social login endpoints. On callback: session created, cookie set. |
| **Create the MFA setup page** (`/security/mfa`) — protected route. Enroll TOTP (show QR code), register phone for SMS OTP, register email for email OTP. |
| **Create the MFA challenge page** (`/login/mfa`) — shown after password login if MFA is enabled. Enter TOTP code / SMS OTP / email OTP. On success: session created. |
| **Create the magic link page** (`/login/magic-link`) — enter email → "Check your email for a login link". Clicking the link → session created. |
| **Create the consent screen** (`/consent`) — shown during OAuth authorize flow. Displays: app name, app logo, requested scopes in plain language (English + Amharic). "Allow" / "Deny" buttons. |
| **Create the active sessions page** (`/security/sessions`) — protected route. Lists all active sessions: device, IP, location, last active. "Revoke" button per session. "Revoke all other sessions" button. |
| **Note:** Login does NOT issue an OAuth token yet. It creates a server-side session. OAuth token issuance comes in Stage 4. The website can use the session cookie for its own authenticated routes until then. |

### Stage 3 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 3.1 | Session Service creates session | gRPC `CreateSession({userId, ip, userAgent})` → returns sessionId |
| 3.2 | Session cached in Redis | `redis-cli GET session:{sessionId}` → returns session data |
| 3.3 | Session validation | `ValidateSession({sessionId})` → returns userId + metadata |
| 3.4 | Session revocation | `RevokeSession({sessionId})` → Redis key deleted, DB status = REVOKED |
| 3.5 | Concurrent session limit | Create 6 sessions → oldest auto-revoked (limit = 5) |
| 3.6 | Password revokes sessions | Publish `tolox.user.password_changed` → all user sessions revoked |
| 3.7 | **Password login on tolo-x.com** | Go to `/login` → enter email + password → redirected to `/profile` → session cookie set |
| 3.8 | **Wrong password on tolo-x.com** | Enter wrong password → "Invalid credentials" error shown → no session created |
| 3.9 | **Brute-force protection** | 5 failed logins → account locked for 15 min → error message shown |
| 3.10 | **Google social login on tolo-x.com** | Click "Continue with Google" → Google consent → callback → session created → redirected to `/profile` |
| 3.11 | **GitHub social login on tolo-x.com** | Same flow as Google but with GitHub |
| 3.12 | **MFA enrollment on tolo-x.com** | Go to `/security/mfa` → scan QR code with authenticator app → enter code → MFA enrolled |
| 3.13 | **MFA challenge on tolo-x.com** | Login with password → redirected to `/login/mfa` → enter TOTP code → session created |
| 3.14 | **Magic link on tolo-x.com** | Go to `/login/magic-link` → enter email → receive email with link → click link → session created |
| 3.15 | **Active sessions on tolo-x.com** | Go to `/security/sessions` → see current session → click "revoke" on another → that session invalidated |
| 3.16 | Consent Service stores consent | gRPC `RecordConsent({userId, clientId, scopes})` → consent stored |
| 3.17 | Consent Service checks existing | `GetConsent({userId, clientId})` → returns previously granted scopes |
| 3.18 | Consent revocation | `RevokeConsent({userId, clientId})` → Kafka `tolox.consent.revoked` published |
| 3.19 | Kafka events verified | Check `tolox.auth.login.success`, `tolox.auth.login.failed`, `tolox.auth.mfa.triggered` topics have events |

---

---

# STAGE 4 — Authorization (OAuth 2.0 / OIDC)

> **Goal:** The full OAuth 2.0 Authorization Code + PKCE flow works end-to-end. External apps can "Login with Tolox."

---

### 4A. Authorization Service *(new — the largest piece)*

| Item | Details |
|---|---|
| **Purpose** | OAuth 2.0 / OIDC core: `/authorize`, `/token`, `/revoke`, `/introspect`, `/userinfo`, `/.well-known/openid-configuration` |
| **Database** | `authorization_db` |
| **Redis** | Auth codes (`authcode:{code}` → 60s TTL), token revocation list (`revoked:jti:{jti}`), PKCE challenges |
| **Depends on** | Key Management (sign tokens), Client Registry (validate clients), Session (check session), Authentication (redirect to login), Consent (check/prompt consent), User Directory (UserInfo claims) |
| **Token format** | RS256 JWT signed by KMS. Access token: `{iss, sub, aud, exp, iat, jti, scope, client_id}`. ID token: `{iss, sub, aud, exp, iat, nonce, auth_time, amr, name, email, locale}` |
| **Refresh tokens** | Opaque, stored as SHA256 hash in DB. Family-based rotation with reuse detection. |
| **Kafka** | Consumer: `tolox.session.revoked`, `tolox.consent.revoked`. Producer: `tolox.token.issued`, `tolox.token.revoked`, `tolox.token.family_compromised` |

### Full OAuth Flow (visual)

```
┌──────┐     ┌─────────┐     ┌───────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│Client│     │ Gateway │     │  Authz    │     │ Client   │     │  Auth    │     │ Session  │     │ Consent  │
│ App  │     │         │     │  Service  │     │ Registry │     │  Service │     │ Service  │     │ Service  │
└──┬───┘     └────┬────┘     └─────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
   │              │                │                 │                │                │                │
   │ GET /authorize?              │                 │                │                │                │
   │ client_id=X&                 │                 │                │                │                │
   │ redirect_uri=Y&              │                 │                │                │                │
   │ code_challenge=Z&            │                 │                │                │                │
   │ scope=openid+profile         │                 │                │                │                │
   ├─────────────>│               │                 │                │                │                │
   │              ├──────────────>│                 │                │                │                │
   │              │               │                 │                │                │                │
   │              │               │ ValidateClient  │                │                │                │
   │              │               ├────────────────>│                │                │                │
   │              │               │<────────────────┤ OK             │                │                │
   │              │               │                 │                │                │                │
   │              │               │ Check session   │                │                │                │
   │              │               │ cookie          │                │                │                │
   │              │               │                 │                │                │                │
   │              │               │ [no session] → redirect to /login                 │                │
   │              │               │ [has session] → ValidateSession  │                │                │
   │              │               ├───────────────────────────────────────────────────>│                │
   │              │               │<──────────────────────────────────────────────────┤ OK             │
   │              │               │                 │                │                │                │
   │              │               │ GetConsent      │                │                │                │
   │              │               ├────────────────────────────────────────────────────────────────────>│
   │              │               │<───────────────────────────────────────────────────────────────────┤
   │              │               │                 │                │                │                │
   │              │               │ [no consent] → show consent screen                │                │
   │              │               │ [has consent] → issue auth code                   │                │
   │              │               │                 │                │                │                │
   │              │               │ Store auth code │                │                │                │
   │              │               │ in Redis (60s)  │                │                │                │
   │              │               │                 │                │                │                │
   │<─────────────┤<──────────────┤ 302 redirect_uri?code=ABC       │                │                │
   │              │               │                 │                │                │                │
   │ POST /token  │               │                 │                │                │                │
   │ code=ABC&    │               │                 │                │                │                │
   │ code_verifier=V              │                 │                │                │                │
   ├─────────────>├──────────────>│                 │                │                │                │
   │              │               │                 │                │                │                │
   │              │               │ Validate auth   │                │                │                │
   │              │               │ code from Redis │                │                │                │
   │              │               │ Verify PKCE     │                │                │                │
   │              │               │ Sign tokens     │                │                │                │
   │              │               │ (call KMS)      │                │                │                │
   │              │               │                 │                │                │                │
   │<─────────────┤<──────────────┤ {access_token, id_token, refresh_token}           │                │
   │              │               │                 │                │                │                │
```

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Convert the website to a proper OAuth 2.0 client.** The tolo-x.com website is now registered as client `tolo-x-website` in Client Registry (from Stage 2). |
| **Implement Authorization Code + PKCE flow:** On "Login" button click → generate `code_verifier` + `code_challenge` → redirect to `/authorize?client_id=tolo-x-website&redirect_uri=https://tolo-x.com/callback&response_type=code&scope=openid+profile+email&code_challenge=...&code_challenge_method=S256` → after login + consent → callback receives `code` → exchange for tokens via `/token` endpoint. |
| **Create the OAuth callback page** (`/callback`) — receives `code` param, exchanges for tokens, stores `access_token` in memory (not localStorage), `refresh_token` in HttpOnly cookie. Redirects to `/profile`. |
| **Implement token refresh** — when access token expires (check `exp` claim), silently call `/token` with `grant_type=refresh_token`. |
| **Implement logout** — call `/revoke` to revoke refresh token, clear cookies, redirect to home. |
| **Update all protected pages** — use access token (Bearer header) for API calls instead of session cookie. |
| **Create OIDC Discovery page** (`/.well-known/openid-configuration`) — this is served by Authorization Service, not the frontend. But verify the frontend can fetch it. |

### Stage 4 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 4.1 | OIDC Discovery endpoint | `curl /.well-known/openid-configuration` → returns JSON with all endpoints |
| 4.2 | JWKS endpoint | `curl /.well-known/jwks.json` → returns RSA public keys |
| 4.3 | **Full OAuth flow on tolo-x.com** | Click "Login" → redirected to `/authorize` → login page → enter credentials → consent screen → "Allow" → redirected to `/callback?code=...` → tokens received → `/profile` loads with user data |
| 4.4 | PKCE enforcement | Send `/authorize` without `code_challenge` → rejected |
| 4.5 | Auth code single-use | Use same `code` twice at `/token` → second request rejected |
| 4.6 | Auth code expires | Wait 61 seconds → `/token` with expired code → rejected |
| 4.7 | Refresh token rotation | Call `/token` with `refresh_token` → new access + refresh token → old refresh token marked as used |
| 4.8 | Refresh token reuse detection | Reuse an already-used refresh token → entire token family revoked |
| 4.9 | Token revocation | `POST /revoke` with refresh token → token revoked → subsequent refresh fails |
| 4.10 | Token introspection | `POST /introspect` with access token → returns `{active: true, sub, scope, exp}` |
| 4.11 | UserInfo endpoint | `GET /userinfo` with Bearer access token → returns `{sub, name, email, locale}` |
| 4.12 | Consent persistence | Login again with same client → no consent screen (already granted) |
| 4.13 | Consent revocation | Revoke consent on `/profile` → next login shows consent screen again |
| 4.14 | Session revocation → token invalidation | Revoke session → refresh token fails → user must re-login |
| 4.15 | **Create a temporary test mobile app** (Flutter/React Native or just `curl` scripts) that implements the full OAuth 2.0 Authorization Code + PKCE flow against the Tolox Authorization Service. This proves that **any external app** can "Login with Tolox" — not just the website. |
| 4.16 | **Test mobile app OAuth flow** | App opens browser → `/authorize` → login → consent → callback with code → app exchanges code for tokens → app calls `/userinfo` → displays user name |
| 4.17 | Global logout | User logs out on website → session revoked → mobile app's refresh token fails on next refresh |
| 4.18 | Multiple clients | Login on tolo-x.com AND test mobile app → both have independent sessions and tokens → revoking one doesn't affect the other (unless global logout) |
| 4.19 | **tolo-x.com displays user info from ID token** | After login, profile page shows: name, email, profile picture — all from the ID token claims |

---

---

# STAGE 5 — Observability

> **Goal:** Every security-relevant event is captured in an append-only audit log.

---

### 5A. Audit & Event Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Consume all `tolox.*` Kafka events, store in append-only `audit_db` with monthly partitioning, provide admin query API |
| **Database** | `audit_db` (partitioned by month) |
| **Kafka** | Consumer: ALL `tolox.*` topics + `notification.delivery.status` |
| **Features** | Severity classification, correlation ID linking, anomaly detection rules, PDPP compliance export (user data extract), admin REST API for querying |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Create the Admin Audit Log page** (`/admin/audit`) — protected admin-only route. Searchable, filterable table of security events: login attempts, token issuance, consent changes, session revocations. Filter by: user, event type, date range, severity. |
| **Create the User Security Activity page** (`/security/activity`) — protected route. Shows the logged-in user their own security events: recent logins, devices, locations, MFA changes. |

### Stage 5 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 5.1 | Audit Service consumes login events | Login on website → `tolox.auth.login.success` event → appears in audit DB |
| 5.2 | Audit Service consumes token events | OAuth flow → `tolox.token.issued` → audit record |
| 5.3 | Audit idempotency | Replay same Kafka event → no duplicate audit entry |
| 5.4 | Correlation ID chain | Single login flow → all audit events share same `X-Correlation-ID` |
| 5.5 | **tolo-x.com `/admin/audit`** | Admin sees all events, can filter by user email, event type |
| 5.6 | **tolo-x.com `/security/activity`** | User sees their own recent login history |
| 5.7 | PDPP export | `GET /admin/audit/users/{userId}/export` → returns all events for user as downloadable JSON |
| 5.8 | Monthly partitioning | Events from different months are in different partitions → query performance verified |

---

---

# STAGE 6 — Notification System

> **Goal:** The full notification pipeline works: IdP events trigger emails/SMS/push to users, with preference control, template rendering, and delivery tracking.

---

### 6A. Template Service *(refactor existing)*

| Item | Details |
|---|---|
| **Current state** | Partially implemented in `notifications/template-service/` with R2DBC, Redis caching, Thymeleaf renderer |
| **Changes** | Integrate with `tolox-common`, register with Discovery/Config services, add Amharic templates for all IdP notification types, add Handlebars engine support alongside Thymeleaf, migrate to `template_db` |
| **Seed data** | Create templates for ALL notification types listed in the Integration Table (012_notification_system.md): `WELCOME_EMAIL`, `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `MFA_OTP_DELIVERY`, `MAGIC_LINK`, `PASSWORD_CHANGED_ALERT`, `LOGIN_FAILED_ALERT`, `SESSION_REVOKED_ALERT`, `TOKEN_COMPROMISE_ALERT`, `CONSENT_CONFIRMATION` — each with `en` and `am` locale variants for EMAIL + SMS channels |

### 6B. Notification Preference Service *(refactor existing)*

| Item | Details |
|---|---|
| **Current state** | Partially implemented in `notifications/notification-preference-service/` with rule engine, Redis caching |
| **Changes** | Integrate with `tolox-common`, add Kafka consumer for `tolox.user.created` (auto-create channel settings), add Kafka consumer for `tolox.user.deleted` (PDPP cleanup), seed notification type registry for all IdP types, register with Discovery/Config |

### 6C. Notification API Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Single entry point for all notification requests. Validates, normalizes, publishes to Kafka. |
| **Database** | `notification_api_db` |
| **Consumers** | Kafka listener for `tolox.*` events from IdP services (transforms them into notification requests) + gRPC endpoint for direct calls |

### 6D. Notification Processor Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Kafka consumer that executes the delivery pipeline: dedup → preference → render → dispatch |
| **Database** | `notification_processor_db` |
| **Provider adapters** | SendGrid (EMAIL), Twilio (SMS), FCM (PUSH) — start with EMAIL only, add SMS/PUSH after |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Create the Notification Preferences page** (`/settings/notifications`) — protected route. Shows all notification types grouped by category (Security, Transactional, Marketing). Per-type toggle (on/off). Per-channel toggles (Email, SMS, Push). Quiet hours setting. Legal opt-out. Calls Preference Service API. |
| **Create the Template Admin page** (`/admin/templates`) — protected admin route. List templates, create new, create versions, preview rendering, publish versions. |
| **Note:** The actual notification *delivery* (email arriving in inbox, SMS arriving on phone) is tested outside the website — see tests below. |

### Stage 6 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 6.1 | Template Service renders English email | `POST /template/render` with `locale=en` → returns English HTML |
| 6.2 | Template Service renders Amharic email | `POST /template/render` with `locale=am` → returns Amharic HTML |
| 6.3 | Locale fallback | Request `am_ET` → falls back to `am` → returns Amharic |
| 6.4 | Preference Service initializes on user creation | Create user → `tolox.user.created` → 3 channel settings rows appear |
| 6.5 | Preference evaluation (mandatory) | Evaluate `PASSWORD_RESET` → `{deliver: true, reason: MANDATORY}` regardless of user settings |
| 6.6 | Preference evaluation (blocked) | User blocks EMAIL channel → evaluate `WELCOME_EMAIL` via EMAIL → `{deliver: false, reason: CHANNEL_BLOCKED}` |
| 6.7 | Preference evaluation (quiet hours) | Set quiet hours 22:00-07:00 → evaluate at 23:00 → `DELAY` |
| 6.8 | **End-to-end: Password Reset email** | User requests password reset on tolo-x.com → User Directory publishes event → Notification API picks up → Processor renders template → dispatches via SendGrid → **actual email arrives in user's inbox** with correct reset link |
| 6.9 | **End-to-end: MFA SMS OTP** | User logs in with MFA → Authentication Service triggers OTP → Notification System sends SMS via Twilio → **actual SMS arrives on phone** with correct OTP code → user enters code → login completes |
| 6.10 | **End-to-end: Welcome email** | New user registers on tolo-x.com → `tolox.user.created` → WELCOME_EMAIL sent → **email arrives** |
| 6.11 | Preference change on tolo-x.com | Go to `/settings/notifications` → toggle OFF "Login Success" → save → verify Redis cache invalidated → next login does NOT send email |
| 6.12 | Idempotency | Same event published twice → only one email sent |
| 6.13 | DLQ on provider failure | Mock SendGrid returning 500 → after retries → event in DLQ → `delivery_history` status = DLQ |
| 6.14 | **User deletion PDPP cascade** | Delete user → preferences deleted → Redis cache cleared → future events for that user fail-closed |
| 6.15 | **tolo-x.com `/settings/notifications`** | Page loads, shows all notification types, toggles work, changes persist |
| 6.16 | **tolo-x.com `/admin/templates`** | Admin can create template, add version with Amharic body, preview it, publish it |

---

---

# STAGE 7 — Campaign + Production Polish

> **Goal:** Bulk notifications, production hardening, monitoring, load testing, security audit.

---

### 7A. Campaign Service *(new)*

| Item | Details |
|---|---|
| **Purpose** | Bulk notification orchestration: audience segmentation, scheduling, traffic-shaped fan-out |
| **Database** | `campaign_db` |

### 7B. Production Hardening

| Item | Details |
|---|---|
| **Monitoring** | Prometheus metrics + Grafana dashboards for: request latency, error rates, Kafka consumer lag, Redis hit rates, provider success rates |
| **Alerting** | PagerDuty/Slack alerts for: circuit breaker open, DLQ growing, audit anomalies, high error rate |
| **Load testing** | k6 or Gatling scripts simulating 10k concurrent users through OAuth flow |
| **Security audit** | OWASP ZAP scan on Gateway, dependency vulnerability scan, penetration test on auth flows |
| **K8s manifests** | Update all K8s deployment files for 16 services |
| **CI/CD** | GitHub Actions pipeline: build → test → containerize → deploy |

### What to Do on tolo-x.com (Frontend)

| Action |
|---|
| **Create the Campaign Management page** (`/admin/campaigns`) — create campaign, select audience, select template, schedule, execute, monitor progress. |
| **Final UI polish** — responsive design, loading states, error handling, Amharic translations for all pages, accessibility (WCAG 2.1 AA), performance optimization (lazy loading, code splitting). |
| **Landing page** (`/`) — public home page explaining Tolox: "One account for everything." Login/Register CTAs. Amharic/English. |
| **Documentation page** (`/docs`) — public developer documentation for "Login with Tolox" integration guide (OAuth 2.0 PKCE flow, endpoints, scopes, example code). |

### Stage 7 — Verification Tests

| # | Test | Pass Criteria |
|:---:|---|---|
| 7.1 | Campaign creates and schedules | Create campaign → schedule for 1 min from now → auto-executes |
| 7.2 | Campaign fan-out | 1000-user campaign → 1000 individual events in Kafka → 1000 delivery records |
| 7.3 | Campaign rate limiting | Set 50/sec limit → verify events arrive at ~50/sec, not all at once |
| 7.4 | Campaign respects preferences | User has MARKETING blocked → campaign notification for that user → BLOCKED |
| 7.5 | **Load test: 10k OAuth flows** | k6 simulates 10k concurrent authorization code flows → p95 latency < 500ms |
| 7.6 | **Load test: notification burst** | 50k notifications in 1 minute → all processed → p95 delivery < 30s |
| 7.7 | **Security: OWASP scan** | No critical or high vulnerabilities on Gateway |
| 7.8 | **Security: token tampering** | Modified JWT rejected by all resource servers |
| 7.9 | **Security: PKCE downgrade** | Attempt OAuth flow without PKCE → rejected |
| 7.10 | **Security: brute-force** | Automated login attempts → rate limited → account locked |
| 7.11 | **tolo-x.com full journey** | New user: register → verify email → login → MFA setup → logout → login with MFA → consent to test app → test app gets tokens → user changes notification preferences → admin sends campaign → user receives only opted-in notifications |
| 7.12 | **tolo-x.com Amharic** | Switch to Amharic → all pages render correctly in Amharic → notifications arrive in Amharic |
| 7.13 | **tolo-x.com mobile responsive** | All pages functional on 375px viewport |

---

---

## Summary — What Gets Built When

```
STAGE │ SERVICES BUILT/CHANGED                  │ FRONTEND PAGES              │ KEY MILESTONE
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  0   │ Infrastructure (PG, Redis, Kafka,        │ —                           │ "All infra containers UP
      │ Vault, CA, docker-compose)               │                             │  and reachable"
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  1   │ tolox-common, Discovery, Config,         │ /status                     │ "JWKS endpoint returns
      │ Key Management Service                   │                             │  RSA public key"
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  2   │ User Directory, Client Registry,         │ /register, /profile,        │ "User created with UUID,
      │ API Gateway                              │ /developer, /status updated │  OAuth client registered"
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  3   │ Session, Authentication, Consent         │ /login, /login/mfa,         │ "User can log in with
      │                                          │ /login/magic-link,          │  password + Google + MFA,
      │                                          │ /consent, /security/mfa,    │  session created"
      │                                          │ /security/sessions          │
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  4   │ Authorization Service                    │ /callback, logout,          │ "External app can
      │                                          │ token refresh               │  'Login with Tolox'"
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  5   │ Audit & Event Service                    │ /admin/audit,               │ "All events captured
      │                                          │ /security/activity          │  in audit log"
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  6   │ Template, Preference, Notification API,  │ /settings/notifications,    │ "Password reset email
      │ Notification Processor                   │ /admin/templates            │  arrives in inbox"
──────┼──────────────────────────────────────────┼─────────────────────────────┼────────────────────────────
  7   │ Campaign Service, production hardening   │ /admin/campaigns, /,        │ "10k concurrent OAuth
      │                                          │ /docs, Amharic polish       │  flows under 500ms"
```

---

## Total Service Count at Completion

```
╔══════════════════════════════════════════════════════════════════════════╗
║                        TOLOX PLATFORM — 16 SERVICES                    ║
╠════════════════════════╦═════════════════════════════════════════════════╣
║ IdP Core (11)          ║ API Gateway, Discovery, Config, Authorization,║
║                        ║ Authentication, User Directory, Client        ║
║                        ║ Registry, Session, Consent, Key Management,   ║
║                        ║ Audit & Event                                 ║
╠════════════════════════╬═════════════════════════════════════════════════╣
║ Notification (5)       ║ Notification API, Preference, Template,       ║
║                        ║ Processor, Campaign                           ║
╠════════════════════════╬═════════════════════════════════════════════════╣
║ Frontend (1)           ║ tolo-x.com (Next.js)                          ║
╚════════════════════════╩═════════════════════════════════════════════════╝
```

---

## Implementation Best Practices

| Practice | Details |
|---|---|
| **📋 Protobuf-first** | Define `.proto` files before writing service code. They are the contract. |
| **🗄️ Flyway always** | Every schema change is a numbered migration. No manual DDL. |
| **🧪 Test before moving on** | Every stage has a test table above. Do not start the next stage until ALL tests pass. |
| **📊 Metrics from day 1** | Every service exposes `/actuator/prometheus`. Grafana dashboards built per stage. |
| **🔒 Secrets in Vault** | Never hardcode secrets. Not in code, not in YAML, not in env files. |
| **🆔 Correlation IDs everywhere** | Every request gets a UUID. It propagates through gRPC metadata, Kafka headers, and log MDC. |
| **📝 Amharic from day 1** | Every user-facing string has both `en` and `am` variants. Do not bolt it on later. |
| **🔄 CI/CD per service** | Each service has its own build + test + containerize pipeline. Independent deployability. |

# Client Registry Service

---

## What to Implement

The Client Registry Service is the backend for the Tolox Developer Console. It stores all registered OAuth 2.0 clients (apps), their allowed scopes, grant types, redirect URIs, and secrets. Every authorization flow starts with a client validation call to this service. It also defines what scopes exist on the platform.

---

## Schema

### PostgreSQL — client_registry_db

```sql
CREATE TABLE clients (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               VARCHAR(100) UNIQUE NOT NULL,  -- public identifier (e.g., "tolox-erp-prod")
    client_secret_hash      VARCHAR(255),                  -- Argon2id hash. NULL for public clients.
    client_type             VARCHAR(20) NOT NULL,          -- 'confidential' | 'public' | 'machine'
    client_name             VARCHAR(200) NOT NULL,
    client_name_am          VARCHAR(200),                  -- Amharic name
    description             TEXT,
    logo_url                VARCHAR(500),
    owner_user_id           UUID NOT NULL,                 -- developer who registered this app
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clients_client_id ON clients(client_id);

CREATE TABLE client_redirect_uris (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   VARCHAR(100) NOT NULL REFERENCES clients(client_id),
    uri         VARCHAR(2000) NOT NULL,
    CONSTRAINT unique_client_uri UNIQUE (client_id, uri)
);

CREATE TABLE client_scopes (
    client_id   VARCHAR(100) NOT NULL REFERENCES clients(client_id),
    scope       VARCHAR(100) NOT NULL REFERENCES scopes(name),
    PRIMARY KEY (client_id, scope)
);

CREATE TABLE client_grant_types (
    client_id   VARCHAR(100) NOT NULL REFERENCES clients(client_id),
    grant_type  VARCHAR(50) NOT NULL,  -- 'authorization_code' | 'refresh_token' | 'client_credentials'
    PRIMARY KEY (client_id, grant_type)
);

CREATE TABLE client_token_config (
    client_id               VARCHAR(100) PRIMARY KEY REFERENCES clients(client_id),
    access_token_ttl_sec    INT NOT NULL DEFAULT 900,
    refresh_token_ttl_sec   INT NOT NULL DEFAULT 2592000,  -- 30 days
    id_token_ttl_sec        INT NOT NULL DEFAULT 900,
    require_pkce            BOOLEAN NOT NULL DEFAULT true,
    reuse_refresh_tokens    BOOLEAN NOT NULL DEFAULT false, -- always false for security
    back_channel_logout_uri VARCHAR(500),
    userinfo_signed_response_alg VARCHAR(10)  -- null = JSON, 'RS256' = signed JWT
);

-- Platform scope definitions
CREATE TABLE scopes (
    name            VARCHAR(100) PRIMARY KEY,
    display_name    VARCHAR(200) NOT NULL,
    display_name_am VARCHAR(200),
    description     TEXT NOT NULL,
    description_am  TEXT,
    scope_type      VARCHAR(20) NOT NULL,  -- 'user' | 'machine' | 'ai'
    is_sensitive    BOOLEAN NOT NULL DEFAULT false,  -- requires explicit user consent even if previously granted
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Secret rotation history (hashes only, for overlap period)
CREATE TABLE client_secret_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       VARCHAR(100) NOT NULL,
    secret_hash     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,  -- overlap window during rotation
    revoked_at      TIMESTAMPTZ
);
```

### Seed data — Platform Scopes

```sql
INSERT INTO scopes VALUES
  ('openid',        'Sign In',              'ምዝገባ',      'Verify your identity', '...', 'user', false),
  ('profile',       'Profile Information',  'መገለጫ',     'Name, picture, locale', '...', 'user', false),
  ('email',         'Email Address',        'ኢሜይል',     'Your email address', '...', 'user', false),
  ('phone',         'Phone Number',         'ስልክ ቁጥር',  'Your phone number', '...', 'user', true),
  ('address',       'Address',              'አድራሻ',     'Your address', '...', 'user', true),
  ('erp:read',      'Read ERP Data',        '...',        'Read your ERP records', '...', 'user', false),
  ('erp:write',     'Modify ERP Data',      '...',        'Create and update ERP records', '...', 'user', true),
  ('ai:query:erp',  'AI ERP Access',        '...',        'Allow AI to query your ERP data', '...', 'ai', true),
  ('ai:summarize',  'AI Summarization',     '...',        'Allow AI to summarize your data', '...', 'ai', true);
```

---

## Functionality in Detail

### ValidateClient(client_id, redirect_uri, requested_scopes)

Called by Authorization Service on every /authorize request.

1. Look up client by `client_id`. If not found → `CLIENT_NOT_FOUND`
2. Check `status = ACTIVE`. If not → `CLIENT_SUSPENDED`
3. Check `redirect_uri` is in `client_redirect_uris` — **exact string match**
4. Check each requested scope exists in platform `scopes` table
5. Check each requested scope is in `client_scopes` for this client
6. Return `ClientDetails{client_id, client_name, client_name_am, logo_url, allowed_scopes, token_config}`

### AuthenticateClient(client_id, client_secret)

Called by Authorization Service on token endpoint.

1. Look up client
2. If `client_type = public` → no secret expected, return OK (PKCE enforced by AuthZ service)
3. If `client_type = confidential` or `machine`:
   - Check current `client_secret_hash` (Argon2id timing-safe verify)
   - If no match → check `client_secret_history` for valid rotation overlap tokens
   - If still no match → return `INVALID_CLIENT`

### Client Registration (Developer Console API — HTTP REST)

```
POST   /console/clients           — register new client
GET    /console/clients           — list my clients (by owner_user_id)
GET    /console/clients/{id}      — get client details
PUT    /console/clients/{id}      — update client
DELETE /console/clients/{id}      — delete (soft) client
POST   /console/clients/{id}/rotate-secret  — rotate client secret
GET    /console/scopes            — list available platform scopes
```

Secret rotation:
1. Generate new secret: `base64url(secureRandom(32))`
2. Hash with Argon2id
3. INSERT new hash into `client_secret_history` with 24h overlap expiry
4. UPDATE `clients.client_secret_hash` to new hash
5. Return new secret **once** — never retrievable again after this response

---

## Interfaces

### Provides (gRPC — inbound)

```protobuf
service ClientRegistryService {
  rpc ValidateClient(ValidateClientRequest) returns (ClientDetails);
  rpc AuthenticateClient(AuthenticateClientRequest) returns (AuthResult);
  rpc GetClientTokenConfig(ClientIdRequest) returns (TokenConfig);
  rpc GetScopeDetails(ScopeListRequest) returns (ScopeListResponse);
}
```

### Provides (HTTP — Developer Console API)

REST API consumed by the Tolox Developer Console frontend.

---

## Functional Tests

| Test | Expected |
|---|---|
| ValidateClient — valid params | Returns ClientDetails |
| ValidateClient — unknown client_id | CLIENT_NOT_FOUND |
| ValidateClient — wrong redirect_uri | INVALID_REDIRECT_URI |
| ValidateClient — redirect_uri prefix match | INVALID_REDIRECT_URI (must be exact) |
| ValidateClient — scope not allowed for client | SCOPE_NOT_ALLOWED |
| ValidateClient — suspended client | CLIENT_SUSPENDED |
| AuthenticateClient — correct secret | OK |
| AuthenticateClient — wrong secret | INVALID_CLIENT |
| AuthenticateClient — rotated secret within overlap | OK (old secret still valid during overlap) |
| AuthenticateClient — rotated secret after overlap | INVALID_CLIENT |
| Secret rotation — new secret returned once | Secret shown in response, not retrievable again |
| Register client — duplicate client_id | Conflict error |

## Unit Tests

```java
@Test void validateRedirectUri_exactMatch_passes()
@Test void validateRedirectUri_extraSlash_fails()
@Test void validateRedirectUri_queryParam_fails()  // ?foo=bar added = fail
@Test void validateScopes_unknownScope_fails()
@Test void validateScopes_subsetOfAllowed_passes()
@Test void rotateSecret_oldSecretValidDuringOverlap()
@Test void rotateSecret_oldSecretInvalidAfterOverlap()
@Test void authenticatePublicClient_noSecretRequired()
```

---
---

# Session Service

---

## What to Implement

The Session Service manages SSO sessions across all Tolox apps. It creates sessions after authentication, validates sessions on authorization requests, enforces concurrent session limits, and handles global logout propagation via back-channel logout.

---

## Schema

### PostgreSQL — session_db

```sql
CREATE TABLE sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL,
    mfa_level           SMALLINT NOT NULL DEFAULT 0,  -- 0=password only, 1=social, 2=password+MFA
    auth_time           TIMESTAMPTZ NOT NULL,
    ip_address          INET,
    user_agent          TEXT,
    device_fingerprint  VARCHAR(64),
    device_name         VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    last_activity_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at          TIMESTAMPTZ,
    revoke_reason       VARCHAR(100)  -- 'logout' | 'admin' | 'password_change' | 'timeout' | 'user_deleted'
);

CREATE INDEX idx_sessions_user ON sessions(user_id, revoked_at);
CREATE INDEX idx_sessions_expires ON sessions(expires_at) WHERE revoked_at IS NULL;

-- Which OAuth clients are using this session (for back-channel logout)
CREATE TABLE session_clients (
    session_id  UUID NOT NULL REFERENCES sessions(id),
    client_id   VARCHAR(100) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (session_id, client_id)
);
```

### Redis Keys

```
session:{session_id}  → JSON{user_id, mfa_level, auth_time, expires_at}   TTL: session lifetime
```

---

## Functionality in Detail

### CreateSession(user_id, mfa_level, ip, user_agent, device_fingerprint)

1. Check concurrent session limit per user (configurable, default 5)
   - Count active sessions: `SELECT COUNT(*) FROM sessions WHERE user_id=? AND revoked_at IS NULL AND expires_at > now()`
   - If at limit → revoke oldest session (by `last_activity_at`)
2. INSERT session record
3. Cache in Redis with TTL = `expires_at - now`
4. Return `session_id`

### GetSession(session_id)

1. Check Redis first: `GET session:{session_id}`
2. Cache hit → parse JSON, check `expires_at > now`, return
3. Cache miss → query DB, if found and valid → re-cache, return
4. If not found or expired or revoked → return NOT_FOUND

### ValidateSession(session_id) — lightweight check

Same as GetSession but called more frequently. Redis-only check. If not in Redis → treat as invalid (forces DB lookup on cache miss only when needed).

### RegisterClientForSession(session_id, client_id)

Called by Authorization Service when token is issued for a session. Used to build the list of apps to notify on logout.

### RevokeSession(session_id, reason)

1. UPDATE `sessions.revoked_at = now`, `revoke_reason`
2. DELETE from Redis: `DEL session:{session_id}`
3. Load all `client_ids` from `session_clients` for this session
4. Publish `session.revoked` Kafka event with all client_ids (for back-channel logout)

### RevokeAllUserSessions(user_id, reason, except_session_id)

Used on password change, account suspension, account deletion.

1. UPDATE all active sessions for user → revoked
2. DELETE all from Redis
3. Publish `session.revoked` events (one per session, or batch)

### UpgradeSession(session_id, new_mfa_level)

1. UPDATE `sessions.mfa_level`
2. UPDATE Redis cache

### Back-Channel Logout

Authorization Service consumes `session.revoked` from Kafka:
- For each `client_id` in the event, if client has `back_channel_logout_uri` registered (from Client Registry) → WebClient POST logout token (signed JWT per OIDC back-channel logout spec)

Logout token claims:
```json
{
  "iss": "https://idp.tolox.io",
  "sub": "{user_id}",
  "aud": "{client_id}",
  "iat": now,
  "jti": "{uuid}",
  "events": {"http://schemas.openid.net/event/backchannel-logout": {}},
  "sid": "{session_id}"
}
```

---

## Interfaces

### Provides (gRPC — inbound)

```protobuf
service SessionService {
  rpc CreateSession(CreateSessionRequest) returns (SessionResponse);
  rpc GetSession(SessionIdRequest) returns (SessionResponse);
  rpc ValidateSession(SessionIdRequest) returns (SessionValidResponse);
  rpc RevokeSession(RevokeSessionRequest) returns (StatusResponse);
  rpc RevokeAllUserSessions(RevokeUserSessionsRequest) returns (StatusResponse);
  rpc UpgradeSession(UpgradeSessionRequest) returns (StatusResponse);
  rpc RegisterClientForSession(ClientSessionRequest) returns (StatusResponse);
  rpc ListUserSessions(UserIdRequest) returns (SessionListResponse);  // for user dashboard
}
```

### Consumes (Kafka — inbound)

| Topic | Action |
|---|---|
| `tolox.user.deleted` | RevokeAllUserSessions |
| `tolox.user.suspended` | RevokeAllUserSessions |
| `tolox.user.password_changed` | RevokeAllUserSessions except current |

### Provides (Kafka — outbound)

| Topic | When |
|---|---|
| `tolox.session.revoked` | Any session revocation — carries client_ids for back-channel logout |

---

## Functional Tests

| Test | Expected |
|---|---|
| CreateSession | Session in DB and Redis, returns session_id |
| GetSession — cache hit | Returns from Redis, no DB query |
| GetSession — cache miss | Returns from DB, re-populates Redis |
| GetSession — expired | NOT_FOUND |
| GetSession — revoked | NOT_FOUND |
| Concurrent session limit | 6th session creation revokes oldest |
| RevokeSession | Removed from Redis, revoked_at set, Kafka event published with client_ids |
| RevokeAllUserSessions | All active sessions revoked, except_session_id preserved |
| UpgradeSession | mfa_level updated in DB and Redis |
| Back-channel logout | session.revoked consumed → POST to client back_channel_logout_uri |
| ListUserSessions | Returns active sessions for user dashboard |

## Non-Functional Tests

| Test | Target |
|---|---|
| GetSession latency | p99 < 5ms (Redis cache hit) |
| RevokeSession — Redis unavailable | DB write succeeds, Redis error logged, session treated as invalid on next check |
| Concurrent session creation | No duplicate session_ids |

## Unit Tests

```java
@Test void createSession_atConcurrentLimit_revokesOldest()
@Test void getSession_expiredSession_returnsNotFound()
@Test void getSession_revokedSession_returnsNotFound()
@Test void revokeSession_setsRevokedAtAndReason()
@Test void revokeAllUserSessions_exceptSessionIdPreserved()
@Test void upgradeSession_updatesMfaLevelInCache()
```

---
---

# Consent Service

---

## What to Implement

The Consent Service stores and manages user consent records — which user has approved which OAuth client to access which scopes. It drives the consent screen logic and handles consent revocation which triggers downstream token revocation.

---

## Schema

### PostgreSQL — consent_db

```sql
CREATE TABLE consents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    client_id       VARCHAR(100) NOT NULL,
    scopes          TEXT NOT NULL,          -- space-separated approved scopes
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,            -- NULL = permanent until revoked
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(50),            -- 'user' | 'admin' | 'client_deleted'
    CONSTRAINT unique_active_consent UNIQUE (user_id, client_id) DEFERRABLE
);

CREATE INDEX idx_consents_user ON consents(user_id);
CREATE INDEX idx_consents_user_client ON consents(user_id, client_id);

-- Per-scope grants (for granular revocation)
CREATE TABLE consent_scopes (
    consent_id  UUID NOT NULL REFERENCES consents(id),
    scope       VARCHAR(100) NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,
    PRIMARY KEY (consent_id, scope)
);
```

---

## Functionality in Detail

### GetConsent(user_id, client_id, requested_scopes)

1. Look up active consent: `WHERE user_id=? AND client_id=? AND revoked_at IS NULL`
2. If not found → return `CONSENT_REQUIRED`
3. Check that every requested scope is in the stored scopes
4. If any requested scope is new (not previously consented) → return `CONSENT_REQUIRED` (will show consent screen for new scopes only)
5. Check no sensitive scopes need re-consent (is_sensitive=true in Client Registry scopes table)
6. If all scopes covered → return `CONSENT_GRANTED` with consent details

### RecordConsent(user_id, client_id, approved_scopes)

1. Check for existing consent record → if exists, UPDATE (add new scopes, reset `granted_at`)
2. If new → INSERT consent + INSERT consent_scopes for each scope
3. Publish `consent.granted` event to Kafka

### RevokeConsent(user_id, client_id, reason)

1. UPDATE `consents.revoked_at`, `revoke_reason`
2. Publish `consent.revoked` event to Kafka → Authorization Service will revoke all active tokens for this user+client
3. Return OK

### RevokeAllConsentsForClient(client_id)

Called when a client is deleted/suspended.

1. UPDATE all consents for client_id → revoked
2. Publish `consent.revoked` events for each affected user

### ListUserConsents(user_id)

For user dashboard — shows which apps have consent.

Returns: `[{client_id, client_name, client_logo, scopes, granted_at}]`

---

## Interfaces

### Provides (gRPC — inbound)

```protobuf
service ConsentService {
  rpc GetConsent(ConsentRequest) returns (ConsentResponse);
  rpc RecordConsent(RecordConsentRequest) returns (StatusResponse);
  rpc RevokeConsent(RevokeConsentRequest) returns (StatusResponse);
  rpc RevokeAllConsentsForClient(ClientIdRequest) returns (StatusResponse);
  rpc ListUserConsents(UserIdRequest) returns (ConsentListResponse);
}
```

### Provides (Kafka — outbound)

| Topic | When |
|---|---|
| `tolox.consent.granted` | Consent recorded |
| `tolox.consent.revoked` | Consent revoked — carries user_id, client_id |

### Consumes (Kafka — inbound)

| Topic | Action |
|---|---|
| `tolox.user.deleted` | RevokeAllConsents for user |

---

## Functional Tests

| Test | Expected |
|---|---|
| GetConsent — never consented | CONSENT_REQUIRED |
| GetConsent — fully consented | CONSENT_GRANTED |
| GetConsent — new scope added | CONSENT_REQUIRED (show consent screen for new scope only) |
| RecordConsent — new | Consent record created |
| RecordConsent — update | Existing record updated with new scopes |
| RevokeConsent | revoked_at set, consent.revoked published |
| ListUserConsents | Returns all active consents with client details |

## Unit Tests

```java
@Test void getConsent_newScopes_requiresConsent()
@Test void getConsent_allScopesCovered_returnsGranted()
@Test void recordConsent_idempotent_sameScopes()
@Test void recordConsent_addsNewScopes_toExisting()
@Test void revokeConsent_publishesKafkaEvent()
```

---
---

# Key Management Service

---

## What to Implement

The Key Management Service generates, stores, and rotates asymmetric signing key pairs for JWT tokens. It is the only service that holds private keys. All other services call it to sign tokens — they never receive private keys. It serves the JWKS endpoint for public key distribution.

---

## Schema

### Vault (not PostgreSQL — key material never in DB)

```
secret/tolox/keys/current          → {key_id, algorithm, private_key_pem, public_key_pem, created_at}
secret/tolox/keys/previous         → {key_id, algorithm, public_key_pem, expires_at}  -- private key purged
secret/tolox/keys/internal/current → {key_id, private_key_pem, public_key_pem}  -- internal service JWTs
```

### PostgreSQL — key_management_db (metadata only, no key material)

```sql
CREATE TABLE key_metadata (
    key_id          VARCHAR(36) PRIMARY KEY,  -- UUID, used as JWT 'kid' claim
    algorithm       VARCHAR(10) NOT NULL,     -- 'RS256' | 'ES256'
    key_size_bits   INT NOT NULL,
    status          VARCHAR(20) NOT NULL,     -- 'ACTIVE' | 'ROTATED' | 'EXPIRED'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,              -- JWKS stops serving public key after this
    jwks_retire_at  TIMESTAMPTZ               -- public key served in JWKS until this date
);
```

---

## Functionality in Detail

### SignToken(claims, key_type)

`key_type`: `user_token` (RS256, user-facing) or `internal_token` (HMAC or RS256, service-to-service) or `logout_token` (for back-channel logout)

1. Load current active key from Vault
2. Build JWT header: `{"alg":"RS256","kid":"{key_id}","typ":"JWT"}`
3. Base64url encode header + claims, sign with private key using `Nimbus JOSE + JWT` library
4. Return signed JWT string

### GetJWKS()

1. Load current key public component from Vault
2. Load previous key public component from Vault (if `jwks_retire_at > now`)
3. Format as JWKS:
   ```json
   {
     "keys": [
       {"kty":"RSA","kid":"current-key-id","use":"sig","alg":"RS256","n":"...","e":"AQAB"},
       {"kty":"RSA","kid":"previous-key-id","use":"sig","alg":"RS256","n":"...","e":"AQAB"}
     ]
   }
   ```

### RotateKeys()

Triggered manually by admin or on schedule (every 90 days).

1. Generate new RSA 2048-bit key pair (`KeyPairGenerator.getInstance("RSA")`)
2. New key_id = new UUID
3. Write new key pair to Vault at `secret/tolox/keys/current`
4. Move old current public key to `secret/tolox/keys/previous` with `jwks_retire_at = now + 48h`
5. Purge old private key immediately (never stored after rotation)
6. UPDATE `key_metadata`: old key → ROTATED, new key → ACTIVE
7. JWKS now serves both keys — allows token holders with old tokens to still validate

### VerifyInternalToken(token)

Used by services to validate incoming internal service JWTs.

1. Parse JWT, extract `kid`
2. Load matching key from Vault
3. Verify signature, `iss=tolox-internal`, `aud=<this-service-name>`, `exp`
4. Check `jti` not in Redis seen-list (replay prevention)
5. Add `jti` to Redis seen-list TTL=65s
6. Return verified claims or error

---

## Interfaces

### Provides (gRPC — inbound)

```protobuf
service KeyManagementService {
  rpc SignToken(SignTokenRequest) returns (SignedTokenResponse);
  rpc VerifyInternalToken(VerifyTokenRequest) returns (VerifiedClaimsResponse);
  rpc GetPublicKeyMetadata(Empty) returns (KeyMetadataResponse);  // for internal use
}
```

### Provides (HTTP — public)

```
GET /.well-known/jwks.json  — served directly by KMS or via Gateway cache
```

---

## Functional Tests

| Test | Expected |
|---|---|
| SignToken | Returns valid JWT, verifiable with public key from JWKS |
| SignToken — kid in header | JWT header contains correct kid |
| GetJWKS — normal | Returns current key |
| GetJWKS — after rotation within retire window | Returns both current and previous keys |
| GetJWKS — after retire window | Returns only current key |
| RotateKeys | New key active, old key in JWKS until retire_at |
| Token signed with rotated key | Verifiable using previous key from JWKS |
| VerifyInternalToken — valid | Returns claims |
| VerifyInternalToken — replayed jti | Rejected |
| VerifyInternalToken — wrong aud | Rejected |

## Unit Tests

```java
@Test void signToken_producesVerifiableJwt()
@Test void signToken_kidMatchesCurrentKey()
@Test void rotateKeys_oldPrivateKeyNoLongerAccessible()
@Test void rotateKeys_oldPublicKeyServedInJwks()
@Test void verifyInternalToken_replayedJti_rejected()
@Test void verifyInternalToken_wrongAudience_rejected()
@Test void verifyInternalToken_expiredToken_rejected()
```

---
---

# Audit & Event Service

---

## What to Implement

The Audit Service maintains an append-only log of all security-relevant events across the IdP. It consumes Kafka events from all other services and writes structured records. It supports compliance queries (PDPP, internal security reviews) and anomaly alerting. Records are never updated or deleted — only inserted.

---

## Schema

### PostgreSQL — audit_db

```sql
-- Master audit log (all events)
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL UNIQUE,   -- from Kafka message key
    event_type      VARCHAR(100) NOT NULL,  -- 'login.success' | 'token.issued' | etc.
    occurred_at     TIMESTAMPTZ NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id         UUID,
    client_id       VARCHAR(100),
    session_id      UUID,
    ip_address      INET,
    correlation_id  UUID,
    severity        VARCHAR(10) NOT NULL DEFAULT 'INFO',  -- 'INFO' | 'WARN' | 'CRITICAL'
    payload         JSONB NOT NULL          -- full event details, schema varies by event_type
) PARTITION BY RANGE (occurred_at);

-- Monthly partitions (create in advance)
CREATE TABLE audit_events_2026_03 PARTITION OF audit_events
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE INDEX idx_audit_user ON audit_events(user_id, occurred_at DESC);
CREATE INDEX idx_audit_type ON audit_events(event_type, occurred_at DESC);
CREATE INDEX idx_audit_client ON audit_events(client_id, occurred_at DESC);
CREATE INDEX idx_audit_correlation ON audit_events(correlation_id);
CREATE INDEX idx_audit_severity ON audit_events(severity, occurred_at DESC) WHERE severity != 'INFO';
```

### Event Payload Schemas (stored in JSONB)

```json
// login.success
{
  "user_id": "uuid",
  "email_hash": "sha256(email)",  // hash not plaintext
  "ip": "x.x.x.x",
  "user_agent": "...",
  "mfa_used": true,
  "mfa_type": "totp",
  "social_provider": null
}

// login.failed
{
  "email_hash": "sha256(email)",
  "ip": "x.x.x.x",
  "reason": "wrong_password",
  "attempt_count": 3
}

// token.issued
{
  "jti": "uuid",
  "user_id": "uuid",
  "client_id": "...",
  "scopes": "openid profile email",
  "token_type": "access",
  "expires_at": "..."
}

// token.family_compromised
{
  "family_id": "uuid",
  "user_id": "uuid",
  "client_id": "..."
}

// consent.granted
{
  "user_id": "uuid",
  "client_id": "...",
  "scopes": "openid profile erp:read"
}
```

---

## Functionality in Detail

### Kafka Consumer (all topics)

One consumer group per topic or a single multi-topic consumer. For each message:
1. Check `event_id` not already in DB (idempotency — Kafka at-least-once delivery)
2. Parse event into `AuditEvent` struct
3. Classify severity: `login.failed` repeated > 5 = WARN, `token.family_compromised` = CRITICAL
4. INSERT into `audit_events`
5. If severity = CRITICAL → publish alert event (handled separately)

### Query API (HTTP REST — internal admin use only)

```
GET /audit/events?user_id={}&event_type={}&from={}&to={}&page={}&size={}
GET /audit/events/{event_id}
GET /audit/summary/user/{user_id}     — login history, consent changes for user
GET /audit/summary/client/{client_id} — token issuance patterns for client
GET /audit/export?from={}&to={}       — PDPP compliance export (JSON Lines format)
```

### Anomaly Detection (simple rule-based, not ML)

These run as background scheduled jobs:

1. **Repeated failed logins:** Count `login.failed` events per `user_id` in last 15min. If > 10 → publish alert.
2. **Token family compromise:** Any `token.family_compromised` event → immediately publish CRITICAL alert.
3. **New country login:** If user's last 10 logins are all from Ethiopia, and new login is from different country → publish WARN alert.
4. **Rapid token issuance:** More than 50 tokens issued for same `client_id` in 1 minute → publish WARN.

Alerts are published to a `tolox.alerts` Kafka topic — not handled by Audit Service itself (separation of concerns). A future alert handler service consumes this.

---

## Interfaces

### Consumes (Kafka — all security topics)

All topics listed in the Overall Architecture document section 12.

### Provides (HTTP REST — internal admin only, not exposed via public Gateway)

Admin query API protected by internal token.

---

## Functional Tests

| Test | Expected |
|---|---|
| Kafka event consumed | Event appears in audit_events table |
| Duplicate event_id | Second insert silently skipped (idempotent) |
| CRITICAL event | Severity set to CRITICAL in DB |
| token.family_compromised | Severity=CRITICAL, alert published |
| Query by user_id | Returns all events for user, newest first |
| Query with date range | Returns only events in range |
| PDPP export | Returns all events in date range as JSON Lines |
| Anomaly — repeated failures | Alert published after threshold |
| Event payload stored | Full payload queryable via JSONB |

## Non-Functional Tests

| Test | Target |
|---|---|
| Event ingestion throughput | 10,000 events/sec sustained (Kafka → DB) |
| Query latency — by user | p99 < 50ms for last 30 days |
| Partition rollover | Monthly partition created automatically |
| Disk growth | Monitor and alert at 70% storage |

## Unit Tests

```java
@Test void consumeEvent_idempotent_duplicateEventSkipped()
@Test void classifyEvent_tokenFamilyCompromised_isCritical()
@Test void classifyEvent_loginSuccess_isInfo()
@Test void classifyEvent_repeatedFailures_isWarn()
@Test void buildPayload_emailNotStoredPlaintext()  // verify email_hash not email
@Test void anomalyDetector_repeatedFailures_triggersAlert()
@Test void anomalyDetector_belowThreshold_noAlert()
```

---

## Additional Considerations

**Never store raw PII in audit logs.** Emails are stored as SHA256 hashes. IP addresses are stored as-is (they are operational security data, not PII under PDPP). Audit logs must be immutable — use PostgreSQL row-level security to prevent UPDATE/DELETE on audit_events.

```sql
-- Prevent all updates and deletes on audit_events
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_insert_only ON audit_events
    FOR ALL USING (false)
    WITH CHECK (true);
-- This allows INSERT but blocks UPDATE and DELETE for all roles except superuser
```

**Retention policy:** PDPP requires audit logs be kept for minimum 3 years. Automated partition archival to cold storage after 90 days active, delete partitions after 3 years. Implement as scheduled job, not manual process.

**Correlation ID is the most useful query field for debugging.** Every cross-service trace ties together via `correlation_id`. Make sure every service includes it in every Kafka event payload.

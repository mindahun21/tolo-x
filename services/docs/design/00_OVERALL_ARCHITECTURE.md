# Tolox IdP — Overall Architecture

---

## 1. Platform Vision

Tolox is an Ethiopian-first SSO-based ecosystem platform. One Central Identity Provider (IdP) authenticates all users once. Every app (ERP, social, customer-facing) is a registered OAuth 2.0 / OIDC client. No app handles passwords or sessions directly.

---

## 2. Services Overview

| Service | Role | Sync Protocol | Async |
|---|---|---|---|
| API Gateway | Single entry point, rate limiting, routing | gRPC / HTTP | — |
| Discovery Service | Service registry, health-aware routing | HTTP (Eureka) | — |
| Config Service | Centralized configuration | HTTP | — |
| Authorization Service | OAuth 2.0 flows, token issuance, PKCE | gRPC (internal) | Kafka producer |
| Authentication Service | Login, MFA, social login orchestration | gRPC (internal) | Kafka producer |
| User Directory Service | User identity, passwords, SCIM | gRPC (internal) | Kafka consumer |
| Client Registry Service | App/client registration, scope definitions | gRPC (internal) | — |
| Session Service | SSO sessions, logout propagation | gRPC (internal) | Kafka consumer/producer |
| Consent Service | Consent records, revocation | gRPC (internal) | Kafka producer |
| Key Management Service | Signing keys, JWKS, rotation | gRPC (internal) | — |
| Audit & Event Service | Append-only security event log | — | Kafka consumer |

---

## 3. Infrastructure Components

| Component | Purpose |
|---|---|
| PostgreSQL (per service) | Primary relational store — each service owns its own DB |
| Redis (shared cluster) | Sessions, authorization codes, token revocation list, rate limit counters |
| Kafka | Async event bus — audit events, session revocation fanout, notifications |
| Vault (HashiCorp) | Secrets — DB passwords, signing key material, provider client secrets |
| Internal CA | Issues mTLS certificates to every service |

---

## 4. High-Level Component Diagram

```plantuml
@startuml Tolox_IdP_Component_Diagram
!theme plain
skinparam backgroundColor #FAFAFA
skinparam componentStyle rectangle

actor "User / Browser" as User
actor "External App (OAuth Client)" as App
actor "External Provider\n(Google, GitHub)" as Provider

rectangle "DMZ" {
  component "API Gateway" as GW
}

rectangle "Core IdP Services" {
  component "Authorization Service" as AuthZ
  component "Authentication Service" as AuthN
  component "Session Service" as SS
  component "Consent Service" as CS
  component "Key Management Service" as KMS
  component "User Directory Service" as UDS
  component "Client Registry Service" as CRS
}

rectangle "Support Services" {
  component "Audit & Event Service" as AUS
  component "Config Service" as CFG
  component "Discovery Service" as DS
}

database "PostgreSQL\n(AuthZ DB)" as PGAZ
database "PostgreSQL\n(AuthN DB)" as PGAN
database "PostgreSQL\n(Session DB)" as PGSS
database "PostgreSQL\n(Consent DB)" as PGCS
database "PostgreSQL\n(User DB)" as PGUD
database "PostgreSQL\n(Client DB)" as PGCR
database "PostgreSQL\n(Audit DB)" as PGAU

queue "Kafka" as KF
database "Redis Cluster" as RD
database "Vault" as VT

User --> GW : HTTPS
App --> GW : HTTPS
GW --> AuthZ : gRPC
GW --> AuthN : gRPC

AuthZ --> AuthN : gRPC
AuthZ --> SS : gRPC
AuthZ --> CS : gRPC
AuthZ --> KMS : gRPC (sign)
AuthZ --> CRS : gRPC
AuthZ --> RD : auth codes, token revocation list

AuthN --> UDS : gRPC
AuthN --> SS : gRPC
AuthN --> Provider : WebClient (OAuth2 client)

UDS --> PGUD
AuthZ --> PGAZ
AuthN --> PGAN
SS --> PGSS
SS --> RD : session cache
CS --> PGCS
CRS --> PGCR
KMS --> VT : key material

AuthZ --> KF : token.issued, token.revoked
AuthN --> KF : login.success, login.failed, mfa.triggered
SS --> KF : session.revoked
CS --> KF : consent.granted, consent.revoked

KF --> AUS : all events
AUS --> PGAU

DS --> GW : service registry
CFG --> AuthZ : config
CFG --> AuthN : config
CFG --> UDS : config

@enduml
```

---

## 5. Full Use Case Flow — Login + Token Issuance (Authorization Code + PKCE)

This is the primary hot path. Every step is numbered and maps to a service.

```
1.  User visits App → App redirects to GET /oauth2/authorize
    Params: response_type=code, client_id, redirect_uri, scope, state, code_challenge, code_challenge_method=S256

2.  Gateway receives request → validates required params → routes to Authorization Service

3.  Authorization Service:
    a. Validates client_id exists → calls Client Registry Service (gRPC)
    b. Validates redirect_uri exact match against registered URIs
    c. Validates requested scopes are allowed for this client
    d. Stores code_challenge + code_challenge_method in Redis (key = session_id, TTL 10min)
    e. Checks if active SSO session exists → calls Session Service (gRPC)

4.  If no session → Authorization Service redirects to login UI
    Login UI POSTs credentials to POST /auth/login → Authentication Service

5.  Authentication Service:
    a. Looks up user by email → calls User Directory (gRPC)
    b. Verifies password hash (timing-safe, Argon2id)
    c. Checks account status (active/suspended/locked)
    d. If MFA enrolled → starts MFA flow (returns MFA challenge, not session yet)
    e. On MFA success → creates session → calls Session Service (gRPC)
    f. Publishes login.success or login.failed to Kafka

6.  Session Service creates session record:
    - session_id (UUID), user_id, auth_time, mfa_level, device_fingerprint
    - Stores in PostgreSQL + caches in Redis (TTL = session lifetime)
    - Returns session_id to Authentication Service

7.  Authentication Service returns session_id (as HttpOnly secure cookie) to browser

8.  Browser redirected back to Authorization Service with session cookie

9.  Authorization Service:
    a. Validates session via Session Service (gRPC)
    b. Checks existing consent → calls Consent Service (gRPC)
    c. If no consent → renders consent screen (scopes, app name, Amharic/English)
    d. User approves → Consent Service records consent
    e. Generates authorization_code (cryptographically random, 32 bytes, base64url)
    f. Stores code → {user_id, client_id, scopes, session_id, code_challenge, redirect_uri} in Redis (TTL 60s)
    g. Redirects to redirect_uri?code=...&state=...

10. App backend POSTs to /oauth2/token:
    Params: grant_type=authorization_code, code, redirect_uri, client_id, client_secret, code_verifier

11. Authorization Service token endpoint:
    a. Validates client credentials (client_id + client_secret, timing-safe)
    b. Retrieves code from Redis → validates it exists (single use: delete immediately)
    c. Verifies code_verifier: SHA256(code_verifier) == code_challenge (base64url)
    d. Verifies redirect_uri matches stored value exactly
    e. Calls Key Management Service (gRPC) to sign tokens
    f. Issues access token (JWT, RS256, 15min TTL)
    g. Issues ID token (JWT, RS256, contains user claims per requested scopes)
    h. Issues refresh token (opaque UUID, stored in DB with family_id, TTL 30 days)
    i. Publishes token.issued event to Kafka
    j. Returns token response JSON

12. Audit Service consumes all Kafka events → writes append-only audit records
```

---

## 6. Sequence Diagram — Full Login Flow

```plantuml
@startuml Tolox_Login_Sequence
!theme plain
skinparam backgroundColor #FAFAFA

actor User
participant "Browser" as B
participant "App" as APP
participant "API Gateway" as GW
participant "Authorization\nService" as AZ
participant "Authentication\nService" as AN
participant "Session\nService" as SS
participant "User Directory\nService" as UD
participant "Consent\nService" as CS
participant "Key Mgmt\nService" as KMS
participant "Client Registry\nService" as CR
queue "Kafka" as KF
database "Redis" as RD

User -> APP : click login
APP -> B : redirect /oauth2/authorize\n?client_id&scope&code_challenge
B -> GW : GET /oauth2/authorize
GW -> AZ : route (gRPC)

AZ -> CR : validateClient(client_id, redirect_uri, scopes) [gRPC]
CR --> AZ : ClientDetails

AZ -> SS : getSession(session_cookie) [gRPC]
SS --> AZ : null (no session)

AZ --> B : redirect /login
B -> GW : POST /auth/login {email, password}
GW -> AN : authenticate(email, password) [gRPC]

AN -> UD : findByEmail(email) [gRPC]
UD --> AN : UserRecord

AN -> AN : verifyArgon2id(password, hash)
AN -> AN : checkMFAEnrolled()

AN -> SS : createSession(user_id, mfa_level) [gRPC]
SS -> RD : cache session
SS --> AN : session_id

AN -> KF : publish login.success
AN --> GW : session_id (cookie)
GW --> B : Set-Cookie: session_id (HttpOnly, Secure)

B -> GW : GET /oauth2/authorize (with session cookie)
GW -> AZ : route
AZ -> SS : validateSession(session_id) [gRPC]
SS --> AZ : SessionDetails {user_id, mfa_level}

AZ -> CS : getConsent(user_id, client_id, scopes) [gRPC]
CS --> AZ : null (no consent)

AZ --> B : render consent screen
User -> B : approve
B -> GW : POST /oauth2/consent {approved_scopes}
GW -> AZ : route

AZ -> CS : recordConsent(user_id, client_id, scopes) [gRPC]
CS --> AZ : ok

AZ -> RD : store auth_code → {user_id, client_id, scopes, code_challenge} TTL=60s
AZ --> B : redirect app?code=...&state=...
B -> APP : code + state

APP -> GW : POST /oauth2/token {code, code_verifier, client_secret}
GW -> AZ : route

AZ -> RD : get+delete auth_code (single use)
AZ -> AZ : verify PKCE: SHA256(code_verifier)==code_challenge
AZ -> KMS : sign(access_token_claims) [gRPC]
KMS --> AZ : signed JWT

AZ -> KMS : sign(id_token_claims) [gRPC]
KMS --> AZ : signed JWT

AZ -> AZ : generate opaque refresh_token, store in DB
AZ -> KF : publish token.issued
AZ --> APP : {access_token, id_token, refresh_token, expires_in}

@enduml
```

---

## 7. Sequence Diagram — Token Refresh + Rotation

```plantuml
@startuml Tolox_Token_Refresh
!theme plain

participant "App" as APP
participant "API Gateway" as GW
participant "Authorization\nService" as AZ
participant "Key Mgmt\nService" as KMS
database "Token DB" as DB
queue "Kafka" as KF

APP -> GW : POST /oauth2/token\n{grant_type=refresh_token, refresh_token}
GW -> AZ : route

AZ -> DB : findRefreshToken(token)
DB --> AZ : RefreshTokenRecord {family_id, user_id, scopes, used=false}

alt token already used (reuse detected)
  AZ -> DB : revokeAllByFamily(family_id)
  AZ -> KF : publish token.family.compromised
  AZ --> APP : 400 invalid_grant
else token valid
  AZ -> DB : markUsed(token)
  AZ -> DB : createNewRefreshToken(same family_id)
  AZ -> KMS : sign(new_access_token_claims)
  KMS --> AZ : signed JWT
  AZ -> KF : publish token.refreshed
  AZ --> APP : {new_access_token, new_refresh_token}
end

@enduml
```

---

## 8. Sequence Diagram — Global Logout

```plantuml
@startuml Tolox_Logout
!theme plain

actor User
participant "App A" as A
participant "API Gateway" as GW
participant "Authorization\nService" as AZ
participant "Session\nService" as SS
database "Redis" as RD
queue "Kafka" as KF
participant "App B\n(back-channel)" as B

User -> A : click logout
A -> GW : POST /oauth2/logout {id_token_hint, post_logout_redirect_uri}
GW -> AZ : route

AZ -> SS : revokeSession(session_id) [gRPC]
SS -> RD : delete session cache
SS -> SS : mark session revoked in DB
SS -> KF : publish session.revoked {session_id, user_id, client_ids[]}

KF -> AZ : consume session.revoked
AZ -> AZ : add active tokens to revocation list in Redis (TTL = token expiry)

KF -> B : back-channel logout POST\n{logout_token (signed JWT)}
B -> B : invalidate local session

AZ --> A : redirect post_logout_redirect_uri

@enduml
```

---

## 9. Service Communication Rules

| Call Type | Protocol | Auth |
|---|---|---|
| External client → Gateway | HTTPS | Client credentials / Bearer token |
| Gateway → any service | gRPC | mTLS + internal service JWT |
| Service → Service (sync) | gRPC | mTLS + internal service JWT |
| Service → external provider | WebClient (HTTPS) | Provider OAuth2 credentials (from Vault) |
| Service → Kafka | Kafka producer | mTLS (Kafka broker) |
| Kafka → Service | Kafka consumer | mTLS (Kafka broker) |
| Any service → Redis | Redis client | TLS + Redis AUTH |
| Any service → Vault | Vault client | AppRole auth |

---

## 10. Internal Service JWT Format

Every gRPC call between services carries this token in metadata header `x-internal-token`:

```json
{
  "iss": "tolox-internal",
  "sub": "authorization-service",
  "aud": "user-directory-service",
  "iat": 1700000000,
  "exp": 1700000060,
  "jti": "unique-per-call-uuid"
}
```

- Signed with a separate internal signing key (not the user-facing RS256 key)
- TTL: 60 seconds
- Issued by each service for each outbound call
- Target service validates `iss`, `aud` (must match its own service name), `exp`, and `jti` (prevent replay via Redis seen-jti cache)

---

## 11. Redis Key Namespaces

| Namespace | Content | TTL |
|---|---|---|
| `authcode:{code}` | auth code payload | 60s |
| `session:{session_id}` | session cache | session lifetime |
| `revoked_token:{jti}` | revoked token marker | token remaining TTL |
| `revoked_family:{family_id}` | compromised refresh token family | 30 days |
| `mfa_state:{session_id}` | in-progress MFA state machine | 5 min |
| `rate_limit:ip:{ip}` | failed attempt counter | rolling 15 min |
| `rate_limit:client:{client_id}` | client request counter | rolling 1 min |
| `seen_jti:{jti}` | internal token replay prevention | 65s |
| `pkce_challenge:{session_id}` | code_challenge during authorize flow | 10 min |

---

## 12. Kafka Topics

| Topic | Producer | Consumer | Payload |
|---|---|---|---|
| `tolox.auth.login` | Authentication Service | Audit Service | user_id, ip, device, success/fail, reason |
| `tolox.auth.mfa` | Authentication Service | Audit Service | user_id, mfa_type, success/fail |
| `tolox.token.issued` | Authorization Service | Audit Service | jti, user_id, client_id, scopes, expiry |
| `tolox.token.revoked` | Authorization Service | Audit Service | jti, reason |
| `tolox.token.family_compromised` | Authorization Service | Audit Service | family_id, user_id |
| `tolox.session.revoked` | Session Service | Authorization Service, Audit Service | session_id, user_id, client_ids |
| `tolox.consent.granted` | Consent Service | Audit Service | user_id, client_id, scopes |
| `tolox.consent.revoked` | Consent Service | Authorization Service, Audit Service | user_id, client_id |

---

## 13. Security Invariants (Cross-Cutting)

These apply to every service. No exceptions.

- Never log: passwords, tokens, authorization codes, client secrets. Log only IDs and hashes.
- All timestamps use UTC. Token `exp`/`iat`/`nbf` allow ±30s clock skew.
- All string comparisons of secrets use constant-time (`MessageDigest.isEqual`).
- All DB writes to security-sensitive tables are append-only or soft-delete only.
- Correlation ID (`X-Correlation-ID`) injected at Gateway, forwarded by every service in every log line and every downstream call.
- Every HTTP response that could leak information on error returns the same generic error shape — never expose internal service names, stack traces, or DB errors to external callers.

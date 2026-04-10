# Notification System

---

## What to Implement

The Notification System is the platform-wide delivery infrastructure for all transactional, security, and marketing communications across the Tolox ecosystem. It is a standalone, event-driven subsystem composed of five internal services that handle the full lifecycle of a notification — from intake to delivery — without coupling to any specific business domain.

Every Tolox service (Authentication, Authorization, User Directory, Session, etc.) publishes events to Kafka. The Notification System consumes those events, resolves user preferences, renders localized content, and dispatches messages through external providers (Email via SendGrid, SMS via Twilio, Push via FCM). It is designed for Ethiopian-first localization (Amharic + English), spotty-network resilience, and PDPP compliance.

---

## System Boundary

```
┌──────────────────────────────────────────────────────────────────────┐
│                        TOLOX PLATFORM                                │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  Auth     │  │  Session │  │  User    │  │  Any App │            │
│  │  Service  │  │  Service │  │  Dir.    │  │  Service │            │
│  └────┬──── ┘  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
│       │              │              │              │                  │
│       └──────────────┴──────┬───────┴──────────────┘                 │
│                             │                                        │
│                    Kafka / gRPC call                                  │
│                             │                                        │
│  ┌──────────────────────────▼───────────────────────────────────┐    │
│  │              NOTIFICATION SYSTEM                              │    │
│  │                                                               │    │
│  │  ┌─────────────┐  ┌──────────┐  ┌────────────┐              │    │
│  │  │ Notification │  │ Template │  │ Preference │              │    │
│  │  │ API Service  │  │ Service  │  │ Service    │              │    │
│  │  └──────┬───── ┘  └────┬─────┘  └─────┬──────┘              │    │
│  │         │               │               │                     │    │
│  │         │  Kafka        │  REST/Cache    │  REST/Cache         │    │
│  │         ▼               ▼               ▼                     │    │
│  │  ┌──────────────────────────────────────┐                     │    │
│  │  │    Notification Processor Service    │                     │    │
│  │  └──────────────┬───────────────────────┘                     │    │
│  │                 │                                              │    │
│  │         ┌───────┴──────────┐                                  │    │
│  │         │  Campaign Service│                                  │    │
│  │         └──────────────────┘                                  │    │
│  └───────────────────────────────────────────────────────────────┘    │
│                             │                                        │
│                    Provider Adapters                                  │
│                             │                                        │
│              ┌──────────────┼──────────────┐                         │
│              ▼              ▼              ▼                          │
│         SendGrid        Twilio          FCM                          │
│         (Email)         (SMS)          (Push)                        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Services Overview

| Service | Role | Sync Protocol | Async |
|---|---|---|---|
| Notification API Service | Single entry point; validation, normalization, Kafka publishing | REST (internal gRPC from platform services) | Kafka producer |
| Notification Preference Service | User preference storage, decision engine, compliance | REST (internal) | Kafka consumer (preference changes) |
| Template Service | Template CRUD, versioning, localization, rendering | REST (internal) | — |
| Notification Processor Service | Kafka consumer; deduplication, preference check, rendering, provider dispatch | — | Kafka consumer / producer |
| Campaign Service | Bulk orchestration, scheduling, audience segmentation, traffic shaping | REST (admin) | Kafka producer (via API Service) |

---

## Schema

### PostgreSQL — notification_api_db

```sql
CREATE TABLE request_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID NOT NULL UNIQUE,
    client_request_id   VARCHAR(255) UNIQUE,             -- idempotency key from caller
    application_code    VARCHAR(50) NOT NULL,             -- e.g., 'TOLO_AUTH', 'TOLO_ERP'
    template_code       VARCHAR(100) NOT NULL,
    user_id             UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL,             -- EMAIL | SMS | PUSH
    locale              VARCHAR(10) NOT NULL DEFAULT 'en',
    priority            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',  -- HIGH | NORMAL | LOW
    status              VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED', -- ACCEPTED | REJECTED
    error_reason        TEXT,
    payload_hash        VARCHAR(64),                      -- SHA256 for dedup
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_request_logs_user ON request_logs(user_id);
CREATE INDEX idx_request_logs_app ON request_logs(application_code);
CREATE INDEX idx_request_logs_created ON request_logs(created_at);
```

### PostgreSQL — notification_preference_db

```sql
-- Global notification type registry
CREATE TABLE notification_types (
    id                      SERIAL PRIMARY KEY,
    code                    VARCHAR(100) UNIQUE NOT NULL,  -- e.g., 'PASSWORD_RESET', 'ORDER_CONFIRMED'
    app_id                  VARCHAR(50) NOT NULL,          -- owning application
    channel                 VARCHAR(20) NOT NULL,          -- EMAIL | SMS | PUSH
    category                VARCHAR(20) NOT NULL,          -- SECURITY | TRANSACTIONAL | MARKETING | SOCIAL
    default_enabled         BOOLEAN NOT NULL DEFAULT true,
    is_mandatory            BOOLEAN NOT NULL DEFAULT false, -- true = always send (password reset, MFA OTP)
    max_frequency_per_day   INT,                           -- null = unlimited
    cooldown_seconds        INT,                           -- minimum gap between sends
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DEPRECATED
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deprecated_at           TIMESTAMPTZ
);

CREATE INDEX idx_notification_types_code ON notification_types(code);

-- Per-user channel-level settings
CREATE TABLE user_channel_settings (
    user_id             UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL,             -- EMAIL | SMS | PUSH
    is_blocked          BOOLEAN NOT NULL DEFAULT false,
    legal_opt_out       BOOLEAN NOT NULL DEFAULT false,   -- PDPP/GDPR right-to-stop
    quiet_hours_start   TIME,                             -- e.g., 22:00
    quiet_hours_end     TIME,                             -- e.g., 07:00
    timezone            VARCHAR(50) NOT NULL DEFAULT 'Africa/Addis_Ababa',
    consent_version     VARCHAR(20),
    consent_timestamp   TIMESTAMPTZ,
    PRIMARY KEY (user_id, channel)
);

-- Per-user notification type overrides (sparse — only when user changes from default)
CREATE TABLE user_notification_overrides (
    user_id                 UUID NOT NULL,
    notification_type_id    INT NOT NULL REFERENCES notification_types(id),
    is_enabled              BOOLEAN NOT NULL,
    consent_version         VARCHAR(20),
    consent_timestamp       TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, notification_type_id)
);

-- Consent audit trail (PDPP compliance)
CREATE TABLE user_consent_history (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    notification_type_id    INT,                           -- null for channel-level changes
    channel                 VARCHAR(20),
    old_value               VARCHAR(20),
    new_value               VARCHAR(20),
    policy_version          VARCHAR(20),
    source                  VARCHAR(20) NOT NULL,          -- UI | API | ADMIN | SYSTEM
    changed_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consent_history_user ON user_consent_history(user_id);

-- Materialized eligibility for campaign batch queries
CREATE TABLE user_notification_eligibility (
    user_id                 UUID NOT NULL,
    notification_type_id    INT NOT NULL REFERENCES notification_types(id),
    is_enabled              BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, notification_type_id)
);

CREATE INDEX idx_eligibility_type ON user_notification_eligibility(notification_type_id, is_enabled);
```

### PostgreSQL — template_db

```sql
-- Logical template registry
CREATE TABLE templates (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_code        VARCHAR(50) NOT NULL,
    template_code           VARCHAR(100) NOT NULL,
    description             TEXT,
    active_version_number   INT NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_template_identity UNIQUE(application_code, template_code)
);

-- Immutable template versions
CREATE TABLE template_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id         UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    channel             VARCHAR(20) NOT NULL,             -- EMAIL | SMS | PUSH
    locale              VARCHAR(10) NOT NULL,             -- en | am
    subject             TEXT,                             -- nullable for SMS/PUSH
    body                TEXT NOT NULL,
    engine              VARCHAR(20) NOT NULL DEFAULT 'HANDLEBARS', -- HANDLEBARS | THYMELEAF
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',      -- DRAFT | PUBLISHED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_version_identity UNIQUE(template_id, version_number, channel, locale)
);

CREATE INDEX idx_template_version_lookup ON template_versions(template_id, version_number, channel, locale);

-- Global shared assets (logos, URLs, branding)
CREATE TABLE assets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_key           VARCHAR(100) UNIQUE NOT NULL,     -- e.g., 'COMPANY_LOGO', 'SUPPORT_URL'
    asset_url           VARCHAR(500) NOT NULL,
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### PostgreSQL — notification_processor_db

```sql
-- Delivery history (idempotency + tracking)
CREATE TABLE delivery_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID NOT NULL UNIQUE,             -- from Kafka event
    user_id             UUID NOT NULL,
    application_code    VARCHAR(50) NOT NULL,
    template_code       VARCHAR(100) NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    locale              VARCHAR(10) NOT NULL,
    provider            VARCHAR(50),                      -- SENDGRID | TWILIO | FCM
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                                          -- PENDING | RENDERED | SENT | FAILED | RETRYING | DLQ
    rendered_at         TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    retry_count         INT NOT NULL DEFAULT 0,
    error_message       TEXT,
    provider_message_id VARCHAR(255),                     -- external provider tracking ID
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_user ON delivery_history(user_id);
CREATE INDEX idx_delivery_status ON delivery_history(status);
CREATE INDEX idx_delivery_created ON delivery_history(created_at);
```

### PostgreSQL — campaign_db

```sql
CREATE TABLE campaigns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    application_code    VARCHAR(50) NOT NULL,
    template_code       VARCHAR(100) NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    locale              VARCHAR(10) NOT NULL DEFAULT 'en',
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                                                          -- DRAFT | SCHEDULED | RUNNING | PAUSED | COMPLETED | CANCELLED
    scheduled_at        TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    total_recipients    INT NOT NULL DEFAULT 0,
    processed_count     INT NOT NULL DEFAULT 0,
    success_count       INT NOT NULL DEFAULT 0,
    failure_count       INT NOT NULL DEFAULT 0,
    rate_limit_per_sec  INT NOT NULL DEFAULT 100,         -- traffic shaping
    created_by          UUID NOT NULL,                    -- admin user ID
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE campaign_segments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID NOT NULL REFERENCES campaigns(id),
    segment_type    VARCHAR(50) NOT NULL,                 -- ALL_USERS | USER_LIST | FILTER
    filter_query    JSONB,                                -- dynamic segment filter
    user_list       UUID[],                               -- explicit user IDs (for USER_LIST type)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Redis Keys

| Key Pattern | TTL | Purpose |
|---|---|---|
| `notif:type:{code}` | 24h (L2 cache of registry) | Notification type lookup |
| `user:channel:{userId}` | Invalidate on write | User channel settings hash |
| `user:overrides:{userId}` | Invalidate on write | User notification overrides hash |
| `template:content:{appCode}:{templateCode}:{channel}:{locale}:{version}` | 24h | Rendered template content cache |
| `template:registry:{appCode}:{templateCode}` | Invalidate on publish | Active version number |
| `template:assets` | Invalidate on update | Global asset map |
| `notif:dedup:{payloadHash}` | 24h | Request deduplication |
| `notif:freq:{userId}:{typeCode}:{date}` | 24h | Frequency counter per user per type per day |
| `notif:cooldown:{userId}:{typeCode}` | {cooldown_seconds} | Cooldown tracking |

---

## Kafka Topics

| Topic | Partitioned By | Producer | Consumer |
|---|---|---|---|
| `notification.events.raw` | userId | Notification API Service | Notification Processor |
| `notification.events.dlq` | eventId | Notification Processor | Manual / Admin reprocessor |
| `notification.delivery.status` | eventId | Notification Processor | Audit & Event Service |
| `notification.preference.changed` | userId | Preference Service | Processor (cache invalidation) |
| `campaign.events.batch` | campaignId | Campaign Service | Notification API Service |

---

## Detailed Functionality

### 1. Notification API Service

**What it does:**
- Sole entry point for all notification requests (REST for platform services, gRPC adapter for IdP core services)
- Validates `applicationCode` + `templateCode` by calling Template Service
- Validates required template variables are present in payload `data`
- Generates unique `eventId` (UUID) for tracking
- Idempotency: hashes request payload, checks Redis `notif:dedup:{hash}` — if duplicate, returns original `eventId`
- Normalizes to `NotificationEvent` format and publishes to Kafka `notification.events.raw`
- Records request in `request_logs` table
- Returns `202 Accepted` with `eventId`

**What it does NOT do:**
- Render templates
- Check user preferences
- Call external providers
- Execute business logic

**API Endpoints:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/notifications` | Submit notification request |
| `GET` | `/v1/notifications/{eventId}/status` | Check delivery status |
| `POST` | `/v1/notifications/batch` | Submit batch of notifications |

### 2. Notification Preference Service

**What it does:**
- Stores and resolves user notification preferences with a strict rule-based decision engine
- Evaluation order (highest to lowest precedence): **Mandatory → Legal Opt-Out → Global Channel Block → Quiet Hours → User Override → Frequency Limit → System Default**
- Provides ultra-low-latency `POST /internal/preferences/evaluate` endpoint (< 10ms target via L1/L2 caching)
- Manages notification type registry (admin CRUD)
- On user registration: auto-creates 3 `user_channel_settings` rows (EMAIL, SMS, PUSH) with defaults
- On preference change: updates DB, publishes `notification.preference.changed` event, invalidates Redis cache
- Maintains `user_consent_history` for PDPP compliance auditing
- Maintains materialized `user_notification_eligibility` table for campaign batch queries

**Decision Engine:**

```
Input: userId, notificationTypeCode, channel, currentTime

1. Load notification_type from L1 cache
2. IF is_mandatory → RETURN SEND (reason: MANDATORY)
3. Load user_channel_settings from Redis L2 (fallback: DB)
4. IF legal_opt_out → RETURN BLOCK (reason: LEGAL_OPTOUT)
5. IF is_blocked for channel → RETURN BLOCK (reason: CHANNEL_BLOCKED)
6. IF in quiet_hours for user timezone → RETURN DELAY (reason: QUIET_HOURS)
7. Load user_notification_overrides from Redis L2 (fallback: DB)
8. IF override exists → RETURN override.is_enabled (reason: USER_OVERRIDE)
9. Check frequency counter in Redis
10. IF max_frequency_per_day exceeded → RETURN BLOCK (reason: FREQUENCY_LIMIT)
11. RETURN notification_type.default_enabled (reason: DEFAULT)
```

**Failure Behavior (category-aware):**
- SECURITY notifications: send mandatory only (fail open)
- TRANSACTIONAL notifications: use last cached preference (fail stale)
- MARKETING notifications: fail closed (do not send)

**API Endpoints:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/internal/preferences/evaluate` | Decision evaluation (Processor calls this) |
| `POST` | `/internal/preferences/effective` | Effective context with best channel |
| `GET` | `/users/{userId}/channels` | Get user channel preferences |
| `PUT` | `/users/{userId}/channels/{channel}` | Update channel preference |
| `PATCH` | `/users/{userId}/channels/{channel}/legal-opt-out` | Legal opt-out |
| `GET` | `/users/{userId}/notifications` | Get user notification overrides |
| `PUT` | `/users/{userId}/notifications/{typeCode}` | Create/update override |
| `DELETE` | `/users/{userId}/notifications/{typeCode}` | Delete override |
| `PUT` | `/users/{userId}/channel-priority` | Set channel priority order |
| `GET` | `/users/{userId}/preferences/history` | Consent audit trail |
| `POST` | `/admin/notification-types` | Create notification type |
| `PUT` | `/admin/notification-types/{code}` | Update notification type |
| `PATCH` | `/admin/notification-types/{code}/deprecate` | Deprecate type |
| `GET` | `/admin/notification-types` | List all types |

### 3. Template Service

**What it does:**
- Central repository for all notification content — email subjects, SMS bodies, push titles
- Immutable versioning: content is never updated in-place; every edit creates a new version
- Multi-locale support: `en` (English), `am` (Amharic), with smart fallback chain (`am_ET` → `am` → `en`)
- Multi-channel: same template code can have EMAIL, SMS, PUSH variants per version
- Rendering via pluggable engine (Handlebars / Thymeleaf) — fetches active version, injects `dataMap` + global assets
- Two-tier caching: L1 in-memory (template registry) + L2 Redis (rendered content)
- Cache invalidation via Redis Pub/Sub on version publish
- Global asset management for shared branding (logos, URLs)

**Publication Lifecycle:**
1. Admin creates `TemplateVersion` with `status=DRAFT`
2. Admin tests via preview endpoint
3. Admin publishes: `status=PUBLISHED`, `activeVersionNumber` updated, L1+L2 cache evicted
4. Rollback: point `activeVersionNumber` to previous published version, evict cache

**API Endpoints:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/template/render` | Render active version (Processor calls this) |
| `GET` | `/template/{id}/variables` | Discover required variables |
| `GET` | `/template/{id}/versions/{v}/variables` | Variables for specific version |
| `POST` | `/template` | Create logical template |
| `GET` | `/template` | List all templates |
| `GET` | `/template/{id}` | Get template metadata |
| `PUT` | `/template/{id}` | Update metadata |
| `POST` | `/template/{id}/versions/{v}` | Create version/channel/locale variant |
| `GET` | `/template/{id}/versions` | List version history |
| `PATCH` | `/template/{id}/publish/{v}` | Publish version |
| `POST` | `/template/{id}/versions/{v}/preview` | Preview draft rendering |
| `GET` | `/assets` | List global assets |
| `POST` | `/assets` | Create asset |
| `PUT` | `/assets/{id}` | Update asset |

### 4. Notification Processor Service

**What it does:**
- Kafka consumer group for `notification.events.raw` — horizontally scalable
- Processing pipeline per event:
  1. **Deduplicate**: check `eventId` in `delivery_history` table — skip if already processed
  2. **Evaluate**: call Preference Service `POST /internal/preferences/evaluate` — if BLOCK, mark as BLOCKED and skip
  3. **Render**: call Template Service `POST /template/render` with event data — get rendered subject + body
  4. **Route**: determine provider adapter based on channel (SendGrid for EMAIL, Twilio for SMS, FCM for PUSH)
  5. **Dispatch**: send via provider adapter with timeout + retry
  6. **Record**: update `delivery_history` with final status + provider message ID
- Retry: exponential backoff (1s, 2s, 4s, 8s, 16s) with max 5 retries
- Dead Letter Queue: after max retries, publish to `notification.events.dlq`
- Circuit breaker: per-provider circuit breaker (Resilience4j) to prevent cascading failure
- Publishes delivery status to `notification.delivery.status` topic for Audit & Event Service

**Provider Adapter Interface:**

```java
public interface ProviderAdapter {
    String provider();                    // "SENDGRID" | "TWILIO" | "FCM"
    String channel();                     // "EMAIL" | "SMS" | "PUSH"
    ProviderResponse send(RenderedMessage message);
}
```

### 5. Campaign Service

**What it does:**
- Manages bulk notification campaigns: create, schedule, execute, pause, cancel
- Audience segmentation: ALL_USERS, explicit USER_LIST, or dynamic FILTER (queries User Directory + eligibility table)
- Execution: resolves audience → batches user IDs → generates individual `NotificationEvent` per user → publishes to Notification API Service at a controlled rate (token bucket)
- Scheduling: Quartz/Spring Batch for future and recurring sends
- Progress tracking: real-time counts of processed/success/failure per campaign
- Traffic shaping: configurable `rate_limit_per_sec` to prevent Kafka flooding

**What it does NOT do:**
- Send messages directly — always goes through Notification API → Processor pipeline
- Render templates
- Store delivery status for individual messages

**API Endpoints:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/admin/campaigns` | Create campaign |
| `GET` | `/admin/campaigns` | List campaigns |
| `GET` | `/admin/campaigns/{id}` | Get campaign details + progress |
| `PUT` | `/admin/campaigns/{id}` | Update campaign |
| `POST` | `/admin/campaigns/{id}/schedule` | Schedule campaign |
| `POST` | `/admin/campaigns/{id}/execute` | Execute immediately |
| `POST` | `/admin/campaigns/{id}/pause` | Pause running campaign |
| `POST` | `/admin/campaigns/{id}/cancel` | Cancel campaign |
| `POST` | `/admin/campaigns/{id}/segments` | Add audience segment |

---

## Integration with Tolox IdP Core Services

The Notification System receives events from IdP services via Kafka. These are the **mandatory notification types** that must be seeded in the registry:

| Source Service | Kafka Topic | Notification Type Code | Category | Channel | Mandatory? |
|---|---|---|---|---|---|
| Authentication | `tolox.auth.login.success` | `LOGIN_SUCCESS` | SECURITY | EMAIL | No |
| Authentication | `tolox.auth.login.failed` | `LOGIN_FAILED_ALERT` | SECURITY | EMAIL | Yes |
| Authentication | `tolox.auth.mfa.triggered` | `MFA_OTP_DELIVERY` | SECURITY | SMS/EMAIL | Yes |
| Authentication | `tolox.auth.magic_link.requested` | `MAGIC_LINK` | SECURITY | EMAIL | Yes |
| User Directory | `tolox.user.created` | `WELCOME_EMAIL` | TRANSACTIONAL | EMAIL | No |
| User Directory | `tolox.user.email_verification` | `EMAIL_VERIFICATION` | SECURITY | EMAIL | Yes |
| User Directory | `tolox.user.password_reset` | `PASSWORD_RESET` | SECURITY | EMAIL | Yes |
| User Directory | `tolox.user.password_changed` | `PASSWORD_CHANGED_ALERT` | SECURITY | EMAIL | Yes |
| Session | `tolox.session.revoked` | `SESSION_REVOKED_ALERT` | SECURITY | EMAIL | No |
| Authorization | `tolox.token.family_compromised` | `TOKEN_COMPROMISE_ALERT` | SECURITY | EMAIL/SMS | Yes |
| Consent | `tolox.consent.granted` | `CONSENT_CONFIRMATION` | TRANSACTIONAL | EMAIL | No |

---

## Interfaces

### gRPC (from IdP core services)

```protobuf
service NotificationGateway {
    rpc SendNotification (SendNotificationRequest) returns (SendNotificationResponse);
    rpc GetDeliveryStatus (GetDeliveryStatusRequest) returns (DeliveryStatusResponse);
}

message SendNotificationRequest {
    string application_code = 1;
    string template_code = 2;
    string user_id = 3;
    string channel = 4;        // EMAIL | SMS | PUSH
    string locale = 5;
    map<string, string> data = 6;
    string priority = 7;       // HIGH | NORMAL | LOW
    string idempotency_key = 8;
}

message SendNotificationResponse {
    string event_id = 1;
    string status = 2;         // ACCEPTED | REJECTED
    string error_reason = 3;
}

message GetDeliveryStatusRequest {
    string event_id = 1;
}

message DeliveryStatusResponse {
    string event_id = 1;
    string status = 2;         // PENDING | RENDERED | SENT | FAILED | DLQ
    string provider = 3;
    string sent_at = 4;
}
```

### Kafka Event Format (NotificationEvent)

```json
{
    "eventId": "uuid",
    "applicationCode": "TOLO_AUTH",
    "templateCode": "MFA_OTP_DELIVERY",
    "userId": "user-uuid",
    "channel": "SMS",
    "locale": "am",
    "priority": "HIGH",
    "data": {
        "otpCode": "123456",
        "userName": "ምንዳሁን",
        "expiresInMinutes": "5"
    },
    "metadata": {
        "sourceService": "authentication-service",
        "correlationId": "req-uuid",
        "timestamp": "2026-04-10T14:00:00Z"
    }
}
```

---

## Edge Cases

| Case | Behavior |
|---|---|
| Preference Service is down + SECURITY notification | Send anyway (fail open for mandatory) |
| Preference Service is down + MARKETING notification | Block (fail closed) |
| Template not found for requested locale | Fallback chain: `am_ET` → `am` → `en` → reject |
| Duplicate request (same idempotency key) | Return original `eventId`, do not re-publish to Kafka |
| Provider timeout (SendGrid/Twilio) | Retry with exponential backoff, max 5 attempts |
| Provider circuit open | Route to DLQ, alert ops |
| User deleted (PDPP) | Preference Service consumes `user.deleted` → delete all user preferences, block future sends |
| Campaign targeting millions | Batch user IDs, token-bucket rate limiting, async fan-out |
| Quiet hours for user | DELAY decision — Processor re-queues with delay or schedules |

---

## Testing

### Unit Tests
- Decision engine: each rule in isolation (MandatoryRule, LegalOptOutRule, etc.)
- Template rendering: variable injection, locale fallback, missing variable detection
- Idempotency: duplicate detection with same payload hash
- Provider adapters: mock provider responses, timeout handling, retry logic

### Integration Tests
- End-to-end: publish event to Kafka → Processor picks up → calls Preference → calls Template → dispatches via mock provider → verify `delivery_history` record
- Preference cache: update preference → verify Redis invalidation → verify next evaluation uses new value
- Template publish: create DRAFT → publish → verify L1/L2 cache update → render returns new content
- Campaign execution: create campaign → execute → verify N events published to Notification API at rate limit

### Security Tests
- Verify internal-only endpoints reject external callers
- Verify admin endpoints require ADMIN role
- Verify user preference endpoints enforce user-ID ownership
- Verify PDPP: user deletion cascades to preference deletion and blocks future sends

### Performance Tests
- Preference evaluation: < 10ms at 95th percentile under 10k RPS
- Template rendering: < 15ms at 95th percentile
- Processor throughput: > 5000 events/sec per instance with 3 partitions

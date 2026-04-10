# Notification System — Flows & Sequence Diagrams

---

## 1. Transactional Notification Flow (e.g., Password Reset)

This is the primary flow — a platform service triggers a notification that reaches the user.

```
┌──────────┐     ┌──────────┐     ┌─────────┐     ┌───────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  User     │     │  Auth    │     │ Kafka   │     │ Notif API │     │ Notif    │     │ Pref     │     │ Template │
│  Dir.     │     │ Service  │     │         │     │ Service   │     │ Processor│     │ Service  │     │ Service  │
│  Service  │     │          │     │         │     │           │     │          │     │          │     │          │
└────┬──────┘     └────┬─────┘     └────┬────┘     └─────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                 │                │                 │                │                │                │
     │ user requests   │                │                 │                │                │                │
     │ password reset  │                │                 │                │                │                │
     ├────────────────>│                │                 │                │                │                │
     │                 │                │                 │                │                │                │
     │  generate token │                │                 │                │                │                │
     │  store in DB    │                │                 │                │                │                │
     │<────────────────┤                │                 │                │                │                │
     │                 │                │                 │                │                │                │
     │    publish tolox.user.password_reset               │                │                │                │
     │    to Kafka with user_id + reset_link              │                │                │                │
     ├───────────────────────────────-->│                 │                │                │                │
     │                 │                │                 │                │                │                │
     │                 │                │  Notif API      │                │                │                │
     │                 │                │  consumes OR    │                │                │                │
     │                 │                │  receives gRPC  │                │                │                │
     │                 │                ├────────────────>│                │                │                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │ validate app   │                │                │
     │                 │                │                 │ + template     │                │                │
     │                 │                │                 ├───────────────────────────────────────────────-->│
     │                 │                │                 │<──────────────────────────────────────────────── │
     │                 │                │                 │                │                │                │
     │                 │                │                 │ check dedup    │                │                │
     │                 │                │                 │ (Redis)        │                │                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │ normalize +    │                │                │
     │                 │                │                 │ publish to     │                │                │
     │                 │                │                 │ notification   │                │                │
     │                 │                │                 │ .events.raw    │                │                │
     │                 │                │<────────────────┤                │                │                │
     │                 │                │                 │                │                │                │
     │                 │                │ Processor       │                │                │                │
     │                 │                │ consumes event  │                │                │                │
     │                 │                ├────────────────────────────────>│                │                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │ 1. check dedup │                │
     │                 │                │                 │                │    (delivery   │                │
     │                 │                │                 │                │     history)   │                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │ 2. evaluate    │                │
     │                 │                │                 │                │    preference  │                │
     │                 │                │                 │                ├───────────────>│                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │  is_mandatory  │                │
     │                 │                │                 │                │  = true        │                │
     │                 │                │                 │                │  → SEND        │                │
     │                 │                │                 │                │<───────────────┤                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │ 3. render      │                │
     │                 │                │                 │                │    template    │                │
     │                 │                │                 │                ├───────────────────────────────>│
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │  rendered HTML │                │
     │                 │                │                 │                │  + subject     │                │
     │                 │                │                 │                │<─────────────────────────────── │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │ 4. dispatch    │                │
     │                 │                │                 │                │    via SendGrid│                │
     │                 │                │                 │                │────────> [SendGrid]             │
     │                 │                │                 │                │<──────── 202 OK                 │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │ 5. record in   │                │
     │                 │                │                 │                │    delivery_   │                │
     │                 │                │                 │                │    history     │                │
     │                 │                │                 │                │                │                │
     │                 │                │                 │                │ 6. publish     │                │
     │                 │                │                 │                │    delivery    │                │
     │                 │                │                 │                │    status to   │                │
     │                 │                │                 │                │    Kafka       │                │
     │                 │                │<───────────────────────────────┤                │                │
     │                 │                │                 │                │                │                │
```

### PlantUML Sequence

```plantuml
@startuml Transactional_Notification
!theme plain

actor User
participant "User Directory\nService" as UDS
participant "Kafka" as K
participant "Notification\nAPI Service" as NAPI
participant "Notification\nProcessor" as NPROC
participant "Preference\nService" as PREF
participant "Template\nService" as TMPL
participant "SendGrid" as SG

User -> UDS : POST /users/me/password-reset
UDS -> UDS : generate reset token, store in DB
UDS -> K : publish tolox.user.password_reset\n{userId, resetLink, email}

== Intake ==
K -> NAPI : consume / gRPC SendNotification
NAPI -> TMPL : GET /template/{id}/variables\n(validate templateCode + variables)
TMPL --> NAPI : aggregatedVariables OK
NAPI -> NAPI : check idempotency (Redis dedup hash)
NAPI -> NAPI : normalize → NotificationEvent
NAPI -> K : publish notification.events.raw\n(partitioned by userId)
NAPI -> NAPI : log to request_logs (ACCEPTED)

== Processing ==
K -> NPROC : consume notification.events.raw
NPROC -> NPROC : 1. deduplicate (check delivery_history)
NPROC -> PREF : 2. POST /internal/preferences/evaluate\n{userId, PASSWORD_RESET, EMAIL}
PREF --> NPROC : {deliver: true, reason: MANDATORY}
NPROC -> TMPL : 3. POST /template/render\n{appCode, templateCode, EMAIL, am, data}
TMPL --> NPROC : {subject: "...", body: "<html>..."}
NPROC -> SG : 4. dispatch email via SendGrid adapter
SG --> NPROC : 202 Accepted (messageId: sg-123)
NPROC -> NPROC : 5. update delivery_history\n(status=SENT, provider_message_id=sg-123)
NPROC -> K : 6. publish notification.delivery.status

@enduml
```

---

## 2. MFA OTP Delivery Flow

Authentication Service triggers an SMS OTP during login. This is a **mandatory, high-priority** notification.

```plantuml
@startuml MFA_OTP_Flow
!theme plain

participant "Authentication\nService" as AUTH
participant "Kafka" as K
participant "Notification\nAPI Service" as NAPI
participant "Notification\nProcessor" as NPROC
participant "Preference\nService" as PREF
participant "Template\nService" as TMPL
participant "Twilio" as TW

AUTH -> AUTH : generate TOTP/OTP code
AUTH -> AUTH : store MFA state in Redis\n(mfa:pending:{authRequestId})

AUTH -> K : publish tolox.auth.mfa.triggered\n{userId, otpCode, channel: SMS, expiresIn: 300}

K -> NAPI : consume event
NAPI -> NAPI : validate + normalize\n(priority: HIGH)
NAPI -> K : publish notification.events.raw

K -> NPROC : consume (HIGH priority first)
NPROC -> NPROC : deduplicate
NPROC -> PREF : evaluate {MFA_OTP_DELIVERY, SMS}
PREF --> NPROC : {deliver: true, reason: MANDATORY}

note over PREF : MFA_OTP is mandatory.\nQuiet hours, opt-out, blocks\nare ALL overridden.

NPROC -> TMPL : render {TOLO_AUTH, MFA_OTP_DELIVERY, SMS, am,\n{otpCode: 123456, expiresInMinutes: 5}}
TMPL --> NPROC : "ኮድዎ 123456 ነው። በ5 ደቂቃ ውስጥ ያስገቡ።"
NPROC -> TW : send SMS via Twilio
TW --> NPROC : messageId: tw-456
NPROC -> NPROC : record delivery_history (SENT)

@enduml
```

---

## 3. Preference Evaluation — Full Decision Tree

This shows every decision branch the Preference Service evaluates.

```
                          ┌─────────────────────┐
                          │  Evaluate Request    │
                          │  userId + typeCode   │
                          │  + channel + time    │
                          └──────────┬──────────┘
                                     │
                          ┌──────────▼──────────┐
                          │ Load notification_   │
                          │ type from L1 cache   │
                          └──────────┬──────────┘
                                     │
                          ┌──────────▼──────────┐
                     ┌────┤ is_mandatory = true? ├────┐
                     │YES └─────────────────────┘ NO  │
                     │                                 │
              ┌──────▼──────┐               ┌──────────▼──────────┐
              │  ✅ SEND     │               │ Load user_channel_  │
              │  (MANDATORY) │               │ settings from Redis │
              └─────────────┘               └──────────┬──────────┘
                                                       │
                                            ┌──────────▼──────────┐
                                       ┌────┤ legal_opt_out?      ├────┐
                                       │YES └─────────────────────┘ NO  │
                                       │                                │
                                ┌──────▼──────┐              ┌─────────▼─────────┐
                                │  ❌ BLOCK    │              │ is_blocked for    │
                                │  (LEGAL_     │         ┌────┤ this channel?     ├────┐
                                │   OPTOUT)    │         │YES └───────────────────┘ NO  │
                                └─────────────┘         │                               │
                                                 ┌──────▼──────┐             ┌──────────▼──────────┐
                                                 │  ❌ BLOCK    │             │ Is it quiet hours   │
                                                 │  (CHANNEL_   │        ┌────┤ for user timezone?  ├────┐
                                                 │   BLOCKED)   │        │YES └───────────────────── ┘ NO │
                                                 └─────────────┘        │                                │
                                                                 ┌──────▼──────┐          ┌──────────────▼────────┐
                                                                 │  ⏸️ DELAY   │          │ Load user_overrides   │
                                                                 │  (QUIET_    │          │ from Redis            │
                                                                 │   HOURS)    │     ┌────┤ override exists?      ├────┐
                                                                 └─────────────┘     │YES └───────────────────────┘ NO │
                                                                                     │                                │
                                                                              ┌──────▼──────┐          ┌──────────────▼────────┐
                                                                              │ Return      │          │ Check frequency       │
                                                                              │ override    │          │ counter in Redis      │
                                                                              │ .is_enabled │     ┌────┤ exceeded today?       ├────┐
                                                                              │ (USER_      │     │YES └───────────────────────┘ NO │
                                                                              │  OVERRIDE)  │     │                                │
                                                                              └─────────────┘     │                                │
                                                                                           ┌──────▼──────┐          ┌──────────────▼─────┐
                                                                                           │  ❌ BLOCK    │          │ Return default_    │
                                                                                           │  (FREQUENCY_ │          │ enabled from       │
                                                                                           │   LIMIT)     │          │ notification_type  │
                                                                                           └──────────────┘          │ (DEFAULT)          │
                                                                                                                     └────────────────────┘
```

---

## 4. User Registration — Preference Initialization + Welcome Email

When a new user registers, the User Directory Service triggers both preference setup and a welcome email.

```plantuml
@startuml User_Registration_Notification
!theme plain

participant "User Directory\nService" as UDS
participant "Kafka" as K
participant "Preference\nService" as PREF
participant "Notification\nAPI Service" as NAPI
participant "Notification\nProcessor" as NPROC
participant "Template\nService" as TMPL
participant "SendGrid" as SG

== User Created ==
UDS -> K : publish tolox.user.created\n{userId, email, name, locale}

== Preference Initialization ==
K -> PREF : consume tolox.user.created
PREF -> PREF : INSERT 3 rows into user_channel_settings\n(EMAIL, SMS, PUSH) with defaults\ntimezone = 'Africa/Addis_Ababa'

note over PREF : No notification overrides created.\nAll defaults come from the\nnotification_type registry.

== Welcome Email ==
K -> NAPI : consume tolox.user.created\n(or Auth Service calls gRPC SendNotification)
NAPI -> NAPI : normalize → NotificationEvent\n(WELCOME_EMAIL, EMAIL)
NAPI -> K : publish notification.events.raw

K -> NPROC : consume event
NPROC -> PREF : evaluate {WELCOME_EMAIL, EMAIL}
PREF --> NPROC : {deliver: true, reason: DEFAULT}
NPROC -> TMPL : render {TOLO_AUTH, WELCOME_EMAIL, EMAIL, am,\n{userName: "ምንዳሁን"}}
TMPL --> NPROC : rendered email HTML
NPROC -> SG : send via SendGrid
SG --> NPROC : 202
NPROC -> NPROC : delivery_history (SENT)

@enduml
```

---

## 5. User Changes Notification Preferences

Shows the cache invalidation chain when a user opts out of a notification type.

```plantuml
@startuml Preference_Change
!theme plain

actor User
participant "tolo-x.com\n(Frontend)" as FE
participant "API Gateway" as GW
participant "Preference\nService" as PREF
participant "Redis" as RD
participant "PostgreSQL" as PG
participant "Kafka" as K

User -> FE : toggle OFF "Order Confirmations" for EMAIL
FE -> GW : PUT /users/{userId}/notifications/ORDER_CONFIRMED\n{enabled: false}
GW -> PREF : route to Preference Service

== Database Update ==
PREF -> PG : UPSERT user_notification_overrides\n(userId, ORDER_CONFIRMED, is_enabled=false)
PREF -> PG : INSERT user_consent_history\n(USER, override, old=true, new=false)

== Cache Invalidation ==
PREF -> RD : DEL user:overrides:{userId}
PREF -> RD : DEL user:channel:{userId}

== Event Publishing ==
PREF -> K : publish notification.preference.changed\n{userId, typeCode: ORDER_CONFIRMED, enabled: false}

== Eligibility Update ==
PREF -> PG : UPDATE user_notification_eligibility\n(userId, ORDER_CONFIRMED, is_enabled=false)

PREF --> GW : 200 {updated: true}
GW --> FE : 200
FE --> User : "Preference saved ✓"

@enduml
```

---

## 6. Campaign Execution Flow

Shows how a bulk campaign fans out into individual notifications without overwhelming the system.

```plantuml
@startuml Campaign_Execution
!theme plain

actor Admin
participant "Campaign\nService" as CAMP
participant "User Directory\nService" as UDS
participant "Preference DB\n(Eligibility)" as ELIG
participant "Notification\nAPI Service" as NAPI
participant "Kafka" as K
participant "Notification\nProcessor" as NPROC

Admin -> CAMP : POST /admin/campaigns/{id}/execute

== Audience Resolution ==
CAMP -> ELIG : SELECT user_id FROM user_notification_eligibility\nWHERE notification_type_id = ? AND is_enabled = true
ELIG --> CAMP : 50,000 user IDs

CAMP -> CAMP : batch into chunks of 500

== Traffic-Shaped Fan-Out ==
loop for each batch (rate limited: 100/sec)
    CAMP -> NAPI : POST /v1/notifications/batch\n(500 individual events)
    NAPI -> NAPI : validate + normalize each
    NAPI -> K : publish 500 events to\nnotification.events.raw

    CAMP -> CAMP : update campaign progress\n(processed_count += 500)
end

== Parallel Processing ==
K -> NPROC : consumer group processes events\n(horizontally scaled instances)

note over NPROC : Each event goes through the full\npipeline: dedup → preference → render → send.\nSome may be blocked by quiet hours\nor user overrides.

NPROC -> NPROC : update delivery_history per event
NPROC -> K : publish delivery status events

== Progress Tracking ==
CAMP -> CAMP : consume delivery status events\nupdate success_count / failure_count

Admin -> CAMP : GET /admin/campaigns/{id}\n→ {status: RUNNING, processed: 45000/50000}

@enduml
```

---

## 7. Provider Failure — Retry + DLQ Flow

Shows what happens when an external provider (SendGrid, Twilio) fails.

```plantuml
@startuml Provider_Failure
!theme plain

participant "Notification\nProcessor" as NPROC
participant "SendGrid" as SG
participant "Circuit\nBreaker" as CB
participant "Kafka DLQ" as DLQ
participant "delivery_history\nDB" as DB

NPROC -> NPROC : event consumed, dedup OK, pref OK, rendered OK

== Attempt 1 ==
NPROC -> CB : check circuit state
CB --> NPROC : CLOSED (healthy)
NPROC -> SG : send email
SG --> NPROC : 503 Service Unavailable
NPROC -> DB : update status = RETRYING, retry_count = 1

== Attempt 2 (after 1s backoff) ==
NPROC -> SG : retry send
SG --> NPROC : 503 Service Unavailable
NPROC -> DB : retry_count = 2

== Attempt 3 (after 2s backoff) ==
NPROC -> SG : retry send
SG --> NPROC : 503 Service Unavailable
NPROC -> DB : retry_count = 3

note over CB : Failure threshold reached.\nCircuit breaker OPENS.

== Attempt 4 (after 4s backoff) ==
NPROC -> CB : check circuit state
CB --> NPROC : OPEN (provider down)

note over NPROC : Circuit open — do not attempt.\nRoute to DLQ.

== DLQ ==
NPROC -> DLQ : publish to notification.events.dlq\n{eventId, reason: CIRCUIT_OPEN, retryCount: 3}
NPROC -> DB : update status = DLQ

note over DLQ : Events sit in DLQ until:\n1. Admin manually reprocesses\n2. Circuit breaker closes (HALF_OPEN)\n   and auto-retry picks up

@enduml
```

---

## 8. User Deletion — PDPP Cascade

When a user exercises their right to deletion under PDPP, all notification preferences and history are cleaned.

```plantuml
@startuml User_Deletion_PDPP
!theme plain

participant "User Directory\nService" as UDS
participant "Kafka" as K
participant "Preference\nService" as PREF
participant "Redis" as RD
participant "PostgreSQL\n(Pref DB)" as PG
participant "Notification\nProcessor" as NPROC

UDS -> K : publish tolox.user.deleted\n{userId, reason: PDPP_REQUEST}

== Preference Cleanup ==
K -> PREF : consume tolox.user.deleted
PREF -> PG : DELETE FROM user_channel_settings WHERE user_id = ?
PREF -> PG : DELETE FROM user_notification_overrides WHERE user_id = ?
PREF -> PG : DELETE FROM user_notification_eligibility WHERE user_id = ?
PREF -> PG : INSERT INTO user_consent_history\n{userId, SYSTEM, "ACCOUNT_DELETED"}
PREF -> RD : DEL user:channel:{userId}
PREF -> RD : DEL user:overrides:{userId}

== Future Notification Block ==
note over NPROC : Any future event for this userId\nwill hit Preference Service →\nno user_channel_settings found →\nfail-closed for non-mandatory.\nMandatory notifications also fail\nbecause email/phone are anonymized\nin User Directory.

@enduml
```

---

## 9. Template Publishing — Cache Invalidation Chain

```plantuml
@startuml Template_Publish
!theme plain

actor Admin
participant "Template\nService" as TMPL
participant "PostgreSQL\n(Template DB)" as PG
participant "Redis" as RD
participant "All Processor\nInstances" as NPROC

Admin -> TMPL : PATCH /template/{id}/publish/3

== Database ==
TMPL -> PG : UPDATE template_versions SET status = 'PUBLISHED'\nWHERE template_id = ? AND version_number = 3
TMPL -> PG : UPDATE templates SET active_version_number = 3\nWHERE id = ?

== L2 Cache Eviction ==
TMPL -> RD : DEL template:content:{appCode}:{templateCode}:*
TMPL -> RD : DEL template:registry:{appCode}:{templateCode}

== L1 Cache Invalidation ==
TMPL -> RD : PUBLISH template.cache.invalidate\n{appCode, templateCode, newVersion: 3}

note over NPROC : All Processor instances subscribe\nto Redis Pub/Sub channel.\nThey evict their local L1 cache\nfor this template.

RD -> NPROC : receive pub/sub message
NPROC -> NPROC : evict L1 entry for\n{appCode, templateCode}

note over NPROC : Next render request will:\n1. Read L1 → MISS (version changed)\n2. Read L2 → MISS (evicted)\n3. Read DB → load version 3\n4. Populate L2 → populate L1

@enduml
```

---

## 10. Notification Lifecycle Status Machine

Every notification transitions through these states:

```
                    ┌──────────┐
                    │ ACCEPTED │  (API Service logs request)
                    └────┬─────┘
                         │
                    ┌────▼─────┐
               ┌────┤ PENDING  ├────┐
               │    └──────────┘    │
               │                    │
          (preference              (preference
           = SEND)                  = BLOCK)
               │                    │
          ┌────▼─────┐        ┌────▼──────┐
          │ RENDERING│        │  BLOCKED  │  (terminal)
          └────┬─────┘        └───────────┘
               │
          ┌────▼─────┐
          │ RENDERED │
          └────┬─────┘
               │
          ┌────▼─────┐
     ┌────┤ SENDING  ├────┐
     │    └──────────┘    │
     │                    │
  (provider             (provider
   success)              failure)
     │                    │
┌────▼─────┐        ┌────▼──────┐
│   SENT   │        │ RETRYING  │
│(terminal)│        └────┬──────┘
└──────────┘             │
                    ┌────┴──────┐
               ┌────┤ max       ├────┐
               │    │ retries?  │    │
               │NO  └───────────┘ YES│
               │                     │
          (back to              ┌────▼─────┐
           SENDING)             │   DLQ    │  (terminal, manual intervention)
                                └──────────┘

                    ┌──────────┐
                    │  DELAYED │  (quiet hours → re-queue with delay)
                    └────┬─────┘
                         │
                    (after delay expires)
                         │
                    ┌────▼─────┐
                    │ PENDING  │  (re-enters pipeline)
                    └──────────┘
```

---

## 11. Cross-System Event Map

Complete map of all Kafka events flowing into and out of the Notification System.

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                     INBOUND EVENTS (consumed by Notification System)       ║
╠══════════════════════════════╦═══════════════════════╦══════════════════════╣
║ Kafka Topic                  ║ Source Service         ║ Triggers             ║
╠══════════════════════════════╬═══════════════════════╬══════════════════════╣
║ tolox.auth.login.failed      ║ Authentication        ║ LOGIN_FAILED_ALERT   ║
║ tolox.auth.mfa.triggered     ║ Authentication        ║ MFA_OTP_DELIVERY     ║
║ tolox.auth.magic_link        ║ Authentication        ║ MAGIC_LINK           ║
║ tolox.user.created           ║ User Directory        ║ WELCOME_EMAIL +      ║
║                              ║                       ║ Preference init      ║
║ tolox.user.email_verification║ User Directory        ║ EMAIL_VERIFICATION   ║
║ tolox.user.password_reset    ║ User Directory        ║ PASSWORD_RESET       ║
║ tolox.user.password_changed  ║ User Directory        ║ PASSWORD_CHANGED_    ║
║                              ║                       ║ ALERT                ║
║ tolox.user.deleted           ║ User Directory        ║ Preference cleanup   ║
║ tolox.session.revoked        ║ Session Service       ║ SESSION_REVOKED_     ║
║                              ║                       ║ ALERT                ║
║ tolox.token.family_compromised║ Authorization        ║ TOKEN_COMPROMISE_    ║
║                              ║                       ║ ALERT                ║
║ tolox.consent.granted        ║ Consent Service       ║ CONSENT_CONFIRMATION ║
╠══════════════════════════════╩═══════════════════════╩══════════════════════╣
║                                                                            ║
║                     INTERNAL EVENTS (within Notification System)           ║
╠══════════════════════════════╦═══════════════════════╦══════════════════════╣
║ notification.events.raw      ║ Notification API      ║ → Processor          ║
║ notification.events.dlq      ║ Processor             ║ → Manual reprocess   ║
║ notification.delivery.status ║ Processor             ║ → Audit & Event Svc  ║
║ notification.preference.     ║ Preference Service    ║ → Processor (cache   ║
║   changed                    ║                       ║   invalidation)      ║
║ campaign.events.batch        ║ Campaign Service      ║ → Notification API   ║
╚══════════════════════════════╩═══════════════════════╩══════════════════════╝
```

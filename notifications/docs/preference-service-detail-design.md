# Preference Service – Final Detailed Design & Implementation Guide (v2)

---

# 1. Purpose

The Preference Service is the **policy resolution engine** that determines whether a notification should be:

- SENT
- BLOCKED
- DELAYED
- FORCED (mandatory)

It acts as:

- Compliance layer
- Delivery gatekeeper
- User control authority

The system must be:

- Deterministic
- Horizontally scalable
- Cache-efficient
- Campaign-safe
- Compliance-ready

---

# 2. Core Data Model

## 2.1 notification_type (Registry Layer)

Represents atomic notification behavior.

Fields:

- id (PK)
- code (UNIQUE)
- app_id
- channel (SMS | EMAIL | PUSH)
- category (SECURITY | TRANSACTIONAL | MARKETING | SOCIAL)
- default_enabled (boolean)
- is_mandatory (boolean)
- max_frequency_per_day (int, nullable)
- cooldown_seconds (int, nullable)
- status (ACTIVE | DEPRECATED)
- created_at
- deprecated_at (nullable)

Rules:

- One row per semantic notification + channel.
- Never hard-delete.
- Cached fully in application memory (L1).
- Rarely changes.

---

## 2.2 user_channel_settings (Global Layer)

Fields:

- user_id
- channel
- is_blocked
- legal_opt_out
- quiet_hours_start
- quiet_hours_end
- timezone
- consent_version (nullable)
- consent_timestamp (nullable)
- PRIMARY KEY (user_id, channel)

Rules:

- Inserted at user registration.
- Exactly one row per channel per user.
- legal_opt_out overrides everything except mandatory.

---

## 2.3 user_notification_overrides (Sparse Layer)

Fields:

- user_id
- notification_type_id
- is_enabled
- consent_version (nullable)
- consent_timestamp (nullable)
- updated_at
- PRIMARY KEY (user_id, notification_type_id)

Rules:

- Created only when user changes preference.
- Most users have zero rows.
- Must be indexed properly.

---

## 2.4 user_consent_history (Mandatory in regulated regions)

Fields:

- user_id
- notification_type_id (nullable for channel-level)
- old_value
- new_value
- policy_version
- changed_at
- source (UI | API | ADMIN)

Used for:

- GDPR / CCPA audits
- Compliance investigations
- Legal proof of consent

---

# 3. Decision Engine (Rule-Based)

Precedence must be explicit and extensible.

Evaluation order:

1. MandatoryRule
2. LegalOptOutRule
3. GlobalBlockRule
4. QuietHoursRule
5. UserOverrideRule
6. DefaultRule

Example structure:

```python
rules = [
    MandatoryRule(),
    LegalOptOutRule(),
    GlobalBlockRule(),
    QuietHoursRule(current_time),
    UserOverrideRule(overrides),
    DefaultRule()
]

for rule in rules:
    decision = rule.apply(context)
    if decision.is_decisive:
        return decision
```
Benefits:
- Easy extension
- Easy testing
- No nested business logic

## 4. Caching Strategy (Optimized & Production Safe)

The caching strategy is designed to minimize database load, reduce latency, and keep preference resolution fast under high traffic.

---

### L1 Cache – Application Memory Registry Cache

Cache the entire `notification_type` registry in application memory.

Reason:
- Table size is small
- Data changes infrequently
- Eliminates database lookup per request

---

### L2 Cache – Redis Split Cache Design

Do NOT store fully resolved preference blobs.

Use two Redis keys.

---

#### Channel Settings Cache
user:channel:{user_id}  
Stores:
- is_blocked
- legal_opt_out
- quiet_hours_start
- quiet_hours_end
- timezone

Stored as Redis Hash.

---

#### Override Cache
user:overrides:{user_id}

Stores:
notification_type_id → is_enabled

Stored as Redis Hash.

---

### Cache Resolution Flow

1. Read notification registry from memory cache
2. Fetch user channel settings from Redis
3. Fetch user overrides from Redis
4. Apply rule engine precedence

Example logic:

```python
channel_data = redis.hgetall(f"user:channel:{user_id}")
overrides = redis.hgetall(f"user:overrides:{user_id}") or {}

nt = registry[notification_code]

if nt.is_mandatory:
    return SEND

if channel_data.get("legal_opt_out"):
    return BLOCK

if is_quiet_hour(now, channel_data):
    return DELAY

if nt.id in overrides:
    return overrides[nt.id]

return nt.default_enabled
```

### Cache Invalidation
On preference change:
- Update database
- Publish preference change event
- Delete Redis keys:
  - user:channel:{user_id}
  - user:overrides:{user_id}

## 5. Lifecycle Scenarios

---

### New User Registration

When a new user registers:

Required Inserts:
- 3 rows into user_channel_settings (SMS, EMAIL, PUSH)

Do NOT:
- Create notification override rows
- Precompute eligibility states
- Copy default preferences into user tables

Preference resolution must always be dynamic using registry + rule engine.

---

### New App or New Notification Type Added

When a new app or notification type is added:

Only insert new rows into notification_type table.

Do NOT:
- Modify user preference tables
- Run bulk migration jobs
- Create per-user preference rows

The system automatically applies default behavior using:
- default_enabled
- is_mandatory

---

### User Preference Change

When user updates preferences:

Step 1:
Update user_notification_overrides table

Step 2:
Publish preference update event to event bus

Step 3:
Invalidate Redis cache keys:
- user:channel:{user_id}
- user:overrides:{user_id}

---

## 6. Campaign Processing at Scale

Never process notifications using per-user loops.

Always use batch or set-based database queries.

Preferred Optimization:

Optional eligibility materialization table:

user_notification_eligibility
Columns:
- user_id
- notification_type_id
- is_enabled

Update Triggers:
- Preference changes
- Policy default updates

Campaign Query Example (SQL Style):
```sql
SELECT u.user_id
FROM user_notification_eligibility u
JOIN user_channel_settings c
ON u.user_id = c.user_id
WHERE u.notification_type_id = ?
AND u.is_enabled = true
AND (
    c.quiet_hours_start IS NULL
    OR NOT is_quiet_hour(
        NOW(),
        c.timezone,
        c.quiet_hours_start,
        c.quiet_hours_end
    )
)

```
---

## 7. Frequency Control Strategy

Implement frequency control using notification_type fields:

- max_frequency_per_day
- cooldown_seconds

Enforce using:
- Send event logs
- Distributed rate counters

Prevents:
- Spam notifications
- Regulatory violations
- User notification fatigue

---

## 8. Failure Handling Strategy

Preference Service failure behavior must be category aware.

Security Notifications:
→ Send mandatory notifications only

Transactional Notifications:
→ Use last cached preference state

Marketing Notifications:
→ Fail closed (do not send)

This behavior must be configurable per category.

---

## 9. Indexing Requirements

Required Indexes:

user_notification_overrides:
- (user_id, notification_type_id)

user_channel_settings:
- (user_id, channel)

notification_type:
- (code)

user_notification_eligibility:
- (notification_type_id, is_enabled)

Indexes are mandatory for campaign performance.

---

## 10. System Characteristics

This design ensures:

- Storage growth proportional to users + overrides only
- New apps require zero database migration
- Campaign queries remain SQL optimized
- Cache usage remains efficient
- Compliance auditing is supported
- Decision rules are extensible

---

## 11. Future Extension Capabilities

System can later support:

- Region compliance policy engine
- Advanced rollout strategies
- Timezone-based campaign schedulers
- Cross shard eligibility computation

These can be added without redesigning core schema.

---

END
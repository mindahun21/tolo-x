# Preference Service API Contract

This document defines the API contracts for the Notification Preference Service, covering Internal, Admin, and User-facing endpoints.

## Base Architecture Contracts

- **Internal Processor Service APIs**: `/internal/preferences`
- **User Preference APIs**: `/users/{userId}`
- **Admin APIs**: `/admin/preferences`

---

## 1. Decision Evaluation API (Core Endpoint)

Evaluate whether a notification should be delivered based on type, channel, and user settings.

### Evaluate Notification Delivery
`POST /internal/preferences/evaluate`

#### Request Example
```json
{
  "userId": "12345",
  "notificationTypeCode": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "currentTime": "2026-02-23T12:00:00Z"
}
```

#### Response Example (Success)
```json
{
  "deliver": true,
  "reason": "DEFAULT_ENABLED"
}
```

#### Response Example (Blocked)
```json
{
  "deliver": false,
  "reason": "QUIET_HOURS"
}
```

#### Decision Reasons:
- `MANDATORY`
- `LEGAL_OPTOUT`
- `CHANNEL_BLOCKED`
- `QUIET_HOURS`
- `USER_OVERRIDE`
- `DEFAULT`
- `FREQUENCY_LIMIT`

---

## 2. Notification Registry APIs (Admin Layer)

Represents global notification behavior definitions.

### Create Notification Type
`POST /admin/notification-types`

#### Request Example
```json
{
  "code": "PAYMENT_SUCCESS",
  "appId": "PAYMENT_APP",
  "channel": "EMAIL",
  "category": "TRANSACTIONAL",
  "defaultEnabled": true,
  "mandatory": false,
  "maxFrequencyPerDay": 5,
  "cooldownSeconds": 300,
  "status": "ACTIVE"
}
```

#### Response Example
```json
{
  "id": 1,
  "code": "PAYMENT_SUCCESS",
  "status": "ACTIVE",
  "createdAt": "2026-02-23T10:00:00Z"
}
```

### Update Notification Type
`PUT /admin/notification-types/{code}`

#### Request Example
```json
{
  "defaultEnabled": true,
  "mandatory": false,
  "maxFrequencyPerDay": 10
}
```

#### Response Example
```json
{
  "updated": true
}
```

### Deprecate Notification Type
`PATCH /admin/notification-types/{code}/deprecate`

#### Response
```json
{
  "deprecated": true
}
```

### Get Notification Types
`GET /admin/notification-types`

#### Response Example
```json
[
  {
    "code": "PAYMENT_SUCCESS",
    "channel": "EMAIL",
    "defaultEnabled": true
  }
]
```

---

## 3. User Channel Preference APIs

### Get User Channel Preferences
`GET /users/{userId}/channels`

#### Response Example
```json
[
  {
    "channel": "EMAIL",
    "blocked": false,
    "legalOptOut": false,
    "quietStart": "22:00",
    "quietEnd": "07:00",
    "timezone": "Africa/Addis_Ababa"
  }
]
```

### Update Channel Preference
`PUT /users/{userId}/channels/{channel}`

#### Request Example
```json
{
  "blocked": false,
  "quietStart": "22:00",
  "quietEnd": "06:00",
  "timezone": "Africa/Addis_Ababa"
}
```

#### Response
```json
{
  "updated": true
}
```

### Legal Opt-Out
`PATCH /users/{userId}/channels/{channel}/legal-opt-out`

#### Request
```json
{
  "value": true
}
```

#### Response
```json
{
  "updated": true
}
```

---

## 4. User Notification Override APIs

### Get User Overrides
`GET /users/{userId}/notifications`

#### Response
```json
[
  {
    "notificationTypeCode": "ORDER_CONFIRMED",
    "enabled": false
  }
]
```

### Create / Update Override
`PUT /users/{userId}/notifications/{notificationTypeCode}`

#### Request
```json
{
  "enabled": false
}
```

#### Response
```json
{
  "updated": true
}
```

### Delete Override
`DELETE /users/{userId}/notifications/{notificationTypeCode}`

#### Response
```json
{
  "deleted": true
}
```

---

## 5. Channel Priority APIs

### Set Channel Priority
`PUT /users/{userId}/channel-priority`

#### Request
```json
{
  "channels": ["SMS", "EMAIL", "PUSH"]
}
```

#### Response
```json
{
  "updated": true
}
```

---

## 6. Audit APIs

### Get Preference History
`GET /users/{userId}/preferences/history`

#### Response
```json
[
  {
    "eventType": "OVERRIDE_UPDATED",
    "changedBy": "USER",
    "timestamp": "2026-02-23T10:00:00Z"
  }
]
```

---

## 7. Internal Context APIs

### Get Effective Context
`POST /internal/preferences/effective`

#### Request
```json
{
  "userId": "uuid",
  "notificationTypeCode": "ORDER_CONFIRMED"
}
```

#### Response
```json
{
  "effectiveChannel": "EMAIL",
  "allowed": true
}
```

---

## 8. Cache Design

### Redis Keys
- `notif:type:{code}`
- `user:channel:{userId}:{channel}`
- `user:override:{userId}:{notificationTypeId}`

### TTL Strategy
- **Registry**: Long TTL (e.g., 24h or indefinite until update)
- **User Preferences**: Invalidate/Update on write operations

---

## 9. Security Rules

- **Admin APIs**: `ADMIN` role required.
- **Internal APIs**: Restricted to internal service communication (e.g., Processor service only).
- **User APIs**: Authenticated user token matching `userId` required.

---

## 10. Performance Targets

- Decision evaluation latency: `< 10ms`
- Read strategy: Cache-first
- Rule engine: Stateless and highly scalable

# Template Service: API Contract

This document defines the API benchmarks for the Template Service, covering the high-throughput rendering API (Internal) and the management APIs (Admin).

---

## 1. Internal Rendering API (Internal Gateway)
Used by the **Notification Processor Service** to fetch and render content.

### Render Template
`POST /internal/templates/render`

#### Request Schema
```json
{
  "applicationCode": "TOLO_PARK",
  "templateCode": "BOOKING_CONFIRMED",
  "channel": "EMAIL",
  "locale": "en_US",
  "data": {
    "userName": "John Doe",
    "bookingRef": "ABC-123",
    "expiryDate": "2026-04-01"
  }
}
```

#### Response (Success)
```json
{
  "subject": "Booking Confirmed: ABC-123",
  "body": "<html>Hello John Doe, your booking...</html>",
  "versionNumber": 4,
  "locale": "en_US",
  "renderedAt": "2026-03-03T12:00:00Z"
}
```

#### Error Responses
*   `404 NOT_FOUND`: Template or version does not exist.
*   `400 BAD_REQUEST`: Missing mandatory variables for rendering.
*   `422 UNPROCESSABLE_ENTITY`: Template syntax error (e.g., broken Handlebars tags).

---

## 2. Template Registry APIs (Admin)
Manage the logical templates (the "containers").

### Create Template
`POST /admin/templates`
*   **Request**: `{ "applicationCode": "STRING", "templateCode": "STRING", "description": "STRING" }`

### List All Templates
`GET /admin/templates?appCode=TOLO_PARK`

### Get Template Details
`GET /admin/templates/{id}`

---

## 3. Version Management APIs (Admin)
Manage the immutable content snapshots.

### Create New Version (Draft)
`POST /admin/templates/{templateId}/versions`
*   **Request**:
```json
{
  "channel": "EMAIL",
  "locale": "en",
  "subject": "Hello {{userName}}",
  "body": "Welcome to Tolo-X!",
  "engine": "HANDLEBARS"
}
```
*   **Note**: This automatically increments the version number but sets `status=DRAFT`.

### Get Particular Version
`GET /admin/templates/{templateId}/versions/{versionNumber}`

### Publish a Version
`PATCH /admin/templates/{templateId}/publish/{versionNumber}`
*   **Action**: Updates the Template's `activeVersionNumber` and triggers cache invalidation across all pods.

### Rollback to Previous Version
`PATCH /admin/templates/{templateId}/rollback/{versionNumber}`
*   **Action**: Same as Publish, but typically used to move backwards in history.

---

## 4. Localization Fallback Rules
When a request for `locale=en_US` arrives:
1.  Search for exactly `en_US`.
2.  Search for `en`.
3.  Search for the system default (e.g., `en`).
4.  If still missing, return `404`.

---

## 5. Performance Targets
*   **Render Latency**: Target `< 10ms` for cache hits.
*   **Throughput**: Scale to `5000+ RPS` via reactive non-blocking architecture.
*   **Availability**: Fallback to DB during Redis downtime must not exceed `50ms` latency.

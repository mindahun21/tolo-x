# Notification API Service: Detailed Design

The **Notification API Service** is the reactive entry point for all notification requests. It transforms HTTP requests into high-quality Kafka events.

---

## 1. Technical Stack
- **Framework**: Spring Boot 3.x (WebFlux)
- **Programming Model**: Functional Reactive Programming (Project Reactor)
- **Database**: PostgreSQL (R2DBC) - For request auditing and idempotency logs.
- **Messaging**: Kafka (Reactor Kafka)
- **Client**: Spring WebClient (to communicate with Template Service)
- **API Documentation**: OpenAPI / Swagger

---

## 2. API Endpoints

### `POST /v1/notifications` (The Core Entry Point)
Accepts a notification request and returns a tracking ID.

**Request Schema:**
```json
{
  "applicationCode": "TOLO_AUTH",
  "templateCode": "WELCOME_EMAIL",
  "userId": "user-uuid-123",
  "channel": "EMAIL",
  "locale": "en",
  "data": {
    "userName": "Mindahun",
    "activationLink": "https://tolo.x/act/123"
  },
  "metadata": {
    "requestId": "unique-client-id-for-idempotency",
    "priority": "HIGH"
  }
}
```

**Response (Success - 202 Accepted):**
```json
{
  "eventId": "generated-uuid-456",
  "status": "ACCEPTED",
  "receivedAt": "2026-03-09T12:00:00Z"
}
```

---

## 3. Core Component Design

### `NotificationController`
- Handles the entry point.
- Maps DTOs to internal command objects.

### `IngressValidationService`
- **Template Check**: Calls Template Service to verify that the `templateCode` is valid for the `applicationCode`.
- **Variable Check**: Compares the variables provided in `data` against the `aggregatedVariables` returned by the Template Service.
- **Fail Fast**: If a required variable is missing, return `400 Bad Request` immediately.

### `IdempotencyService`
- Uses the `requestId` from metadata.
- Persists a hash of the request in the local PostgreSQL DB with a 24-hour expiration.
- If a duplicate is received, returns the original `eventId` without publishing to Kafka again.

### `EventProducer`
- Converts the validated request into a `NotificationEvent` (AVRO or JSON format).
- Publishes to Kafka topic `notification.raw.events`.
- **Partitioning**: Uses `userId` as the message key to maintain ordering per user.

---

## 4. Database Schema (Postgres)

**Table: `request_logs`**
- `id`: UUID (Primary Key)
- `client_request_id`: String (Unique Index - for idempotency)
- `application_code`: String
- `user_id`: String
- `status`: String (ACCEPTED, REJECTED)
- `error_reason`: String (nullable)
- `created_at`: Timestamp

---

## 5. Sequence Diagram (Logic Flow)
1. **Source** calls API.
2. **API Service** validates JSON schema.
3. **API Service** fetches Template Variables from **Template Service** (Cached).
4. **API Service** validates `data` payload against variables.
5. **API Service** records request in **local DB** (Idempotency check).
6. **API Service** publishes to **Kafka**.
7. **API Service** returns `202 Accepted` to Source.

---

## 6. Error Handling Strategy
- **400 Bad Request**: Invalid template, missing data, or malformed JSON.
- **401/403**: Invalid Application Key (Security Layer).
- **429 Too Many Requests**: Rate limit exceeded for that specific app.
- **503 Service Unavailable**: Kafka is down or Template Service is unreachable.

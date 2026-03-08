# Notification API Service Overview

## Why This Service Must Exist

The Notification API Service is the mandatory entry point for the entire notification ecosystem. It serves as a protective layer between upstream business applications (like Auth, Orders, Inventory) and the downstream delivery infrastructure.

In a mature distributed architecture, business services should never directly interact with messaging queues (Kafka) or a rendering engine. This service provides a clean, unified REST interface that handles the complexities of the notification lifecycle.

This service acts as:
- **Security Gatekeeper**: Verifies that the calling application has the authority to send notifications.
- **Validation Layer**: Rejects malformed or incomplete requests at the edge.
- **Event Normalization**: Standardizes diverse incoming payloads into a single "Notification Event" format.
- **Async Bridge**: Decouples the low-latency HTTP request from the high-throughput Kafka backbone.

---

## What This Service Must Do

### Request Validation & Logic
- **Application Identification**: Verify the `applicationCode` (e.g., TOLO_PARK, TOLO_AUTH).
- **Template Verification**: Ensure the requested `templateCode` exists and is valid for the given application.
- **Payload Validation**: Check that the business `data` provided contains all the variables required by the template (using the Variable Discovery API of the Template Service).
- **Fail Fast**: Reject requests before they reach Kafka if they are destined to fail later.

### Event Normalization
- Construct a standardized **Notification Event** object containing:
    - Unique `eventId` (UUID) for tracking and idempotency.
    - Application & Template context.
    - Recipient details (User ID, Channel, Locale).
    - Normalized business data map.
    - Metadata (High Priority vs. Bulk).

### Idempotency Protection
- Generate and store a request fingerprint to prevent the same notification from being sent twice if a client retries a successful request.

### Kafka Event Publishing
- Publish normalized events to the `notification.raw.events` topic.
- Use the `userId` as the Kafka partition key to ensure all notifications for a single user are processed in the correct order.

### Audit Logging
- Record the intake status of every request in the dedicated **Notification API Database**.

---

## What This Service Must Not Do

To maintain high performance and separation of concerns, this service **MUST NOT**:
- **Render Templates**: Content generation is handled by the Processor.
- **Fetch User Preferences**: This happens later in the Processor to ensure the most up-to-date data is used.
- **Call External Providers**: It never talks to Twilio, SendGrid, etc.
- **Execute Business Logic**: It doesn't decide *who* gets a notification, only *how* to accept the request.

---

## Scalability & Security
- **Stateless Design**: Allows for effortless horizontal scaling during traffic spikes.
- **Rate Limiting**: Protects downstream services from being flooded by a single malfunctioning or malicious application.
- **Application Isolation**: Ensures that one app (e.g., Marketing) cannot use templates belonging to another app (e.g., Auth).
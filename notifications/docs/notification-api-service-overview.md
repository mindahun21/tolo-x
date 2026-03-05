# Notification API Service Design

## Why This Service Must Exist

The Notification API Service is a mandatory system boundary component. It exists to protect downstream notification infrastructure and enforce consistent notification contracts across all producers.

In production notification systems, clients must never publish events directly to Kafka or messaging infrastructure.

This service acts as:
- Security gatekeeper
- Validation layer
- Event normalization layer
- Observability and audit entry point

Removing this component introduces security, reliability, and maintainability risks.

---

## What This Service Must Do

### Request Validation
The service must validate:
- Notification type existence
- Required payload fields
- Allowed channels
- Template availability

Invalid requests must be rejected before publishing events.

---

### Authentication and Authorization
The service must verify:
- Client identity
- Tenant permissions
- Service-level access rights

Unauthorized requests must be blocked.

---

### Business Rule Enforcement
The service must enforce:
- User notification preferences
- Channel restrictions
- Compliance rules
- Rate limits

These rules must be applied before event publishing.

---

### Event Normalization
The service must convert all incoming requests into a single standardized event format before sending to Kafka.

All downstream services must consume only standardized events.

---

### Idempotency Protection
The service must guarantee idempotent request handling.

Duplicate requests must not produce duplicate notifications.

Each request must generate or verify a unique event identifier.

---

### Kafka Event Publishing
The service must:
- Publish events asynchronously to Kafka
- Confirm publish success before responding to clients
- Use userId or tenantId as partition keys

The service must not perform notification delivery directly.

---

### Logging and Observability
The service must record:
- Request metadata
- Event publishing results
- Failure reasons

Audit traceability is mandatory.

---

## What This Service Must Not Do

The Notification API Service must NOT:

- Send notifications directly to providers
- Render notification templates
- Perform campaign segmentation
- Execute long-running business workflows
- Maintain provider-specific logic
- Contain channel delivery implementations

These responsibilities belong to downstream processing services.

---

## Security Requirements

The service must:
- Sanitize all inputs
- Enforce request rate limits
- Prevent unauthorized message publishing
- Support tenant isolation

Security enforcement must happen at the API boundary.

---

## Scalability Requirements

The service must be stateless.

The service must:
- Support horizontal scaling
- Handle high request concurrency
- Remain lightweight and fast

Stateful business processing is prohibited.

---

## Core Responsibility Statement

The Notification API Service is strictly responsible for:
- Validation
- Security enforcement
- Event normalization
- Kafka event publishing

Nothing else.
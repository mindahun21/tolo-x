# Template Service: Final Detailed Design & Implementation Guide

---

## 1. Purpose
The Template Service is a stateless, reactive content service responsible for managing the lifecycle of notification content. It decouples message presentation from business logic, allowing for dynamic rendering and instant content updates.

**Core Responsibilities:**
*   **Logical Management**: Grouping content variants under stable lookup keys (Application + Template Code).
*   **Immutability**: Treating specific versions of content as immutable for safety and audit.
*   **Dynamic Rendering**: Injecting runtime variables into templates (e.g., Handlebars).
*   **High Performance**: Serving rendering requests at sub-10ms latencies for the Notification Processor.

---

## 2. Scope & Boundaries
*   **Upstream**: Consumed by the **Notification Processor Service** via reactive REST.
*   **Downstream**: 
    *   **PostgreSQL**: Persistent storage for templates and versions (via R2DBC).
    *   **Redis**: High-speed cache for rendered content and active versions.
*   **Strict Boundary**: Does NOT handle user preferences, Kafka logic, or direct provider communication.

---

## 3. Domain Model

### 3.1 Template (Logical Registry)
Represents the "name" of a notification type within an application.
*   **Fields**: `id` (UUID), `applicationCode`, `templateCode`, `description`, `activeVersionNumber`, `createdAt`, `updatedAt`.
*   **Constraints**: `UNIQUE(applicationCode, templateCode)`.
*   **L1 Cache Target**: These records are small and change rarely, making them ideal for in-memory caching.

### 3.2 TemplateVersion (Immutable Content)
Represents the actual message body and subject for a specific version, channel, and locale.
*   **Fields**: `id`, `templateId`, `versionNumber`, `channel` (EMAIL, SMS, PUSH), `locale` (en, am, etc.), `subject`, `body`, `engine` (HANDLEBARS), `status` (DRAFT, PUBLISHED).
*   **Constraints**: `UNIQUE(templateId, versionNumber, channel, locale)`.
*   **Rule**: Content is never updated in-place. Edits result in a new `versionNumber`.

---

## 4. Layered Caching Strategy (Performance First)

To match the performance of the Preference Service, the Template Service employs a two-tier caching strategy.

### Tier 1: L1 In-Memory Cache (Registry)
*   **Target**: `Template` logical records.
*   **Strategy**: All active template names and their `activeVersionNumber` are loaded into application memory.
*   **Invalidation**: Near-real-time invalidation via Redis Pub/Sub when a new version is "Published."

### Tier 2: L2 Redis Cache (Content)
*   **Target**: `TemplateVersion` rendered objects.
*   **Key Format**: `template:content:{appCode}:{templateCode}:{channel}:{locale}:{vNumber}`
*   **Value**: Compressed JSON blob of the subject and body.
*   **TTL**: 24 hours (or indefinite until manually purged).

### Cache Flow:
1.  **Processor Request** arrives.
2.  **L1 Memory Check**: Find the `activeVersionNumber` for the requested Template Code.
3.  **L2 Redis Check**: Look for the specific version/channel/locale content.
4.  **DB Fallback**: If L2 misses, query PostgreSQL, hydrate L2, and return.

---

## 5. Versioning & Publication Lifecycle
1.  **Drafting**: Admin creates a new `TemplateVersion` with `status=DRAFT`.
2.  **Testing**: Version can be tested via internal APIs using the specific version number.
3.  **Publication**: 
    *   Set `status=PUBLISHED`.
    *   Update `Template.activeVersionNumber` in DB.
    *   **Evict Cache**: Trigger L1 and L2 cache invalidation for this template.
4.  **Rollback**: Simply point `Template.activeVersionNumber` back to a previous `PUBLISHED` version number and evict cache.

---

## 6. Rendering Architecture

### 7.1 Renderer Abstraction
*   The service uses a pluggable `TemplateRenderer` interface.
*   **Default Implementation**: `HandlebarsRenderer` (Reactive, non-blocking).

### 7.2 Rendering Algorithm
Input: `appCode`, `templateCode`, `channel`, `locale`, `dataMap`.
1.  **Resolve Version**: Determine the `activeVersion` from L1 cache.
2.  **Fetch Content**: Retrieve `TemplateVersion` from L2 or DB.
3.  **Locale Fallback**: If `en_US` is missing, try `en`, then fallback to the system default.
4.  **Variable Injection**: Compile the template with the provided `dataMap`.
5.  **Validation**: Ensure all mandatory placeholders were filled.

---

## 7. Storage Strategy
*   **Database**: PostgreSQL with **Spring Data R2DBC**.
*   **Reactive**: No JPA, no Hibernate, no blocking JDBC.
*   **Concurrency**: Uses optimistic locking (`@Version`) for Template logical records to prevent lost updates during concurrent edits.

---

## 8. Failure Handling & Resilience
*   **Redis Failure**: Service falls back to Postgres. Performance might degrade, but service remains available.
*   **Postgres Failure**: Critical failure. Service returns `503 Service Unavailable`.
*   **Missing Variables**: If the renderer identifies a missing required variable, it returns `400 Bad Request` with the specific field name.
*   **Fallback Content**: A "Default Fallback" template can be configured per application to prevent empty notifications.

---

## 9. Scalability Characteristics
*   **Read-Heavy**: 99% of traffic is rendering requests.
*   **Efficient Memory**: Only metadata is stored in L1, keeping heap usage low even with thousands of templates.
*   **Horizontally Scalable**: Service is completely stateless; scaling up pods linearly increases rendering capacity.

---

## 10. Design Guarantees
*   **Deterministic Rendering**: Given the same data and version, the output is always identical.
*   **Auditability**: Every change in content is captured as a new version.
*   **Zero-Downtime Updates**: Publishing a new version updates all pods instantly via the layered cache invalidation.

---

## 11. Database Schema (PostgreSQL)

```sql
-- Logical Template Registry
CREATE TABLE IF NOT EXISTS templates (
    id UUID PRIMARY KEY,
    application_code VARCHAR(50) NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    description TEXT,
    active_version_number INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_identity UNIQUE(application_code, template_code)
);

-- Immutable Template Versions
CREATE TABLE IF NOT EXISTS template_versions (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    channel VARCHAR(20) NOT NULL, -- EMAIL, SMS, PUSH
    locale VARCHAR(10) NOT NULL,  -- en, am, etc.
    subject TEXT,                 -- Nullable for SMS/PUSH
    body TEXT NOT NULL,
    engine VARCHAR(20) NOT NULL,  -- HANDLEBARS
    status VARCHAR(20) NOT NULL,  -- DRAFT, PUBLISHED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_version_identity UNIQUE(template_id, version_number, channel, locale)
);

-- Index for high-throughput lookup
CREATE INDEX IF NOT EXISTS idx_template_version_lookup 
ON template_versions(template_id, version_number, channel, locale);
```

---

## 12. Spring Data R2DBC Models & Enums

### Enums
```java
public enum ChannelType {
    EMAIL, SMS, PUSH
}

public enum TemplateEngine {
    HANDLEBARS
}

public enum VersionStatus {
    DRAFT, PUBLISHED
}
```

### Entities (Reactive Records)
```java
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table("templates")
public record Template(
    @Id UUID id,
    String applicationCode,
    String templateCode,
    String description,
    Integer activeVersionNumber,
    @CreatedDate Instant createdAt,
    @LastModifiedDate Instant updatedAt
) {}

@Table("template_versions")
public record TemplateVersion(
    @Id UUID id,
    UUID templateId,
    Integer versionNumber,
    ChannelType channel,
    String locale,
    String subject,
    String body,
    TemplateEngine engine,
    VersionStatus status,
    @CreatedDate Instant createdAt
) {}
```

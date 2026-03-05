# Template Service: Overview

The **Template Service** is the centralized source of truth for all notification content. It manages the lifecycle of message templates, ensuring that marketing and product teams can update content without requiring service redeployments.

---

## 🎯 Purpose
In modern notification systems, content is separated from delivery logic. This service exists to:
*   **Decouple Content from Code**: Changes to message text shouldn't require code changes.
*   **Enable Multi-tenancy**: Manage different brands and styles for different tenants.
*   **Support Global Growth**: Handle multi-language translations (localization) seamlessly.

---

## 🛠️ Core Responsibilities

### 1. Template Management
*   **Storage**: Maintains a repository of templates for all channels (Email, SMS, Push).
*   **Versioning**: Supports multiple versions of the same template, allowing for safe content rollouts and rollbacks.
*   **Metadata**: Stores essential metadata such as `notificationType`, `channelType`, and `language`.

### 2. Localization (i18n)
*   **Language-Specific Variants**: Automatically serves the correct template version based on the user's preferred language.

### 3. Multi-tenancy
*   **Tenant Isolation**: Ensures that Tenant A's templates are never visible to or used by Tenant B.

### 4. High-Performance Retrieval
*   **Quick Lookups**: Provides lightweight APIs for the **Notification Processor** to fetch template content during the delivery pipeline.

---

## 🔍 Data Structure (Conceptual)
Each template entry includes:
*   `templateId`: Unique identifier.
*   `notificationType`: The business event (e.g., `WELCOME_EMAIL`).
*   `channel`: `EMAIL`, `SMS`, or `PUSH`.
*   `version`: Semantic or sequential versioning.
*   `contentBody`: The raw template string (supporting syntax like Handlebars/Thymeleaf).
*   `language`: ISO language code (e.g., `en-US`, `fr-FR`).

---

## 🚫 What This Service MUST NOT Do
To maintain a clean separation of concerns, this service **never**:
*   **Renders Templates**: Rendering is a heavy CPU task performed by the **Notification Processor**.
*   **Stores User Data**: It knows "what" to send, but not "who" it is going to.
*   **Executes Delivery**: It has no knowledge of Twilio, SendGrid, or Kafka.

---

## 💎 Design Goals
*   **High Read Throughput**: Optimized for frequent lookups by the Processor.
*   **Cacheability**: Template content is ideal for heavy caching (L2 Redis).
*   **Atomic Updates**: Ensures that template updates are consistent across all instances.
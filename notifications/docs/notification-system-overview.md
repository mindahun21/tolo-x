# Tolo-X Notification System: High-Level Overview

Welcome to the Tolo-X Notification System! This document provides a broad overview of how we've designed and implemented our notification infrastructure. It is intended for new engineers joining the team to quickly grasp the "big picture."

---

## 🧬 Core Architecture
Our system is built on an **event-driven, microservices-based architecture**. We prioritize decoupling, scalability, and reliability, ensuring that massive spikes in notification traffic don't degrade the performance of our core business services.

---

## 🏗️ Services & Responsibilities

### 1. Notification API Service (The Gatekeeper)
*   **Inbound Entry Point**: The sole API for all external and internal systems (e.g., Order Service, Auth Service) to request notifications.
*   **Validation & Security**: Rejects malformed requests and enforces client authentication/authorization at the edge.
*   **Event Normalization**: Converts various input formats into a single **Standardized Event**.
*   **Async Publishing**: Publishes normalized events to Kafka (`notification.events.raw`) immediately, ensuring low latency for producers.

### 2. Notification Preference Service (The Policy Engine)
*   **User Preferences**: Stores user-specific settings such as opt-ins, channel choices (SMS vs. Email), and global opt-outs.
*   **Compliance & Rules**: Manages "quiet hours" and mandatory notification overrides (e.g., password resets always go through).
*   **Eligibility Check**: Provides high-performance lookups for the Processor to decide if a notification should actually be delivered.

### 3. Template Service (The Content Manager)
*   **Centralized Storage**: Manages message content for all notification types (Email, SMS, Push).
*   **Versioning & Localization**: Handles multiple versions of templates and multi-language support.
*   **Multi-tenancy**: Ensures templates are isolated and branded correctly for different tenants.

### 4. Notification Processor Service (The Delivery Engine)
*   **Kafka Consumption**: Scales horizontally to consume raw events from Kafka.
*   **Idempotency & Deduplication**: Prevents duplicate sends by tracking `eventId` in a delivery history table.
*   **Final Decisioning**: Queries the Preference Service for a final "Go/No-Go" before rendering.
*   **Content Rendering**: Fetches templates and hydrates them with event data.
*   **Provider Routing**: Routes the final message to external providers (SendGrid, Twilio, FCM, etc.) with built-in retry logic.

### 5. Campaign Service (Bulk Orchestration)
*   **Bulk/Marketing Sends**: Handles scheduled and recurring high-volume notification blasts.
*   **Segmentation**: Integrates with User and Analytics services to identify target audiences.
*   **Traffic Management**: Generates individual events to the API Service at a controlled rate to prevent system overload ("Token Bucket" approach).

---

## 🔄 The Journey of a Notification
1.  **Request**: A source service (e.g., *Order Service*) calls the **Notification API**.
2.  **Ingress**: The API validates the request, normalizes it, and drops it into **Kafka**.
3.  **Process**: The **Notification Processor** picks up the event from the topic.
4.  **Verify**: The Processor checks the **Preference Service** (Is the user opted in? Is it midnight?).
5.  **Render**: The Processor fetches the correct template from the **Template Service** and renders the final message.
6.  **Deliver**: The Processor sends the message via a provider adapter (e.g., **Twilio** for SMS).

---

## 🛠️ Supporting Infrastructure
*   **Apache Kafka**: The backbone for asynchronous message passing and system scalability.
*   **Redis**: High-speed caching for user preferences and template versions to minimize database load.
*   **Auditing & Observability**: Centralized tracking for the entire lifecycle (from *PENDING* to *RENDERED* to *SENT/FAILED*).

---

## 💎 Core Design Principles
1.  **Statelessness**: Every service is horizontally scalable and stateless.
2.  **Asynchronicity**: No provider calls happen in the request pipeline; everything moves through Kafka topics.
3.  **Tenant Isolation**: Data and templates are strictly separated by `tenantId`.
4.  **Decoupling**: Marketing/Campaign logic is strictly separated from the core delivery logic.

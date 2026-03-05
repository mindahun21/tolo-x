# Processor Service: Overview

The **Processor Service** (or Delivery Engine) is the "workhorse" of the notification system. It is a highly scalable Kafka consumer that handles the complex logic of turning a raw event into a successfully delivered notification.

---

## 🎯 Purpose
While other services handle "what" and "who," the Processor handles **"how"**. It exists to:
*   **Execute Delivery**: Orchestrates the final steps of rendering and sending.
*   **Ensure Reliability**: Manages retries, provider failures, and at-least-once delivery.
*   **Absorb Spikes**: Uses asynchronous consumption to process high volumes without crashing.

---

## 🛠️ Core Responsibilities

### 1. Kafka Consumption
*   **Scalable Processing**: Operates as a Kafka consumer group to handle massive parallel workloads.

### 2. Idempotency & Deduplication
*   **Safety First**: Checks against a delivery history to ensure the same `eventId` is never processed twice (preventing duplicate emails/SMS).

### 3. Preference & Eligibility Verification
*   **Final Decision**: Performs a last-minute check with the **Preference Service** (e.g., Is the user currently in "Quiet Hours"? Did they just opt out?).

### 4. Content Rendering (The Heavy Lifting)
*   **Hydration**: Fetches the correct template from the **Template Service** and populates it with variables from the raw event payload.

### 5. Multi-Channel Routing
*   **Provider Adapters**: Routes the finalized message to the specific provider adapter (e.g., SendGrid for Email, Twilio for SMS, Firebase for Push).

### 6. Status Tracking
*   **Observability**: Records every stage of the delivery attempt (RENDERED, SENT, FAILED, RETRYING) for the audit trail.

---

## 🔄 Internal Processing Flow
1.  **Consume**: Pick up event from Kafka.
2.  **Deduplicate**: Verify `eventId` isn't a duplicate.
3.  **Validate**: Confirm user eligibility via Preference Service.
4.  **Fetch**: Retrieve raw template from Template Service.
5.  **Render**: Generate final HTML/Text content.
6.  **Send**: Dispatch via the external provider.
7.  **Settle**: Store the final delivery status.

---

## 🚫 What This Service MUST NOT Do
To maintain a clean separation of concerns, this service **never**:
*   **Store Master Preferences**: It only *queries* the Preference Service.
*   **Manage Marketing Campaigns**: It treats every event as an isolated work item.
*   **Perform User Segmentation**: It doesn't know "why" the user is getting a notification.

---

## 💎 Design Goals
*   **At-Least-Once Delivery**: Guaranteed delivery even in the face of partial system failures.
*   **Resiliency**: Built-in exponential backoff for provider errors and circuit breakers for down providers.
*   **Statelessness**: No local state; all progress is tracked in Kafka or the delivery database.
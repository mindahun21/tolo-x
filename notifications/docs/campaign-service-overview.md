# Campaign Service: Overview

The **Campaign Service** is the orchestration engine for bulk, scheduled, and recurring notifications. It manages "Push" marketing and operational blasts, ensuring large-scale communications are handled systematically without overwhelming the delivery infrastructure.

---

## 🎯 Purpose
Not all notifications are real-time. This service exists to:
*   **Orchestrate Bulk Sends**: Manage the lifecycle of marketing campaigns or bulk system updates.
*   **Separate Workloads**: Protect real-time transactional alerts (like password resets) from being delayed by a 1-million-user marketing blast.
*   **Schedule Communications**: Allow teams to schedule notifications for the future or recurring intervals.

---

## 🛠️ Core Responsibilities

### 1. Campaign Orchestration
*   **Lifecycle Management**: Handle campaign creation, scheduling, execution monitoring, and cancellation.
*   **Status Tracking**: Provide real-time visibility into how many recipients have been processed.

### 2. Audience Segmentation
*   **Targeting**: Integrate with User and Analytics services to identify the list of `userIds` who should receive the campaign.
*   **Batching**: Load and process recipients in optimized batches to prevent memory exhaustion.

### 3. Intelligent Event Generation
*   **Individualization**: Instead of a "massive payload," it generates one event per user and sends it to the **Notification API Service**.
*   **Traffic Shaping**: Uses rate-limiting (e.g., "Token Bucket") to drip events into the system, preventing Kafka topic flooding.

### 4. Scheduling & Recurrence
*   **Job Management**: Executes campaigns at specific dates/times or on recurring cycles (e.g., "Every Monday at 9 AM").

---

## 🚫 What This Service MUST NOT Do
To maintain a clean separation of concerns, this service **never**:
*   **Sends Emails/SMS Directly**: It only generates the *request* to send; the Processor handles the actual delivery.
*   **Renders Templates**: This is delegated to the Processor.
*   **Stores Delivery Status**: It tracks campaign *progress*, but the Processor tracks whether an individual message was successfully delivered.

---

## 🔍 Integration Boundary
*   **User/Analytics Services**: For audience selection.
*   **Notification API Service**: The target for generated events.
*   **Redis/DB**: For campaign state and scheduling metadata.

---

## 💎 Design Goals
*   **Scalability**: Supports massive audiences through persistent, chunked processing.
*   **Loose Coupling**: Functions independently of the delivery engine, interacting only via the standard API entry point.
*   **Safe Execution**: Built to fail gracefully; a failure for one user never halts the entire campaign.
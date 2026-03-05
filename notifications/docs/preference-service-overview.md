# Preference Service: Overview

The **Preference Service** is the "Policy Engine" of the Tolo-X notification system. It acts as the final decision-maker, determining whether a specific notification should be delivered to a user based on their personal settings, global rules, and legal compliance.

---

## 🎯 Purpose
In a world of information overload, users must have control over what they receive. This service exists to:
*   **Empower Users**: Allow users to opt-in/opt-out of specific channels or types of content.
*   **Ensure Compliance**: Handle global "Do Not Disturb" (Quiet Hours) and legal opt-outs (GDPR/CCPA).
*   **Enforce Safety**: Ensure that critical Security and Transactional alerts (like password resets) always get through, even if a user has blocked general notifications.

---

## 🛠️ Core Responsibilities

### 1. User Preference Storage
*   **Granular Control**: Stores whether a user wants Email vs. SMS vs. Push for different categories of notifications.
*   **Channel Settings**: Manages global channel-level blocks and legal opt-out status.

### 2. Decision Logic (The Rule Engine)
*   **Precedence Resolution**: Applies a strictly ordered set of rules (Mandatory > Legal Opt-Out > User Override > Default).
*   **Quiet Hours Check**: Evaluates if the current time in the user's timezone allows for non-urgent notifications.

### 3. High-Performance Evaluation
*   **Evaluator API**: Provides a high-speed `/evaluate` endpoint for the **Notification Processor** to call mid-pipeline.
*   **Effective Context**: Calculates the best possible channel to reach a user based on their hierarchy of preferences.

### 4. Admin Management
*   **Notification Registry**: Manages the global list of `notificationTypes`, their defaults, and whether they are marked as "Mandatory."

---

## 🧠 Decision Precedence (High to Low)
1.  **Mandatory**: Is this a critical security alert? (Always SEND).
2.  **Legal Opt-Out**: Has the user exercised a legal right to stop all communication? (BLOCK).
3.  **Global Channel Block**: Has the user disabled this entire channel (e.g., "No SMS")? (BLOCK).
4.  **Quiet Hours**: Is it currently too early/late for the user? (DELAY/BLOCK).
5.  **User Override**: Has the user specifically turned this notification type ON or OFF? (RESPECT USER).
6.  **System Default**: If no user setting exists, what is the default for this type? (FOLLOW DEFAULT).

---

## 💎 Design Goals
*   **Ultra-Low Latency**: Decisions should be made in `< 10ms` using heavy L1/L2 caching.
*   **Consistency**: A user's preference change must be reflected across the system near-instantaneously.
*   **Scalability**: Optimized for extremely high read traffic during campaign bursts.
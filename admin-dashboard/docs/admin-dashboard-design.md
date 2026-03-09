# Tolo-X Admin Dashboard: Detailed Design

This document outlines the architecture and implementation strategy for the **Tolo-X Notification Admin Dashboard**.

---

## 1. Executive Summary
The Admin Dashboard is the "Control Center" for the notification system. While the core engine is built in **Spring Boot (Java)** for performance, the Dashboard is built in **Laravel (PHP)** for extreme developer productivity and a premium UI experience.

### Technical Stack
- **Backend Framework**: Laravel 11 (PHP 8.3)
- **Frontend Bridge**: Inertia.js (The "Protocol" for modern monoliths)
- **Frontend Framework**: Vue.js 3 (Composition API)
- **Styling**: Tailwind CSS
- **Local Storage**: PostgreSQL (Dedicated Admin Database)

---

## 2. Authentication & User Integration
The Dashboard follows the **"Passport & Building"** pattern. It does not manage its own identity; it trusts the **Spring Auth Service**.

### 2.1 The OIDC/OAuth2 Handshake
1.  **Auth Redirect**: When an admin accesses `/login`, Laravel redirects to the **Spring Auth Service** URL.
2.  **OIDC Login**: The admin logs in via the central Auth Service.
3.  **JWT Retrieval**: The Auth Service redirects back to Laravel with an Authorization Code. Laravel exchanges this for a JWT.
4.  **Verification**: Laravel verifies the JWT signature (using public keys provided by the Auth Service).

### 2.2 The "Shadow User" Pattern
Laravel maintains a local `users` table, but it acts as a **Read-Only Proxy** for identity.
- **Master User ID**: Matches the UUID from the Spring User Service.
- **Local Roles (RBAC)**: Roles like `TEMPLATE_EDITOR` or `SUPER_ADMIN` are stored locally in Laravel.
- **Sync Logic**: On the first successful login, Laravel creates a local record for the user and fetches their profile details (Name, Avatar) from the **Spring User Service**.

---

## 3. Detailed Component Design

### 3.1 Content Manager (Vue 3 + Inertia)
This module interacts with the **Template Service**.
- **Live Preview**: As the admin types in the Vue component, it debounces and calls the Spring `/preview` endpoint.
- **Syntax Highlighting**: Uses `Monaco Editor` or `CodeMirror` within the Vue component to provide a professional developer feel.
- **Version History**: Fetches the list of versions from Spring and shows a "Diff" between the current and previous content.

### 3.2 Global Asset Library
Interacts with the **Asset Service**.
- **Gallery View**: Shows all global assets (Logos, Banners) in a grid.
- **Update propagation**: When an asset URL is updated in Laravel (via PUT request to Spring), the local cache in the Spring services is automatically evicted as defined in the contract.

### 3.3 Preference Auditor
Interacts with the **Preference Service**.
- **Search**: Allows admins to search for a specific User ID and see their current opt-in/opt-out status.
- **Quiet Hour Visualizer**: A graphical timeline showing the user's current restricted windows.

---

## 4. Service-to-Service Communication
Laravel acts as a **First-Class Client** to your Spring services.

### 4.1 Internal API Discovery
Laravel uses the standard Spring API endpoints.
- **HttpClient**: Uses Laravel's `Http` facade (Guzzle wrapper) to call internal service URIs.
- **Circuit Breakers**: Implements basic retries for transient failures in the Spring cluster.
- **JSON Contracts**: Maps Spring's snake_case or camelCase responses into clean Vue props via Inertia.

---

## 5. Local Database Responsibility
Even though services are external, the Dashboard has a dedicated PostgreSQL database for:

1.  **RBAC (Authorization)**: Storing the local permissions for maintainers.
2.  **Dashboard Audit Logs**: Recording who changed a template (The "Who" and "Why").
3.  **Announcement System**: System-wide notifications specifically for Admins.
4.  **Report Caching**: Storing high-level metrics fetched from Analytics to avoid hammering the production analytics DB.

---

## 6. Logic Flow: Creating a Template
1.  **Vue (Frontend)**: User clicks "Create Template".
2.  **Inertia (Request)**: Sends data to Laravel `TemplateController@store`.
3.  **Laravel (Controller)**: Performs basic validation (e.g., checking for prohibited words).
4.  **Spring Call (Backend)**: Laravel makes a `POST /template` call to the **Spring Template Service**.
5.  **Audit Log**: Laravel saves a local record: *"User X created Template Y at Time Z"*.
6.  **Success**: Laravel returns an Inertia redirect back to the Template List.

### 7. Why Vue + Inertia?
- **No API Complexity**: We don't need a separate API for the Dashboard. Data is passed directly from Laravel Controllers to Vue components as props.
- **Security**: The JWT token remains on the server (in the session). The browser (Frontend) never handles the raw credentials, significantly reducing XSS risks.
- **Speed**: We get the "App-like" feel of Vue without the hundreds of files required for a standalone React/Angular project.

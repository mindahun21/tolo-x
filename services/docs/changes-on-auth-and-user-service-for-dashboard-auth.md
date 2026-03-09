# Changes for Dashboard Integration: Auth & User Services

This document details the necessary modifications to the **Auth Service** and **User Service** to support authentication for the **Laravel Admin Dashboard** using the "Passport & Building" pattern.

---

## 1. Analysis of Current State

### 1.1 Auth Service (`services/auth`)
- **Role**: Currently acts as an **OAuth2 Client** (integrates with Google).
- **Security**: Uses standard Spring Security with manual JWT generation via `jjwt`.
- **Limitation**: It **cannot** act as an identity provider for other applications. It doesn't have an Authorization Server capability, meaning Laravel has no endpoint to perform a "Login with Tolo-X" handshake.

### 1.2 User Service (`services/user`)
- **Role**: Manages profiles and roles (`User`, `Role`, `ERole`).
- **Database**: Has tables for `users`, `roles`, and their mapping.
- **Limitation**: Needs to ensure it provides sufficient data (name, email, roles) to Laravel during the sync process.

---

## 2. Required Changes: Auth Service

To support the Laravel Dashboard, the Auth Service must be upgraded to a **Spring Authorization Server**.

### 2.1 Dependency Upgrades
Add the following to `services/auth/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
```

### 2.2 Configuration To-Do List
1.  **Client Registration**: Register `admin-dashboard` as a trusted client.
    - `client-id`: `tolo-admin-client`
    - `client-secret`: `{noop}dashboard-secret-123`
    - `redirect-uri`: `http://admin-dashboard.tolo-x.com/login/callback`
    - `scopes`: `openid`, `profile`, `email`, `roles`
2.  **Authorization Server Settings**:
    - Define an `Issuer` URL (e.g., `http://auth-service.tolo-x.com`).
    - Configure **JWK Source**: Generate an RSA Key Pair for signing JWTs so Laravel can verify them.
3.  **Bridge Logic**:
    - Update `OAuth2AuthenticationSuccessHandler`. Currently, it registers/logs in the user and returns a manual JWT. 
    - **Change**: After Google login, the user must be authenticated in the Spring Security Context so the Authorization Server can proceed with issuing the code for Laravel.

---

## 3. Required Changes: User Service

The User Service must provide the necessary data for the "Shadow User" sync.

### 3.1 Profile Synchronization
- **Internal API**: Ensure an internal endpoint exists (e.g., `GET /internal/profiles/{userId}`) that returns:
    - `id`, `email`, `firstName`, `lastName`, and **list of Roles**.
- **Role Mapping**: Verify that the `ADMIN` role is properly assigned to maintainers in the database.

---

## 4. Logical Flow for Admin Login

1.  **Admin** visits Laravel Dashboard.
2.  **Laravel** redirects to `auth-service/oauth2/authorize?client_id=tolo-admin-client...`
3.  **Auth Service** sees no session -> Shows "Login with Google" button.
4.  **Admin** completes Google Login.
5.  **Auth Service** maps Google user to Tolo-X account, puts it in Security Context.
6.  **Auth Service** redirects to **Laravel Callback** with Authorization Code.
7.  **Laravel** swaps code for a JWT (signed by Tolo-X).
8.  **Laravel** calls **User Service** to sync profile and checks for the `ADMIN` role.
9.  **Laravel** creates a local session. **DONE.**

---

## 5. Summary Tracking [TODO]

| Task | Status | Service |
| :--- | :--- | :--- |
| Add Authorization Server Dependency | 🟥 TODO | Auth |
| Configure `RegisteredClientRepository` | 🟥 TODO | Auth |
| Generate RSA Signing Keys (JWKs) | 🟥 TODO | Auth |
| Update SuccessHandler for Session Context | 🟥 TODO | Auth |
| Verify Internal Profile Sync API | 🟥 TODO | User |
| Ensure Role assignments in DB | 🟥 TODO | User |

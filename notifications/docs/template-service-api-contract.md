# Template Service: API Contract (v2 - Implemented)

This document defines the REST API contract for the Template Service. It distinguishes between high-performance rendering (internal), content discovery, and administrative management.

**Base Path**: `/notification-template`

---

## 1. Internal Rendering API
Used by the **Notification Processor Service** to generate the final message content for delivery.

### Render Template (Active Version)
`POST /template/render`
*   **Purpose**: Resolves the "Active" version for the given channel/locale and renders it.
*   **Request Body**:
```json
{
  "applicationCode": "TOLO_AUTH",
  "templateCode": "SECURITY_ALERT",
  "channel": "EMAIL",
  "locale": "en",
  "data": {
    "userName": "Mindahun",
    "location": "Addis Ababa"
  }
}
```
*   **Notes**: 
    *   Automatically injects global assets into the rendering context (accessible via `[[${assets.KEY}]]`).
    *   Implements localization fallback (e.g., `en_US` -> `en` -> `application default`).

---

## 2. Variable Discovery API
Used by upstream services to understand what business data (placeholders) a template requires.

### Discover Variables (Active Version)
`GET /template/{templateId}/variables`

### Discover Variables (Target Version)
`GET /template/{templateId}/versions/{versionNumber}/variables`

*   **Response Schema**:
```json
{
  "channelVariables": {
    "EMAIL": ["userName", "location"],
    "SMS": ["userName"]
  },
  "aggregatedVariables": ["userName", "location"]
}
```
*   **Note**: This API **filters out** `assets.*` variables, as those are handled automatically by the service.

---

## 3. Global Asset Management
Manages shared assets (logos, URLs, branding) that change independently of templates.

### List All Assets
`GET /assets`

### Create/Update Asset
`POST /assets` | `PUT /assets/{id}`
```json
{
  "assetKey": "COMPANY_LOGO",
  "assetUrl": "https://cdn.tolo-x.com/logo.png",
  "description": "Global header logo"
}
```
*   **Cache Invalidation**: Updating an asset automatically evicts the global asset registry from the hierarchical cache.

---

## 4. Administrative Management (CRUD)

### Template Registry
*   `POST /template`: Create new logical template.
*   `GET /template`: List all templates.
*   `GET /template/{id}`: Get metadata.
*   `PUT /template/{id}`: Update metadata.

### Versioning & Publishing
*   `POST /template/{templateId}/versions/{versionNumber}`: Create a specific version/channel variant.
*   `GET /template/{templateId}/versions`: List all version history.
*   `PATCH /template/{templateId}/publish/{versionNumber}`: Set a version as "ACTIVE" globally.
*   `POST /template/{templateId}/versions/{versionNumber}/preview`: Render a specific version (even if DRAFT) for UI testing.

---

## 5. Implementation Status & [TODOs]

| Feature | Status | Notes |
| :--- | :--- | :--- |
| **Hierarchical Caching** | ✅ DONE | L1 (Caffeine) + L2 (Redis) implemented. |
| **Asset Injection** | ✅ DONE | Automatic injection of `assets.*` into all renders. |
| **Localization Fallback** | ✅ DONE | Smart fallback (language_COUNTRY -> language -> generic). |
| **Handlebars Engine** | ⚠️ TODO | Engine defined in enum, but implementation pending. |
| **Rollback API** | ⚠️ TODO | Use `publish` for now, but need specific `/rollback` for auditing. |
| **Tenant Isolation** | ⚠️ TODO | Currently uses `applicationCode`, but multi-tenant DB schema needed. |
| **API Path Prefixing** | ⚠️ TODO | Standardize `/admin` vs `/internal` at the Gateway level. |

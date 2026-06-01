# Keycloak Webhook Extension

A Keycloak extension that sends webhook notifications to external APIs when specific user events occur in Keycloak.

**Author:** Chintan Buch
**Website:** https://mrbu.ch
**Keycloak Minimum Version:** 26.6

## Overview

This extension integrates with Keycloak's event system to capture user-related events such as registration, login, logout, and password resets. When these events occur, the extension sends HTTP POST requests with user data to a configured webhook endpoint.

Key features:
- Listens for REGISTER, REGISTER_ERROR, LOGIN, LOGOUT, RESET_PASSWORD, VERIFY_EMAIL, UPDATE_EMAIL, and DELETE_ACCOUNT events
- Sends structured JSON payloads with comprehensive user information
- Includes user roles, organization membership, custom profile attributes, and realm context in payload
- Supports authentication with API keys
- Implements retry logic with exponential backoff for resilient delivery
- Circuit breaker pattern to prevent hammering failed endpoints
- Configurable through Keycloak client attributes

## Requirements

- **Java:** 17 or higher
- **Keycloak:** 26.6 or higher (required for Organizations feature support)
  - Older Keycloak versions (<26.0): May work but organizations will be empty in webhook payload
- **Maven:** for building the project

## Installation

1. Build the extension:
   ```bash
   make build
   ```

2. Copy the generated JAR file to Keycloak's providers directory:
   ```bash
   cp target/keycloak-client-webhook.jar /path/to/keycloak/providers/
   ```

3. Restart Keycloak to load the extension.

## Configuration

### 1. Enable the Event Listener

> [!IMPORTANT]
> **This step is mandatory and must be repeated for every new Keycloak instance or realm.**
> Without it, no webhooks will fire — even if the JAR is loaded and client attributes are configured.

1. Log in to the Keycloak Admin Console
2. Navigate to your realm
3. Go to **Realm Settings → Events**
4. In the **"Event Listeners"** field, add `brew-event-webhook"`
5. Click **"Save"**

### 2. Configure Webhook Endpoint

The webhook URL and API key are configured at the client level via the Keycloak Admin API — there is no Attributes tab in the UI for custom attributes.

**Create a service account client for API access:**

1. Admin console → Clients → **Create client**
2. Enable **Service account roles** (under Capability config)
3. Go to that client → **Service accounts roles** tab → **Assign role** → filter by `realm-management` → add **manage-clients**

**Get a token:**

```bash
curl -X POST \
  "https://your-keycloak/realms/{realm}/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id={service-client-id}" \
  -d "client_secret={service-client-secret}"
```

**Fetch the current client representation** (the PUT replaces the full object — always GET first):

```bash
curl -X GET \
  "https://your-keycloak/admin/realms/{realm}/clients/{client-uuid}" \
  -H "Authorization: Bearer {access_token}"
```

**Merge your attributes and PUT back:**

```bash
curl -X PUT \
  "https://your-keycloak/admin/realms/{realm}/clients/{client-uuid}" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    ...existing client JSON...,
    "attributes": {
      ...existing attributes...,
      "api.url": "https://your-api.example.com/webhooks/keycloak",
      "api.key": "your-secret-token",
      "disable.autologin": "true",
      "trusted.proxy.count": "1"
    }
  }'
```

You can find the client UUID in the URL when viewing the client in Admin console.

**Supported attributes:**

| Attribute | Required | Description |
|---|---|---|
| `api.url` | Yes | Webhook endpoint URL |
| `api.key` | Yes | Bearer token sent in Authorization header |
| `disable.autologin` | No | Set `true` to prevent auto-login after registration |
| `trusted.proxy.count` | No | Reverse proxies in front of Keycloak (default: 1). Increase if client IPs are incorrect |

## Webhook Payload

The extension sends a JSON payload with the following structure:

```json
{
  "type": "LOGIN",
  "user_id": "6f8df73e-9c42-4f8b-b3a1-c1d9bcb45f0b",
  "user_name": "john.doe",
  "email": "john.doe@example.com",
  "first_name": "John",
  "last_name": "Doe",
  "email_verified": true,
  "created_timestamp": 1621459200000,
  "user_ip": "192.168.1.1",
  "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
  "delete_by_admin": false,
  "user_roles": [
    "admin",
    "user"
  ],
  "organizations": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "ACME Corporation",
      "alias": "acme"
    }
  ],
  "attributes": {
    "phone_number": ["+1-555-0100"],
    "company": ["ACME Corporation"],
    "job_title": ["Engineer"]
  },
  "realm": {
    "id": "a3f8c2d1-1234-5678-abcd-000000000001",
    "name": "myrealm",
    "display_name": "My Application Realm"
  }
}
```

### Payload Fields

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Event type (REGISTER, LOGIN, LOGOUT, etc.) |
| `user_id` | string | Unique Keycloak user ID |
| `user_name` | string | Username/login name |
| `email` | string | User email address |
| `first_name` | string | User first name |
| `last_name` | string | User last name |
| `email_verified` | boolean | Email verification status |
| `created_timestamp` | number | User creation timestamp (milliseconds) |
| `user_ip` | string | IP address of user making the request |
| `user_agent` | string | Browser/client user agent string |
| `delete_by_admin` | boolean | Whether deletion was admin-triggered |
| `user_roles` | array | List of realm role names assigned to user |
| `organizations` | array | List of organizations user belongs to (id, name, alias) |
| `attributes` | object | Custom user attributes defined in Realm Settings → User Profile (filtered; internal Keycloak attrs excluded) |
| `realm` | object | Realm context: `id`, `name`, `display_name` |

## Supported Events

The extension currently listens for the following Keycloak events:
- `REGISTER`: When a new user registers
- `REGISTER_ERROR`: When a user registration fails
- `LOGIN`: When a user logs in
- `LOGOUT`: When a user logs out
- `RESET_PASSWORD`: When a user resets their password
- `VERIFY_EMAIL`: When a user verifies their email address
- `UPDATE_EMAIL`: When a user's email address is updated
- `DELETE_ACCOUNT`: When a user account is deleted

## Troubleshooting

### Webhook Not Triggering

1. Verify the event listener is properly enabled in the realm settings
2. Check that the client has the correct `api.url` and `api.key` attributes
3. Examine Keycloak server logs for any error messages
4. Ensure your webhook endpoint is accessible from the Keycloak server
5. Check minimum Keycloak version is 26.6 or higher

### HTTP Connection Issues

The extension implements retry logic with exponential backoff.
If there are temporary connection issues, it will retry up to 3 times with exponential backoff (200ms, 400ms).
4xx errors fail fast without retrying — check your `api.url` and `api.key` if you see those.
Since webhook calls are executed asynchronously, these retries happen in the background and don't affect Keycloak's performance or user experience.

### Organizations Field Empty in Webhook

If `organizations` array is always empty even for users in orgs:
1. Verify Keycloak version is 26.6+ (organizations feature introduced in 26.x)
2. Check that Organizations feature is enabled in your Keycloak realm
3. Confirm the user is actually assigned to at least one organization in Keycloak admin console
4. Check server logs for "Failed to fetch organizations" warnings

### Incompatible Keycloak Version

This extension requires **Keycloak 26.6 or higher**. Older versions lack the OrganizationProvider API.
If using older Keycloak, consider forking and removing the org-related code.

## CI/CD Pipeline

The GitLab CI pipeline (`.gitlab-ci.yml`) has two stages:

| Stage | What it does |
|---|---|
| `build` | Compiles JAR, uploads to GitLab Package Registry, extracts release notes from `CHANGELOG.md` |
| `release` | Creates GitLab Release with tag and asset link pointing to Package Registry |

### POM.xml Dependencies

The pipeline reads values directly from `pom.xml` — no hardcoded values:

| CI Variable | POM Source | Example                              |
|---|---|--------------------------------------|
| `RELEASE_VERSION` | `project.version` | `1.0.0`                              |
| `ARTIFACT_ID` | `project.artifactId` | `keycloak-client-webhook`            |
| `JAR_FILE` | `target/${ARTIFACT_ID}.jar` (resolved from `project.artifactId`) | `target/keycloak-client-webhook.jar` |

Package Registry URL pattern:
```
$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages/generic/$ARTIFACT_ID/v$RELEASE_VERSION/$ARTIFACT_ID.jar
```

Release tag is `v$RELEASE_VERSION` (e.g. `v1.0.0`). To release a new version, bump `<version>` in `pom.xml` and push to `main`.

## v2.0.0 Major Release

✨ **Payload Enrichment**
- User roles (realm roles) included in webhook payload
- Organization membership details (id, name, alias) included
- Both fields always present (empty list if user has no roles/orgs)

🚀 **Performance & Reliability**
- Removed Spring Web dependency (100% httpclient5 direct)
- HTTP client reuse — connection pooling enabled
- Circuit breaker pattern prevents cascading failures
- Smarter retry logic: 4xx errors fail fast, 5xx errors retry with exponential backoff
- Fixed user model null validation in session details

🔧 **Operational**
- Keycloak 26.6+ with Organizations feature support
- Graceful handling when org feature disabled
- Informative logging on extension initialization

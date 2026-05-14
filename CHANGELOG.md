# Changelog

All notable changes to this project are documented here.

---

## [Unreleased]

### Breaking Changes

- **Minimum Keycloak version raised to 26.6** — `OrganizationProvider` API required; older versions incompatible
- **`user_id` no longer includes `"f:"` prefix** — strip-prefix behaviour is now applied; consumers relying on the old format must update
- **Spring Web removed** — replaced by pure `httpclient5`; any classpath dependency on Spring beans from this extension will break

### Added

- `REGISTER_ERROR`, `VERIFY_EMAIL`, `UPDATE_EMAIL`, `DELETE_ACCOUNT` event support
- Realm roles included in webhook payload
- Organization membership (`id`, `name`, `alias`) included in webhook payload
- Circuit breaker — prevents hammering failed endpoints; endpoint marked unavailable after repeated failures
- `KeycloakSessionDetails` — extracts and validates client attributes (`api.url`, `api.key`, `disable.autologin`, `trusted.proxy.count`) with explicit exceptions
- `RequestUtils` — IP extraction respecting X-Forwarded-For proxy chain; `trusted.proxy.count` client attribute controls depth
- `AppConstants` — centralises all client attribute keys and header constants
- `NoWebhookDetailException` — thrown when required client attributes are missing
- `NoUserFoundException` — thrown when user lookup fails
- `disable.autologin` client attribute — opt-in override to prevent auto-login after registration

### Changed

- HTTP client migrated from Spring `RestClient` to Apache `httpclient5` with connection pooling
- Retry strategy: 4xx errors fail fast (no retry); 5xx errors retry with exponential backoff (200 ms / 400 ms / 800 ms)
- License changed from AGPL-3.0 to Apache-2.0

---

## [1.0.0] — 2025-05-13

Initial release.

- Webhook notifications on `REGISTER`, `LOGIN`, `LOGOUT`, `RESET_PASSWORD` events
- Structured JSON payload with user id, username, email, names, email-verified status, timestamps, IP, user agent
- Per-client configuration via Keycloak client attributes (`api.url`, `api.key`)
- API key authentication header on every webhook request
- Async execution via shared `ScheduledExecutorService` (5 threads) — never blocks Keycloak event processing
- Retry logic with exponential backoff (1 s / 2 s / 3 s), max 3 attempts
- Event listener registered as `brew-event-webhook` — activate under Realm settings → Events → Event listeners

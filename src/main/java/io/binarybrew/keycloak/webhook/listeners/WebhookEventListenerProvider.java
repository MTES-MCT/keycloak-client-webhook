/**
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.listeners;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.binarybrew.keycloak.webhook.constant.AppConstants;
import io.binarybrew.keycloak.webhook.data.dto.KeycloakUserEventDTO;
import io.binarybrew.keycloak.webhook.data.dto.OrgDetailDTO;
import io.binarybrew.keycloak.webhook.data.dto.RealmDetailDTO;
import io.binarybrew.keycloak.webhook.util.RequestUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The WebhookEventListenerProvider is an implementation of the Keycloak
 * EventListenerProvider interface. It listens for certain Keycloak events
 * and executes webhook requests to external APIs based on the event type.
 * <p>
 * This class is designed to handle user-related events, such as registration,
 * password reset, login, logout, email verification, and email updates, and
 * can notify an external service by sending structured event data using an
 * HTTP POST request.
 * <p>
 * The provider supports asynchronous webhook execution with retry capabilities
 * to ensure reliable delivery of event notifications even in case of temporary
 * network or service issues. It also provides configuration options through
 * client attributes to customize webhook behavior.
 * <p>
 * Key features:
 * <ul>
 *   <li>Configurable webhook URL and API key through client attributes</li>
 *   <li>Asynchronous webhook execution to avoid blocking Keycloak operations</li>
 *   <li>Retry mechanism with exponential backoff for failed webhook calls</li>
 *   <li>Support for disabling automatic login after registration</li>
 *   <li>Detailed event payload with user information and request metadata</li>
 * </ul>
 */
public class WebhookEventListenerProvider implements EventListenerProvider {

    private final KeycloakSession session;
    private final WebhookEventListenerProviderFactory factory;

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookEventListenerProvider.class);

    private static final Set<EventType> SUPPORTED_EVENTS = EnumSet.of(
            EventType.REGISTER, EventType.RESET_PASSWORD, EventType.LOGIN,
            EventType.LOGOUT, EventType.VERIFY_EMAIL, EventType.UPDATE_EMAIL,
            EventType.DELETE_ACCOUNT
    );

    public WebhookEventListenerProvider(
            KeycloakSession session,
            WebhookEventListenerProviderFactory factory
    ) {
        this.session = session;
        this.factory = factory;
    }

    /**
     * Handles Keycloak user events by processing specific event types and triggering webhook notifications.
     * <p>
     * This method is called by the Keycloak event system whenever a user-related event occurs.
     * It specifically listens for the following event types:
     * <ul>
     *     <li>REGISTER: When a new user registers</li>
     *     <li>RESET_PASSWORD: When a user resets their password</li>
     *     <li>LOGIN: When a user logs in</li>
     *     <li>LOGOUT: When a user logs out</li>
     *     <li>VERIFY_EMAIL: When a user verifies their email address</li>
     *     <li>UPDATE_EMAIL: When a user's email address is updated</li>
     *     <li>REGISTER_ERROR: When a registration attempt fails</li>
     * </ul>
     * <p>
     * When this method is called, it performs the following steps:
     * <ol>
     * <li>Retrieves the client model from the session context</li>
     * <li>Extracts webhook configuration (API URL, API key, auto-login settings) from client attributes</li>
     * <li>Validates the configuration parameters</li>
     * <li>Retrieves the realm associated with the event</li>
     * <li>For REGISTER_ERROR events, extracts available information and calls sendWebhookRequestForError()</li>
     * <li>For other events, retrieves the user model from the user ID</li>
     * <li>If auto-login is disabled and the event is REGISTER, calls disableAutoLogin() to invalidate sessions</li>
     * <li>For supported event types with valid user information, calls sendWebhookRequest()</li>
     * </ol>
     * <p>
     * Events that don't match the specified types, events with missing user information,
     * or events with invalid configuration are handled appropriately or silently ignored.
     *
     * @param event The Keycloak event containing information about what occurred,
     *              including event type, user ID, and realm ID
     */
    @Override
    public void onEvent(Event event) {
        LOGGER.info("Webhook event received: type={}, userId={}, realmId={}", event.getType().name(), event.getUserId(), event.getRealmId());
        KeycloakSessionDetails keycloakSessionDetails;
        try {
            keycloakSessionDetails = new KeycloakSessionDetails(session, event.getRealmId(), event.getUserId());
        } catch (IllegalStateException e) {
            LOGGER.error("WebhookEventListenerProvider > onEvent(event) > ClientModel is null.");
            return;
        } catch (NoWebhookDetailException e) {
            LOGGER.debug("{}", e.getMessage());
            return;
        } catch (NoUserFoundException e) {
            LOGGER.debug("{}", e.getMessage());
            // Handle registration error
            if (event.getType() == EventType.REGISTER_ERROR) {
                if (event.getDetails() != null) {
                    LOGGER.error("{} for {}", EventType.REGISTER_ERROR.name(), event.getDetails().get("email"));
                    RealmModel errorRealm = session.realms().getRealm(event.getRealmId());
                    sendWebhookRequestForError(event, e.apiUrl, e.apiKey, false, AppConstants.DEFAULT_TRUSTED_PROXY_COUNT, errorRealm);
                }
                return;
            }
            return;
        }
        // disable auto login after user registration
        if (keycloakSessionDetails.disableLogin && event.getType() == EventType.REGISTER &&
                event.getSessionId() != null && keycloakSessionDetails.userModel != null) {
            disableAutoLogin(keycloakSessionDetails.realmModel, keycloakSessionDetails.userModel);
        }

        // Handle different events
        if (keycloakSessionDetails.userModel != null && SUPPORTED_EVENTS.contains(event.getType())) {
            sendWebhookRequest(event, keycloakSessionDetails.userModel, keycloakSessionDetails.apiUrl, keycloakSessionDetails.apiKey, false, keycloakSessionDetails.trustedProxyCount, keycloakSessionDetails.realmModel);
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent.getResourceType() != ResourceType.USER) return;

        RealmModel realm = session.realms().getRealm(adminEvent.getRealmId());
        if (realm == null) {
            LOGGER.warn("Admin event: realm not found for id {}", adminEvent.getRealmId());
            return;
        }

        OperationType opType = adminEvent.getOperationType();
        if (opType == OperationType.DELETE) {
            handleAdminDelete(adminEvent, realm, includeRepresentation);
        } else if (opType == OperationType.UPDATE) {
            handleAdminUpdate(adminEvent, realm);
        }
    }

    @Override
    public void close() {
        // No resources to clean up - executor service is managed by the factory
    }

    /**
     * Sends a webhook request to an external API with user event data asynchronously.
     * <p>
     * Constructs a payload with user event information and schedules an asynchronous HTTP POST request
     * to the configured endpoint. Implements retry mechanism with exponential backoff for transient failures.
     *
     * @param event The Keycloak event that triggered this webhook
     * @param user The user model associated with the event
     * @param apiUrl The webhook URL to send the request to
     * @param apiKey The API key used for authentication with the webhook endpoint
     * @param adminEvent Whether this is an admin-triggered event
     * @param trustedProxyCount Number of trusted proxies for client IP extraction
     */
    private void sendWebhookRequest(Event event, UserModel user, String apiUrl, String apiKey, boolean adminEvent, int trustedProxyCount, RealmModel realm) {
        sendWebhookAsync(event.getType().name(), apiUrl, apiKey, () -> createPayload(event.getType().name(), user, adminEvent, trustedProxyCount, realm), "event");
    }

    /**
     * Sends a webhook request for error events with partial user information.
     * <p>
     * Handles error scenarios (e.g., REGISTER_ERROR) where a complete user model is not available.
     * Extracts whatever information is available from the event details map.
     *
     * @param event The Keycloak error event
     * @param apiUrl The webhook URL to send the request to
     * @param apiKey The API key used for authentication with the webhook endpoint
     * @param adminEvent Whether this is an admin-triggered event
     * @param trustedProxyCount Number of trusted proxies for client IP extraction
     */
    private void sendWebhookRequestForError(Event event, String apiUrl, String apiKey, boolean adminEvent, int trustedProxyCount, RealmModel realm) {
        sendWebhookAsync(event.getType().name(), apiUrl, apiKey, () -> createPayloadForError(event.getType().name(), event.getDetails(), adminEvent, trustedProxyCount, realm), "error event");
    }

    private void sendWebhookAsync(String eventType, String apiUrl, String apiKey, PayloadSupplier payloadSupplier, String logContext) {
        try {
            if (factory.getCircuitBreaker().isOpen()) {
                LOGGER.warn("Circuit breaker open, skipping webhook for {} {}", logContext, eventType);
                return;
            }

            final KeycloakUserEventDTO payload = payloadSupplier.get();
            LOGGER.info("Submitting {} webhook to executor pool: apiUrl={}", eventType, apiUrl);
            factory.getExecutorService().submit(() -> executeWebhookWithRetries(apiUrl, apiKey, payload));
        } catch (IllegalStateException e) {
            LOGGER.error("Webhook configuration error: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error scheduling webhook: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface PayloadSupplier {
        KeycloakUserEventDTO get() throws Exception;
    }

    private void executeWebhookWithRetries(String apiUrl, String apiKey, KeycloakUserEventDTO payload) {
        String json;
        try {
            json = factory.getObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            LOGGER.error("Payload serialization failed: {}", e.getMessage());
            return;
        }

        int maxRetries = 3;
        int retryCount = 0;
        LOGGER.info("Starting webhook execution: eventType={}, apiUrl={}, maxRetries={}", payload.getType(), apiUrl, maxRetries);

        while (retryCount < maxRetries) {
            try {
                HttpPost request = new HttpPost(apiUrl);
                request.setHeader("Authorization", "Bearer " + apiKey);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

                LOGGER.info("Sending HTTP POST to {} (attempt {}/{})", apiUrl, retryCount + 1, maxRetries);
                int status = factory.getHttpClient().execute(request, response -> response.getCode());

                if (status >= 200 && status < 300) {
                    LOGGER.info("Webhook success: eventType={}, apiUrl={}, status={}", payload.getType(), apiUrl, status);
                    factory.getCircuitBreaker().recordSuccess();
                    return;
                } else if (status >= 400 && status < 500) {
                    LOGGER.error("Webhook rejected (4xx={}), not retrying. Check api.url and api.key.", status);
                    factory.getCircuitBreaker().recordFailure();
                    return;
                } else {
                    LOGGER.warn("Webhook server error (5xx={}), attempt {}/{}", status, retryCount + 1, maxRetries);
                }
            } catch (Exception e) {
                LOGGER.warn("Webhook call failed, attempt {}/{}: {}", retryCount + 1, maxRetries, e.getMessage());
            }

            retryCount++;
            if (retryCount < maxRetries) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100L * (1L << retryCount));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        factory.getCircuitBreaker().recordFailure();
        LOGGER.info("Webhook failed after {} attempts: eventType={}, apiUrl={}", maxRetries, payload.getType(), apiUrl);
    }

    private List<String> fetchRealmRoles(UserModel user) {
        return user.getRealmRoleMappingsStream()
                .map(RoleModel::getName)
                .collect(Collectors.toList());
    }

    private Map<String, List<String>> fetchUserAttributes(UserModel user) {
        try {
            UserProfileProvider upProvider = session.getProvider(UserProfileProvider.class);
            if (upProvider == null) {
                return Collections.emptyMap();
            }
            Set<String> profileKeys = upProvider.create(UserProfileContext.ACCOUNT, user)
                    .getAttributes()
                    .nameSet();
            return user.getAttributes().entrySet().stream()
                    .filter(e -> profileKeys.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch user profile attributes for {}: {}", user.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<OrgDetailDTO> fetchOrganizations(UserModel user) {
        try {
            OrganizationProvider orgProvider = session.getProvider(OrganizationProvider.class);
            if (orgProvider == null || !orgProvider.isEnabled()) {
                return Collections.emptyList();
            }
            return orgProvider.getByMember(user)
                    .map(org -> new OrgDetailDTO(org.getId(), org.getName(), org.getAlias()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch organizations for user {}: {}", user.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Creates a data transfer object containing user event information for the webhook payload.
     * <p>
     * Constructs a KeycloakUserEventDTO with user information from Keycloak UserModel including
     * event type, user ID/username/email, profile info, account status, timestamps, and request metadata.
     *
     * @param eventType The type of Keycloak event that occurred
     * @param user The Keycloak user model containing user information
     * @param adminEvent Whether this is an admin-triggered event
     * @param trustedProxyCount Number of trusted proxies for client IP extraction
     * @return KeycloakUserEventDTO populated with user and event data
     */
    private KeycloakUserEventDTO createPayload(String eventType, UserModel user, boolean adminEvent, int trustedProxyCount, RealmModel realm) {
        String[] headers = RequestUtils.extractRequestHeaders(
                session.getContext(),
                session.getContext().getConnection().getRemoteAddr(),
                trustedProxyCount
        );
        RealmDetailDTO realmDetail = realm != null
                ? new RealmDetailDTO(realm.getId(), realm.getName(), realm.getDisplayName())
                : null;
        return new KeycloakUserEventDTO(
                eventType, RequestUtils.stripKeycloakIdPrefix(user.getId()), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.isEmailVerified(),
                user.getCreatedTimestamp(),
                headers[0],
                headers[1],
                adminEvent,
                fetchRealmRoles(user),
                fetchOrganizations(user),
                fetchUserAttributes(user),
                realmDetail
        );
    }

    /**
     * Creates a data transfer object for error events using available event details.
     * <p>
     * Handles error scenarios (e.g., REGISTER_ERROR) where a complete user model is not available.
     * Extracts available information from the event details map (email, name fields, etc.).
     * Many fields may be null or incomplete since error typically occurs before user is fully created.
     *
     * @param eventType The type of error event that occurred (e.g., "REGISTER_ERROR")
     * @param eventDetailMap Map containing details about the error event with partial user information
     * @param adminEvent Whether this is an admin-triggered event
     * @param trustedProxyCount Number of trusted proxies for client IP extraction
     * @return KeycloakUserEventDTO populated with available error event data
     */
    private KeycloakUserEventDTO createPayloadForError(String eventType, Map<String, String> eventDetailMap, boolean adminEvent, int trustedProxyCount, RealmModel realm) {
        String[] headers = RequestUtils.extractRequestHeaders(
                session.getContext(),
                session.getContext().getConnection().getRemoteAddr(),
                trustedProxyCount
        );
        RealmDetailDTO realmDetail = realm != null
                ? new RealmDetailDTO(realm.getId(), realm.getName(), realm.getDisplayName())
                : null;
        return new KeycloakUserEventDTO(
                eventType, null, null, eventDetailMap.get(AppConstants.EVENT_DETAIL_EMAIL),
                eventDetailMap.get(AppConstants.EVENT_DETAIL_FIRST_NAME), eventDetailMap.get(AppConstants.EVENT_DETAIL_LAST_NAME), null, null,
                headers[0], headers[1],
                adminEvent,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                realmDetail
        );
    }

    /**
     * Disables automatic login after user registration by invalidating all active sessions.
     * <p>
     * When a user registers in Keycloak, they are typically logged in automatically.
     * This method provides a way to disable that behavior by finding and removing
     * all active sessions for the user immediately after registration.
     * <p>
     * This functionality is useful in scenarios where additional verification steps
     * are required before allowing a user to access the system, such as email verification,
     * admin approval, or completion of additional registration steps.
     *
     * @param realmModel The realm model where the user exists
     * @param user The user model whose sessions should be invalidated
     */
    private void disableAutoLogin(RealmModel realmModel, UserModel user) {
        // Find all user sessions and invalidate them to prevent automatic login
        session.sessions().getUserSessionsStream(realmModel, user)
                .forEach(userSession -> {
                    LOGGER.debug("Removing session {} for user {}", userSession.getId(), user.getUsername());
                    session.sessions().removeUserSession(realmModel, userSession);
                });
    }

    private String extractUserIdFromPath(String resourcePath) {
        if (resourcePath == null) return null;
        String[] parts = resourcePath.split("/");
        return (parts.length >= 2) ? parts[parts.length - 1] : null;
    }

    /**
     * Creates a data transfer object from JSON representation of a user.
     * <p>
     * Parses user data from JSON representation (used in admin events) and constructs a payload.
     * Falls back to userId if representation is unavailable or parsing fails.
     *
     * @param eventType The type of event that occurred
     * @param representationJson JSON representation of the user
     * @param userId Fallback user ID if representation unavailable
     * @param adminEvent Whether this is an admin-triggered event
     * @param trustedProxyCount Number of trusted proxies for client IP extraction
     * @return KeycloakUserEventDTO populated from representation or fallback data
     */
    private KeycloakUserEventDTO createPayloadFromRepresentation(String eventType, String representationJson, String userId, boolean adminEvent, int trustedProxyCount, RealmModel realm) {
        String[] headers = RequestUtils.extractRequestHeaders(
                session.getContext(),
                session.getContext().getConnection().getRemoteAddr(),
                trustedProxyCount
        );
        RealmDetailDTO realmDetail = realm != null
                ? new RealmDetailDTO(realm.getId(), realm.getName(), realm.getDisplayName())
                : null;
        try {
            JsonNode node = representationJson != null ? factory.getObjectMapper().readTree(representationJson) : null;
            String id = (node != null) ? node.path("id").asText(userId) : userId;
            Map<String, List<String>> attributes = null;
            if (node != null && node.has("attributes")) {
                attributes = factory.getObjectMapper().convertValue(
                        node.path("attributes"),
                        new TypeReference<Map<String, List<String>>>() {}
                );
            }

            return new KeycloakUserEventDTO(
                    eventType,
                    RequestUtils.stripKeycloakIdPrefix(id),
                    (node != null) ? node.path("username").asText(null) : null,
                    (node != null) ? node.path("email").asText(null) : null,
                    (node != null) ? node.path("firstName").asText(null) : null,
                    (node != null) ? node.path("lastName").asText(null) : null,
                    null, null, headers[0], headers[1],
                    adminEvent,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    attributes,
                    realmDetail
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to parse representation JSON: {}", e.getMessage());
            return new KeycloakUserEventDTO(
                    eventType, RequestUtils.stripKeycloakIdPrefix(userId), null, null, null, null,
                    null, null, headers[0], headers[1], adminEvent,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null,
                    realmDetail
            );
        }
    }

    private void sendAdminWebhookToAllClients(RealmModel realm, String eventType, UserModel user, String representationJson, String userId) {
        if (factory.getCircuitBreaker().isOpen()) {
            LOGGER.warn("Circuit breaker open, skipping admin webhook for {}", eventType);
            return;
        }

        realm.getClientsStream().forEach(client -> {
            String apiUrl = client.getAttribute(AppConstants.API_URL);
            String apiKey = client.getAttribute(AppConstants.API_KEY);
            if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank()) return;

            int trustedProxyCount = RequestUtils.parseTrustedProxyCount(client.getAttribute(AppConstants.TRUSTED_PROXY_COUNT));
            KeycloakUserEventDTO payload = buildAdminPayload(eventType, user, representationJson, userId, client.getClientId(), trustedProxyCount, realm);
            if (payload != null) {
                factory.getExecutorService().submit(() -> executeWebhookWithRetries(apiUrl, apiKey, payload));
            }
        });
    }

    private KeycloakUserEventDTO buildAdminPayload(String eventType, UserModel user, String representationJson, String userId, String clientId, int trustedProxyCount, RealmModel realm) {
        if (user != null) {
            return createPayload(eventType, user, true, trustedProxyCount, realm);
        } else if (representationJson != null || userId != null) {
            return createPayloadFromRepresentation(eventType, representationJson, userId, true, trustedProxyCount, realm);
        }
        LOGGER.warn("Admin event {}: no user and no representation for client {}", eventType, clientId);
        return null;
    }

    private void handleAdminDelete(AdminEvent adminEvent, RealmModel realm, boolean includeRepresentation) {
        String userId = extractUserIdFromPath(adminEvent.getResourcePath());
        if (userId == null) {
            LOGGER.warn("Admin DELETE: unable to extract userId from resourcePath {}", adminEvent.getResourcePath());
            return;
        }

        String representation = includeRepresentation ? adminEvent.getRepresentation() : null;
        sendAdminWebhookToAllClients(realm, EventType.DELETE_ACCOUNT.name(), null, representation, userId);
    }

    private void handleAdminUpdate(AdminEvent adminEvent, RealmModel realm) {
        String representation = adminEvent.getRepresentation();
        if (representation == null || !representation.contains("\"enabled\"")) {
            LOGGER.debug("Admin UPDATE on USER is not a status change — skipping");
            return;
        }

        boolean enabled;
        try {
            JsonNode node = factory.getObjectMapper().readTree(representation);
            if (!node.has("enabled")) return;
            enabled = node.get("enabled").asBoolean();
        } catch (Exception e) {
            LOGGER.warn("Admin UPDATE: failed to parse representation: {}", e.getMessage());
            return;
        }

        String eventType = enabled
                ? AppConstants.ADMIN_EVENT_USER_ENABLED
                : AppConstants.ADMIN_EVENT_USER_DISABLED;

        String userId = extractUserIdFromPath(adminEvent.getResourcePath());
        UserModel user = (userId != null) ? session.users().getUserById(realm, userId) : null;

        sendAdminWebhookToAllClients(realm, eventType, user, representation, userId);
    }
}

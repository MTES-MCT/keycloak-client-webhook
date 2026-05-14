/**
 * Encapsulates Keycloak session context and webhook configuration details.
 * <p>
 * This class validates and extracts webhook configuration (API URL, API key, and auto-login
 * settings) from client attributes and loads the associated Keycloak models (realm and user).
 * It performs validation during construction and throws appropriate exceptions if required
 * configuration is missing or if the user cannot be found.
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.listeners;

import io.binarybrew.keycloak.webhook.constant.AppConstants;
import io.binarybrew.keycloak.webhook.util.RequestUtils;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Validates and stores webhook configuration and Keycloak session details.
 */
public class KeycloakSessionDetails {
    /** The client model extracted from the current Keycloak session context. */
    ClientModel clientModel;

    /** The realm model for the current event. */
    RealmModel realmModel;

    /** The user model associated with the current event. */
    UserModel userModel;

    /** The ID of the user associated with the event. */
    String userId;

    /** The webhook API URL from client attributes (api.url). */
    String apiUrl;

    /** The API key for webhook authentication from client attributes (api.key). */
    String apiKey;

    /** Flag indicating whether to disable auto-login after registration. */
    boolean disableLogin;

    /** Number of trusted proxies for extracting real client IP from X-Forwarded-For header. */
    int trustedProxyCount;

    /**
     * Constructs a KeycloakSessionDetails instance by validating webhook configuration
     * and loading Keycloak models.
     * <p>
     * Validates that the client has api.url and api.key attributes configured and that
     * the user exists in the realm. Throws appropriate exceptions if validation fails.
     *
     * @param session the current Keycloak session
     * @param realmId the ID of the realm where the event occurred
     * @param userId the ID of the user associated with the event
     * @throws NoWebhookDetailException if api.url or api.key is not configured on the client
     * @throws NoUserFoundException if the user cannot be found in the realm
     * @throws IllegalStateException if the client model is null
     */
    public KeycloakSessionDetails(KeycloakSession session, String realmId, String userId) throws NoWebhookDetailException, NoUserFoundException, IllegalStateException {
        this.clientModel = session.getContext().getClient();
        this.realmModel = session.realms().getRealm(realmId);
        this.userId = userId;
        this.apiUrl = this.clientModel.getAttribute(AppConstants.API_URL);
        this.apiKey = this.clientModel.getAttribute(AppConstants.API_KEY);
        this.disableLogin = (
                clientModel.getAttribute(AppConstants.DISABLE_AUTOLOGIN) != null &&
                        clientModel.getAttribute(AppConstants.DISABLE_AUTOLOGIN).equals("true")
        );
        this.trustedProxyCount = RequestUtils.parseTrustedProxyCount(
                clientModel.getAttribute(AppConstants.TRUSTED_PROXY_COUNT)
        );

        if (this.clientModel == null) {
            throw new IllegalStateException("ClientModel is null.");
        }

        if (this.apiUrl == null || this.apiUrl.trim().isEmpty()) {
            throw new NoWebhookDetailException("API URL is not configured.");
        }

        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            throw new NoWebhookDetailException("API Key is not configured.");
        }

        if (this.userId == null || this.userId.trim().isEmpty()) {
            throw new NoUserFoundException("User ID is null", this.apiUrl, this.apiKey);
        }

        session.getContext().setRealm(realmModel);
        this.userModel = session.users().getUserById(realmModel, userId);
        if (this.userModel == null) {
            throw new NoUserFoundException("User not found in realm: " + userId, this.apiUrl, this.apiKey);
        }
    }
}

/**
 * Exception thrown when a user cannot be found in the Keycloak realm during event processing.
 * <p>
 * This exception is raised during webhook configuration validation when the user ID
 * is null, empty, or the user does not exist in the specified realm. It carries the
 * webhook API URL and key for potential error recovery or fallback handling.
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.listeners;

import java.io.Serial;

/**
 * Thrown when a user lookup fails during webhook event processing.
 */
public class NoUserFoundException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The webhook API URL from client attributes. */
    public String apiUrl;

    /** The API key for webhook authentication. */
    public String apiKey;

    /**
     * Constructs a NoUserFoundException with error details and webhook configuration.
     *
     * @param message the error message describing why the user was not found
     * @param apiUrl the webhook API URL that would have been used
     * @param apiKey the API key that would have been used for authentication
     */
    public NoUserFoundException(String message, String apiUrl, String apiKey) {
        super(message);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }
}

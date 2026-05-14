/**
 * Exception thrown when required webhook configuration is missing from client attributes.
 * <p>
 * This exception is raised when the webhook extension cannot find required configuration
 * such as the API URL (api.url) or API key (api.key) in the client attributes.
 * Events with missing configuration are logged and skipped without blocking Keycloak operations.
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.listeners;

import java.io.Serial;

/**
 * Thrown when webhook configuration details are missing or invalid.
 */
public class NoWebhookDetailException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a NoWebhookDetailException with a detailed error message.
     *
     * @param message the error message describing what configuration is missing
     */
    public NoWebhookDetailException(String message) {
        super(message);
    }

    /**
     * Constructs a NoWebhookDetailException with a message and underlying cause.
     *
     * @param message the error message describing what configuration is missing
     * @param cause the underlying exception that caused this error
     */
    public NoWebhookDetailException(String message, Throwable cause) {
        super(message, cause);
    }

}

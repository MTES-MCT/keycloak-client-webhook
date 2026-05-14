/**
 * Central repository of constants used throughout the Keycloak webhook extension.
 * <p>
 * This class defines all client attribute keys, event detail keys, and HTTP header names
 * used for webhook configuration, event processing, and request metadata extraction.
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.constant;

public class AppConstants {

    /** Client attribute key for webhook API URL configuration. */
    public static final String API_URL = "api.url";

    /** Client attribute key for webhook API key (authentication token). */
    public static final String API_KEY = "api.key";

    /** Client attribute key to disable automatic login after user registration. */
    public static final String DISABLE_AUTOLOGIN = "disable.autologin";

    /** Client attribute key for number of trusted proxies in X-Forwarded-For chain. */
    public static final String TRUSTED_PROXY_COUNT = "trusted.proxy.count";

    /** Default number of trusted proxies (assumes deployment behind at least one reverse proxy). */
    public static final int DEFAULT_TRUSTED_PROXY_COUNT = 1;

    /** Event detail key for user email in error events. */
    public static final String EVENT_DETAIL_EMAIL = "email";

    /** Event detail key for user first name in error events. */
    public static final String EVENT_DETAIL_FIRST_NAME = "firstName";

    /** Event detail key for user last name in error events. */
    public static final String EVENT_DETAIL_LAST_NAME = "lastName";

    /** HTTP header name for forwarded IP address (from reverse proxies). */
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /** HTTP header name for real client IP (alternative to X-Forwarded-For). */
    public static final String HEADER_X_REAL_IP = "X-Real-IP";

    /** HTTP header name for client user agent information. */
    public static final String HEADER_USER_AGENT = "User-Agent";

    /** Admin event type: user disabled by an administrator. */
    public static final String ADMIN_EVENT_USER_DISABLED = "USER_DISABLED_BY_ADMIN";

    /** Admin event type: user enabled by an administrator. */
    public static final String ADMIN_EVENT_USER_ENABLED = "USER_ENABLED_BY_ADMIN";

}

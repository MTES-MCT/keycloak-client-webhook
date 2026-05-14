/**
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.binarybrew.keycloak.webhook.constant.OperationalInfo;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating WebhookEventListenerProvider instances.
 * 
 * This factory initializes and manages a ScheduledExecutorService that is used
 * for asynchronous execution of webhook requests. The executor service is shared
 * across all provider instances created by this factory.
 */
public class WebhookEventListenerProviderFactory implements EventListenerProviderFactory, ServerInfoAwareProviderFactory {

    public static final int THREAD_POOL = 5;
    public static final String PROVIDER_ID = "brew-event-webhook";

    private ExecutorService executorService;
    private CloseableHttpClient httpClient;
    private ObjectMapper objectMapper;
    private WebhookCircuitBreaker circuitBreaker;
    private RequestConfig requestConfig;

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookEventListenerProviderFactory.class);

    /**
     * Creates a new WebhookEventListenerProvider instance for the given session.
     *
     * @param session the Keycloak session
     * @return a new WebhookEventListenerProvider configured with shared resources
     */
    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new WebhookEventListenerProvider(session, this);
    }

    /**
     * Initializes the factory by creating shared resources.
     * <p>
     * Creates and configures:
     * <ul>
     *   <li>FixedThreadPool executor service with 5 threads for async webhook execution</li>
     *   <li>HTTP client with 5 second connect timeout and 10 second response timeout</li>
     *   <li>ObjectMapper for JSON serialization</li>
     *   <li>Circuit breaker for failure protection</li>
     * </ul>
     *
     * @param config the Keycloak configuration scope for this provider
     */
    @Override
    public void init(Config.Scope config) {
        // Initialize JSON serializer
        objectMapper = new ObjectMapper();

        // Initialize circuit breaker
        circuitBreaker = new WebhookCircuitBreaker();

        // Store request config for lazy HTTP client creation
        requestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.STRICT)
                .setConnectTimeout(5000, TimeUnit.MILLISECONDS)
                .setResponseTimeout(10000, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Called after all providers have been initialized.
     * <p>
     * Can be used to perform cross-provider initialization or dependency setup.
     * Currently no-op for this implementation.
     *
     * @param factory the Keycloak session factory
     */
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-initialization logic (e.g., if you need to connect to another service)
    }

    /**
     * Gracefully shuts down executor service only. HTTP client remains open for lazy recreation.
     * This allows the extension to recover if resources need to be recreated.
     */
    @Override
    public void close() {
        // Shutdown executor service gracefully
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        LOGGER.error("ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.error("ExecutorService exception occurred {}", e.getMessage());
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close HTTP client if it exists (may fail if classloader unloading)
        if (httpClient != null) {
            try {
                httpClient.close();
                httpClient = null;
            } catch (Exception e) {
                LOGGER.debug("HttpClient close failed (may be classloader shutdown): {}", e.getMessage());
            }
        }
    }

    /**
     * Lazily creates or returns the executor service.
     * Auto-recreates if previously shutdown.
     */
    public synchronized ExecutorService getExecutorService() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(THREAD_POOL);
            LOGGER.debug("ExecutorService created/recreated");
        }
        return executorService;
    }

    /**
     * Lazily creates or returns the HTTP client.
     * Auto-recreates if previously closed.
     */
    public synchronized CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();
            LOGGER.debug("HttpClient created/recreated");
        }
        return httpClient;
    }

    /**
     * Returns the object mapper instance (always available, never closed).
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Returns the circuit breaker instance (always available, never closed).
     */
    public WebhookCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Returns the unique identifier for this event listener provider.
     *
     * @return the provider ID ("brew-event-webhook")
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Returns operational metadata about this extension.
     * <p>
     * Contains version, maintainer, contact, and organization information
     * as displayed in Keycloak admin console.
     *
     * @return map of operational information
     */
    @Override
    public Map<String, String> getOperationalInfo() {
        return OperationalInfo.operationalInfo();
    }
}

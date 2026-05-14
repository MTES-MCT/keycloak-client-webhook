/**
 * Circuit breaker pattern implementation to prevent cascading webhook failures.
 * <p>
 * This class monitors webhook call failures and temporarily stops sending webhooks
 * when failures exceed a threshold, allowing time for the external API to recover.
 * It implements a half-open state that periodically allows probe requests through
 * to detect when the service has recovered.
 * <p>
 * States:
 * <ul>
 *   <li>CLOSED: Normal operation, webhooks are sent</li>
 *   <li>OPEN: Circuit is open, consecutive failures exceed threshold, webhooks are skipped</li>
 *   <li>HALF-OPEN: Reset timeout expired, one request is allowed through as a probe</li>
 * </ul>
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.listeners;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements circuit breaker logic for webhook execution.
 */
public class WebhookCircuitBreaker {
    /** Number of consecutive failures before opening the circuit. */
    private static final int THRESHOLD = 5;

    /** Duration in milliseconds before attempting to close the circuit. */
    private static final long RESET_MS = 60_000L;

    /** Tracks consecutive failures in a thread-safe manner. */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** Timestamp when the circuit was opened. */
    private volatile long openedAt = 0L;

    /**
     * Checks if the circuit breaker is in the open state.
     * <p>
     * Returns true if the circuit is open (too many consecutive failures).
     * If the reset timeout has expired, the circuit transitions to half-open
     * and allows the next request through.
     *
     * @return true if the circuit is open and blocking requests, false otherwise
     */
    public boolean isOpen() {
        if (consecutiveFailures.get() >= THRESHOLD) {
            if (System.currentTimeMillis() - openedAt < RESET_MS) {
                return true;
            }
            // half-open: let one through
            consecutiveFailures.set(THRESHOLD - 1);
        }
        return false;
    }

    /**
     * Records a successful webhook call and resets the failure counter.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
    }

    /**
     * Records a failed webhook call and increments the failure counter.
     * <p>
     * If the failure count reaches the threshold, records the timestamp
     * at which the circuit was opened.
     */
    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= THRESHOLD) {
            openedAt = System.currentTimeMillis();
        }
    }
}

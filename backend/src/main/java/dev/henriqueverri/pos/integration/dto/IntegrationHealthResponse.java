package dev.henriqueverri.pos.integration.dto;

import java.time.Instant;

/**
 * State of the integration. {@code enabled} is whether the worker delivers at all (enabled and a
 * key configured); {@code keyPrefix} is only the public prefix of the key, never the secret.
 */
public record IntegrationHealthResponse(
    boolean enabled,
    Instant pausedUntil,
    String targetUrl,
    String keyPrefix,
    long pending,
    long failed,
    Instant lastSentAt) {}

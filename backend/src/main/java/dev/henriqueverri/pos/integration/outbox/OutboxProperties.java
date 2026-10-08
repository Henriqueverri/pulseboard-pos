package dev.henriqueverri.pos.integration.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Worker settings. The lease must outlast one delivery (connect + read timeouts) so that a live
 * worker never loses its claim; if the worker dies, the event is claimable again when it expires.
 *
 * <p>Transient failures wait {@code min(maxDelay, baseDelay × 2^(n−1))} after the n-th attempt,
 * times a random jitter; after {@code maxAttempts} the event is {@code FAILED/EXHAUSTED}. A 401
 * pauses the whole integration for {@code configurationPause}.
 */
@ConfigurationProperties("pos.outbox")
public record OutboxProperties(
    @DefaultValue("2s") Duration pollInterval,
    @DefaultValue("20") int batchSize,
    @DefaultValue("2m") Duration lease,
    @DefaultValue("10") int maxAttempts,
    @DefaultValue("10s") Duration baseDelay,
    @DefaultValue("15m") Duration maxDelay,
    @DefaultValue("15m") Duration configurationPause) {}

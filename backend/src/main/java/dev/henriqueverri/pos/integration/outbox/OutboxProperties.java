package dev.henriqueverri.pos.integration.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Worker settings. The lease must outlast one delivery (connect + read timeouts) so that a live
 * worker never loses its claim; if the worker dies, the event is claimable again when it expires.
 */
@ConfigurationProperties("pos.outbox")
public record OutboxProperties(
    @DefaultValue("2s") Duration pollInterval,
    @DefaultValue("20") int batchSize,
    @DefaultValue("2m") Duration lease) {}

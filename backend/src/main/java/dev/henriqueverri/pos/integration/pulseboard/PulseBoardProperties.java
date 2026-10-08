package dev.henriqueverri.pos.integration.pulseboard;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where and how to reach the PulseBoard ingestion API. The integration is active only when it is
 * enabled and an API key is configured; otherwise events keep accumulating as {@code PENDING} and
 * are delivered once it is turned on. A missing key never prevents startup.
 */
@ConfigurationProperties("pulseboard")
public record PulseBoardProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("http://localhost:8000/api/v1") URI apiUrl,
    @DefaultValue("") String apiKey,
    @DefaultValue("10s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout) {

  public boolean isActive() {
    return enabled && !apiKey.isBlank();
  }

  /** The API key must never reach logs, so it is left out of the generated representation. */
  @Override
  public String toString() {
    return "PulseBoardProperties[enabled=%s, apiUrl=%s, apiKey=%s, connectTimeout=%s, readTimeout=%s]"
        .formatted(
            enabled,
            apiUrl,
            apiKey.isBlank() ? "<empty>" : "<redacted>",
            connectTimeout,
            readTimeout);
  }
}

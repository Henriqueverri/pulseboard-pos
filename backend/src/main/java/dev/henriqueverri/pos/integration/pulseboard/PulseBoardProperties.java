package dev.henriqueverri.pos.integration.pulseboard;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final Pattern KEY_FORMAT = Pattern.compile("pb_([A-Za-z0-9]{12})_[A-Za-z0-9]{40}");

  public boolean isActive() {
    return enabled && !apiKey.isBlank();
  }

  /**
   * The public part of a well-formed key ({@code pb_<prefix>_<secret>}), as PulseBoard shows it.
   * Null for an empty or malformed key, so no fragment of an unexpected value is ever exposed.
   */
  public String keyPrefix() {
    Matcher matcher = KEY_FORMAT.matcher(apiKey.trim());
    return matcher.matches() ? matcher.group(1) : null;
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

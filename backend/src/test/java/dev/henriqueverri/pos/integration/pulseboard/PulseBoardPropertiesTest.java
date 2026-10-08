package dev.henriqueverri.pos.integration.pulseboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PulseBoardPropertiesTest {

  private static final String KEY = "pb_abcdefghijkl_0123456789012345678901234567890123456789";

  @Test
  void activeOnlyWhenEnabledAndKeyed() {
    assertThat(properties(true, KEY).isActive()).isTrue();
    assertThat(properties(true, "").isActive()).isFalse();
    assertThat(properties(true, "  ").isActive()).isFalse();
    assertThat(properties(false, KEY).isActive()).isFalse();
  }

  @Test
  void neverPrintsTheApiKey() {
    assertThat(properties(true, KEY).toString())
        .doesNotContain(KEY)
        .doesNotContain("0123456789012345")
        .contains("apiKey=<redacted>");
    assertThat(properties(true, "").toString()).contains("apiKey=<empty>");
  }

  private static PulseBoardProperties properties(boolean enabled, String key) {
    return new PulseBoardProperties(
        enabled,
        URI.create("http://localhost:8000/api/v1"),
        key,
        Duration.ofSeconds(10),
        Duration.ofSeconds(30));
  }
}

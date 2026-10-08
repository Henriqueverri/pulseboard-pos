package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RetryPolicyTest {

  private static final Instant NOW = Instant.parse("2026-10-06T21:00:00Z");

  @Test
  void nominalDelaysDoubleUpToFifteenMinutes() {
    RetryPolicy policy = policy(10, () -> 0.0);

    assertThat(IntStream.rangeClosed(1, 9).mapToObj(policy::nominalDelay).map(Duration::toSeconds))
        .containsExactly(10L, 20L, 40L, 80L, 160L, 320L, 640L, 900L, 900L);
    assertThat(policy.nominalDelay(500)).isEqualTo(Duration.ofMinutes(15));
  }

  /** Jitter multiplies the delay by a factor in [0.5, 1.0]. */
  @ParameterizedTest
  @CsvSource({"1, 5000, 10000", "3, 20000, 40000", "9, 450000, 900000"})
  void jitterStaysWithinHalfAndTheFullDelay(int attempts, long lowestMillis, long highestMillis) {
    assertThat(policy(10, () -> 0.0).backoff(attempts)).isEqualTo(Duration.ofMillis(lowestMillis));
    assertThat(policy(10, () -> Math.nextDown(1.0)).backoff(attempts))
        .isEqualTo(Duration.ofMillis(highestMillis));
    assertThat(policy(10, () -> 0.5).backoff(attempts))
        .isEqualTo(Duration.ofMillis(lowestMillis * 3 / 2));
  }

  @Test
  void nextAttemptUsesTheBackoff() {
    assertThat(policy(10, () -> 0.0).nextAttemptAt(NOW, 2, null)).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void retryAfterIsHonouredExactlyWithoutJitter() {
    RetryPolicy policy = policy(10, () -> 0.0);

    assertThat(policy.nextAttemptAt(NOW, 1, Duration.ofSeconds(60))).isEqualTo(NOW.plusSeconds(60));
    assertThat(policy.nextAttemptAt(NOW, 9, Duration.ofSeconds(120)))
        .isEqualTo(NOW.plusSeconds(120));
  }

  @Test
  void exhaustedAtTheConfiguredLimit() {
    RetryPolicy defaults = policy(10, () -> 0.0);
    assertThat(defaults.exhausted(9)).isFalse();
    assertThat(defaults.exhausted(10)).isTrue();

    RetryPolicy three = policy(3, () -> 0.0);
    assertThat(three.exhausted(2)).isFalse();
    assertThat(three.exhausted(3)).isTrue();
  }

  private static RetryPolicy policy(int maxAttempts, java.util.function.DoubleSupplier random) {
    return new RetryPolicy(
        new OutboxProperties(
            Duration.ofSeconds(2),
            20,
            Duration.ofMinutes(2),
            maxAttempts,
            Duration.ofSeconds(10),
            Duration.ofMinutes(15),
            Duration.ofMinutes(15)),
        random);
  }
}

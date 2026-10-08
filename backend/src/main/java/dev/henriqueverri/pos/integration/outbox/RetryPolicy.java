package dev.henriqueverri.pos.integration.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * When a transiently failed event may be tried again (section 8.6 of the plan): after the n-th
 * attempt since the last manual retry, {@code min(maxDelay, baseDelay × 2^(n−1))} times a jitter in
 * [0.5, 1.0], or exactly what {@code Retry-After} asked for. With the defaults: 10 s, 20 s, 40 s,
 * …, 640 s, 15 min, 15 min, then {@code FAILED/EXHAUSTED} on the 10th attempt.
 */
@Component
public class RetryPolicy {

  private final OutboxProperties settings;
  private final DoubleSupplier random;

  @Autowired
  public RetryPolicy(OutboxProperties settings) {
    this(settings, () -> ThreadLocalRandom.current().nextDouble());
  }

  /** {@code random} yields values in [0, 1). */
  RetryPolicy(OutboxProperties settings, DoubleSupplier random) {
    this.settings = settings;
    this.random = random;
  }

  public boolean exhausted(int attemptsSinceRetry) {
    return attemptsSinceRetry >= settings.maxAttempts();
  }

  public Instant nextAttemptAt(Instant now, int attemptsSinceRetry, Duration retryAfter) {
    return now.plus(retryAfter != null ? retryAfter : backoff(attemptsSinceRetry));
  }

  Duration backoff(int attemptsSinceRetry) {
    double jitter = 0.5 + 0.5 * random.getAsDouble();
    return Duration.ofMillis(Math.round(nominalDelay(attemptsSinceRetry).toMillis() * jitter));
  }

  Duration nominalDelay(int attemptsSinceRetry) {
    int exponent = Math.min(Math.max(attemptsSinceRetry, 1) - 1, 30);
    Duration delay = settings.baseDelay().multipliedBy(1L << exponent);
    return delay.compareTo(settings.maxDelay()) > 0 ? settings.maxDelay() : delay;
  }
}

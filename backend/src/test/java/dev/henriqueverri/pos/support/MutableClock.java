package dev.henriqueverri.pos.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The application clock in integration tests: real time plus an offset, so a test can jump past a
 * backoff or a pause instead of sleeping. Tests that move it call {@link #reset()} afterwards.
 */
public class MutableClock extends Clock {

  private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

  public void advance(Duration amount) {
    offset.updateAndGet(current -> current.plus(amount));
  }

  public void reset() {
    offset.set(Duration.ZERO);
  }

  @Override
  public Instant instant() {
    return Instant.now().plus(offset.get());
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("UTC only");
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }
}

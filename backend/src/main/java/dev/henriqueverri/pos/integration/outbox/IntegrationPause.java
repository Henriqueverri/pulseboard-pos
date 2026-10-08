package dev.henriqueverri.pos.integration.outbox;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pauses the whole integration after a 401 (section 8.7 of the plan): with a bad key every event
 * would fail the same way, so the worker claims nothing until the cooldown ends — at most one
 * request per cooldown, never a loop. In memory on purpose: fixing the key means a restart, which
 * also clears the pause. The end of a pause (cooldown elapsed, or lifted by the manual retry of
 * configuration failures) is logged once.
 */
@Component
public class IntegrationPause {

  private static final Logger log = LoggerFactory.getLogger(IntegrationPause.class);

  private final OutboxProperties settings;
  private final AtomicReference<Instant> pausedUntil = new AtomicReference<>();

  public IntegrationPause(OutboxProperties settings) {
    this.settings = settings;
  }

  public Instant pause(Instant now) {
    Instant until = now.plus(settings.configurationPause());
    pausedUntil.set(until);
    return until;
  }

  public boolean isPaused(Instant now) {
    return pausedUntil(now) != null;
  }

  /** The end of the current pause, or null when not paused. */
  public Instant pausedUntil(Instant now) {
    Instant until = pausedUntil.get();
    if (until == null) {
      return null;
    }
    if (now.isBefore(until)) {
      return until;
    }
    if (pausedUntil.compareAndSet(until, null)) {
      resumed("cooldown_elapsed");
    }
    return null;
  }

  public void clear() {
    if (pausedUntil.getAndSet(null) != null) {
      resumed("cleared");
    }
  }

  private static void resumed(String reason) {
    log.atInfo()
        .addKeyValue("outcome", "integration_resumed")
        .addKeyValue("reason", reason)
        .log("PulseBoard integration resumed");
  }
}

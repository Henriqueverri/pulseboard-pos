package dev.henriqueverri.pos.integration.outbox;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Pauses the whole integration after a 401 (section 8.7 of the plan): with a bad key every event
 * would fail the same way, so the worker claims nothing until the cooldown ends — at most one
 * request per cooldown, never a loop. In memory on purpose: fixing the key means a restart, which
 * also clears the pause.
 */
@Component
public class IntegrationPause {

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
    return until != null && now.isBefore(until) ? until : null;
  }

  public void clear() {
    pausedUntil.set(null);
  }
}

package dev.henriqueverri.pos.integration;

import dev.henriqueverri.pos.integration.outbox.IntegrationPause;
import dev.henriqueverri.pos.integration.outbox.OutboxRepository;
import dev.henriqueverri.pos.integration.outbox.OutboxStatus;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * The {@code pulseBoard} component of {@code /actuator/health}. It reflects the local state of the
 * integration only — configuration, pause and backlog — and never calls PulseBoard: a health check
 * must not depend on (or put load on) an external system.
 *
 * <p>Always {@code UP}: the POS keeps selling while the integration is off, paused or misconfigured
 * (events wait in the outbox), so none of these makes the application unhealthy. The {@code state}
 * detail says which one applies. Details are only shown to authorized users (see {@code
 * management.endpoint.health}) and never include the key, only its public prefix.
 */
@Component("pulseBoard")
public class PulseBoardHealthIndicator implements HealthIndicator {

  /** What the worker is doing about PulseBoard right now. */
  public enum State {
    /** {@code PULSEBOARD_INTEGRATION_ENABLED=false}: events accumulate as {@code PENDING}. */
    DISABLED,
    /** Enabled without an API key: same as disabled. */
    NOT_CONFIGURED,
    /** The key is not {@code pb_<prefix>_<secret>}: PulseBoard will answer 401. */
    INVALID_KEY,
    /** A 401 paused the integration until {@code pausedUntil}. */
    PAUSED,
    /** Delivering. */
    ACTIVE
  }

  private final PulseBoardProperties pulseBoard;
  private final IntegrationPause pause;
  private final OutboxRepository events;
  private final Clock clock;

  public PulseBoardHealthIndicator(
      PulseBoardProperties pulseBoard,
      IntegrationPause pause,
      OutboxRepository events,
      Clock clock) {
    this.pulseBoard = pulseBoard;
    this.pause = pause;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public Health health() {
    Instant pausedUntil = pause.pausedUntil(clock.instant());
    Health.Builder health =
        Health.up()
            .withDetail("state", state(pausedUntil))
            .withDetail("enabled", pulseBoard.enabled())
            .withDetail("apiUrl", withoutUserInfo(pulseBoard.apiUrl()));
    String keyPrefix = pulseBoard.keyPrefix();
    if (keyPrefix != null) {
      health.withDetail("keyPrefix", keyPrefix);
    }
    if (pausedUntil != null) {
      health.withDetail("pausedUntil", pausedUntil.toString());
    }
    try {
      health
          .withDetail(
              "pending",
              events.countByStatusIn(Set.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING)))
          .withDetail("failed", events.countByStatusIn(Set.of(OutboxStatus.FAILED)));
    } catch (DataAccessException e) {
      // The database has its own health component; the integration state is still meaningful.
    }
    return health.build();
  }

  private State state(Instant pausedUntil) {
    if (!pulseBoard.enabled()) {
      return State.DISABLED;
    }
    if (pulseBoard.apiKey().isBlank()) {
      return State.NOT_CONFIGURED;
    }
    if (pausedUntil != null) {
      return State.PAUSED;
    }
    if (pulseBoard.keyPrefix() == null) {
      return State.INVALID_KEY;
    }
    return State.ACTIVE;
  }

  private static String withoutUserInfo(URI url) {
    if (url.getUserInfo() == null) {
      return url.toString();
    }
    String port = url.getPort() == -1 ? "" : ":" + url.getPort();
    String path = url.getRawPath() == null ? "" : url.getRawPath();
    return url.getScheme() + "://" + url.getHost() + port + path;
  }
}

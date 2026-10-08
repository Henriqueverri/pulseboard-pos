package dev.henriqueverri.pos.integration.outbox;

import java.util.UUID;

/**
 * An event a worker holds the lease for. {@code attempts} already counts the current attempt and
 * works as a fencing token: results are only recorded if nobody re-claimed the event meanwhile.
 */
public record ClaimedEvent(
    UUID id,
    long sequence,
    UUID aggregateId,
    OutboxEventType type,
    String payload,
    int attempts,
    int attemptsAtRetry) {

  public String requestId() {
    return "pos-" + id;
  }

  /** Attempts that count towards the limit: those since the last manual retry. */
  public int attemptsSinceRetry() {
    return attempts - attemptsAtRetry;
  }
}

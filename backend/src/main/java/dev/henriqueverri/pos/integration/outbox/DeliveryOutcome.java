package dev.henriqueverri.pos.integration.outbox;

import java.time.Duration;
import java.util.UUID;

/** What a PulseBoard result means for the event (section 8.5 of the plan). */
public sealed interface DeliveryOutcome {

  /** 201, or 200 replay. {@code remoteStatus} is the transaction's status in the body. */
  record Sent(
      int httpStatus, String requestId, boolean replayed, UUID remoteId, String remoteStatus)
      implements DeliveryOutcome {}

  /**
   * Worth trying again later: 429, 5xx, timeout, network. {@code retryAfter} is set when the server
   * said when (429); otherwise the backoff decides.
   */
  record Transient(
      Integer httpStatus, String requestId, String code, String message, Duration retryAfter)
      implements DeliveryOutcome {}

  /** Repeating the same request cannot succeed. */
  record Failed(FailureKind kind, Integer httpStatus, String requestId, String code, String message)
      implements DeliveryOutcome {}

  /** 409 {@code invalid_transition} of a refund: ask PulseBoard for the current state. */
  record Reconcile(int httpStatus, String requestId, String code, String message)
      implements DeliveryOutcome {}
}

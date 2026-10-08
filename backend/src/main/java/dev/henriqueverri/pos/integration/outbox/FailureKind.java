package dev.henriqueverri.pos.integration.outbox;

/** Why an event is {@link OutboxStatus#FAILED}; none of them is retried automatically. */
public enum FailureKind {
  /**
   * PulseBoard rejected the content (422, 413, 409, 404): resending the same payload fails again.
   */
  PERMANENT,
  /** Transient failures until the attempt limit. */
  EXHAUSTED,
  /** 401: the API key is invalid, revoked or expired. */
  CONFIGURATION
}

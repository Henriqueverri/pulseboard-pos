package dev.henriqueverri.pos.integration.outbox;

public enum OutboxStatus {
  /** Waiting to be claimed by the worker. */
  PENDING,
  /** Claimed by a worker that holds the lease ({@code locked_until}). */
  PROCESSING,
  /** Accepted by PulseBoard (201, or 200 replay). Final. */
  SENT,
  /** Rejected; the last attempt's data explains why. */
  FAILED
}

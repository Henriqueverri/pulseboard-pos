package dev.henriqueverri.pos.integration.outbox;

/** Order events delivered to PulseBoard. Only paid orders exist there, so there is no cancel. */
public enum OutboxEventType {
  ORDER_PAID,
  ORDER_REFUNDED
}

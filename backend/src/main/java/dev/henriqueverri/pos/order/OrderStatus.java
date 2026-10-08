package dev.henriqueverri.pos.order;

/**
 * Order lifecycle. The only transitions are {@code PENDING → PAID}, {@code PENDING → CANCELED} and
 * {@code PAID → REFUNDED}; {@code CANCELED} and {@code REFUNDED} are terminal. {@code CANCELED}
 * (one L) matches the PulseBoard status.
 */
public enum OrderStatus {
  PENDING,
  PAID,
  CANCELED,
  REFUNDED;

  public boolean canTransitionTo(OrderStatus target) {
    return switch (this) {
      case PENDING -> target == PAID || target == CANCELED;
      case PAID -> target == REFUNDED;
      case CANCELED, REFUNDED -> false;
    };
  }
}

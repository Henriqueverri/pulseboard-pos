package dev.henriqueverri.pos.integration.outbox;

/**
 * Events delivered to PulseBoard. Order events: only paid orders exist there, so there is no
 * cancel; they are delivered in order. Product events carry the absolute stock balance and the
 * instant it changed, so PulseBoard keeps the newest and they never wait for each other.
 */
public enum OutboxEventType {
  ORDER_PAID(OutboxEvent.AGGREGATE_ORDER),
  ORDER_REFUNDED(OutboxEvent.AGGREGATE_ORDER),
  STOCK_CHANGED(OutboxEvent.AGGREGATE_PRODUCT);

  private final String aggregateType;

  OutboxEventType(String aggregateType) {
    this.aggregateType = aggregateType;
  }

  public String aggregateType() {
    return aggregateType;
  }

  public boolean isOrderEvent() {
    return OutboxEvent.AGGREGATE_ORDER.equals(aggregateType);
  }
}

package dev.henriqueverri.pos.order;

import static dev.henriqueverri.pos.order.OrderStatus.CANCELED;
import static dev.henriqueverri.pos.order.OrderStatus.PAID;
import static dev.henriqueverri.pos.order.OrderStatus.PENDING;
import static dev.henriqueverri.pos.order.OrderStatus.REFUNDED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

  private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED =
      Map.of(
          PENDING, EnumSet.of(PAID, CANCELED),
          PAID, EnumSet.of(REFUNDED),
          CANCELED, EnumSet.noneOf(OrderStatus.class),
          REFUNDED, EnumSet.noneOf(OrderStatus.class));

  @Test
  void fullTransitionMatrix() {
    for (OrderStatus from : OrderStatus.values()) {
      for (OrderStatus to : OrderStatus.values()) {
        assertThat(from.canTransitionTo(to))
            .as("%s -> %s", from, to)
            .isEqualTo(ALLOWED.get(from).contains(to));
      }
    }
  }
}

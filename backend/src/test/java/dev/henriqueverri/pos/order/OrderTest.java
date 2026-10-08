package dev.henriqueverri.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.henriqueverri.pos.catalog.Product;
import dev.henriqueverri.pos.customer.Customer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderTest {

  private static final Instant CREATED = Instant.parse("2026-10-06T21:00:00.123456789Z");
  private static final Instant LATER = Instant.parse("2026-10-06T21:14:05.987654321Z");

  private final Customer customer = new Customer("Ana", "ana@cliente.pos.example", null, CREATED);

  @Test
  void placesPendingOrderWithSnapshotsAndServerTotals() {
    Product mouse = product("PB-001-ESS", "79.90");
    Product keyboard = product("PB-002-ESS", "249.90");

    Order order =
        Order.place(
            7,
            customer,
            "BRL",
            List.of(new Order.Line(mouse, 2), new Order.Line(keyboard, 1)),
            CREATED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getNumber()).isEqualTo(7);
    assertThat(order.getItems())
        .extracting(OrderItem::getLineTotal)
        .containsExactly(new BigDecimal("159.80"), new BigDecimal("249.90"));
    assertThat(order.getTotal()).isEqualTo(new BigDecimal("409.70"));
    assertThat(order.getItems().get(0).getSku()).isEqualTo("PB-001-ESS");
    assertThat(order.getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal("79.90"));

    mouse.update("PB-001-ESS", "Renamed", new BigDecimal("1.00"), true, LATER);
    assertThat(order.getItems().get(0).getProductName()).isEqualTo("Mouse PB-001-ESS");
    assertThat(order.getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal("79.90"));
  }

  @Test
  void rejectsEmptyDuplicatedAndOutOfRangeLines() {
    Product mouse = product("PB-001-ESS", "79.90");

    assertThatThrownBy(() -> Order.place(1, customer, "BRL", List.of(), CREATED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Order.place(
                    1,
                    customer,
                    "BRL",
                    List.of(new Order.Line(mouse, 1), new Order.Line(mouse, 1)),
                    CREATED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Order.place(1, customer, "BRL", List.of(new Order.Line(mouse, 0)), CREATED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Order.place(1, customer, "BRL", List.of(new Order.Line(mouse, 10_001)), CREATED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void payRecordsMethodAndWholeSecondTimestamp() {
    Order order = pendingOrder();

    order.pay(PaymentMethod.PIX, LATER);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
    assertThat(order.getPaidAt()).isEqualTo(Instant.parse("2026-10-06T21:14:05Z"));
  }

  @Test
  void refundAndCancelRecordTimestamps() {
    Order paid = pendingOrder();
    paid.pay(PaymentMethod.CASH, LATER);
    paid.refund(LATER.plusSeconds(60));
    assertThat(paid.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(paid.getRefundedAt()).isEqualTo(Instant.parse("2026-10-06T21:15:05Z"));

    Order canceled = pendingOrder();
    canceled.cancel(LATER);
    assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(canceled.getCanceledAt()).isEqualTo(Instant.parse("2026-10-06T21:14:05Z"));
    assertThat(canceled.getPaidAt()).isNull();
  }

  @Test
  void invalidTransitionsThrowAndLeaveTheOrderUntouched() {
    assertInvalid(pendingOrder(), (order, now) -> order.refund(now), OrderStatus.PENDING);

    Order canceled = pendingOrder();
    canceled.cancel(LATER);
    assertInvalid(
        canceled, (order, now) -> order.pay(PaymentMethod.CARD, now), OrderStatus.CANCELED);
    assertInvalid(canceled, (order, now) -> order.refund(now), OrderStatus.CANCELED);
    assertInvalid(canceled, (order, now) -> order.cancel(now), OrderStatus.CANCELED);

    Order paid = pendingOrder();
    paid.pay(PaymentMethod.CARD, LATER);
    assertInvalid(paid, (order, now) -> order.pay(PaymentMethod.CASH, now), OrderStatus.PAID);
    assertInvalid(paid, (order, now) -> order.cancel(now), OrderStatus.PAID);
    assertThat(paid.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);

    paid.refund(LATER);
    assertInvalid(paid, (order, now) -> order.pay(PaymentMethod.CASH, now), OrderStatus.REFUNDED);
    assertInvalid(paid, (order, now) -> order.cancel(now), OrderStatus.REFUNDED);
    assertInvalid(paid, (order, now) -> order.refund(now), OrderStatus.REFUNDED);
  }

  private void assertInvalid(
      Order order, BiConsumer<Order, Instant> action, OrderStatus expectedStatus) {
    Instant updatedAt = order.getUpdatedAt();
    assertThatThrownBy(() -> action.accept(order, LATER.plusSeconds(3600)))
        .isInstanceOf(InvalidOrderTransitionException.class)
        .extracting("code")
        .isEqualTo("invalid_order_transition");
    assertThat(order.getStatus()).isEqualTo(expectedStatus);
    assertThat(order.getUpdatedAt()).isEqualTo(updatedAt);
  }

  private Order pendingOrder() {
    return Order.place(
        1, customer, "BRL", List.of(new Order.Line(product("PB-001-ESS", "79.90"), 1)), CREATED);
  }

  private static Product product(String sku, String price) {
    Product product = new Product(sku, "Mouse " + sku, new BigDecimal(price), true, CREATED);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    return product;
  }
}

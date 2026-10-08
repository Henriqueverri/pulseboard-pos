package dev.henriqueverri.pos.integration.pulseboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.henriqueverri.pos.catalog.Product;
import dev.henriqueverri.pos.customer.Customer;
import dev.henriqueverri.pos.order.Order;
import dev.henriqueverri.pos.order.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IngestPayloadFactoryTest {

  private static final Instant CREATED = Instant.parse("2026-10-06T21:00:00Z");
  private static final UUID ORDER_ID = UUID.fromString("7b1e2c4a-9f0d-4c1e-8a55-0d6f3b2a9c10");
  private static final UUID CUSTOMER_ID = UUID.fromString("3f6a0d2e-1b7c-4e90-a2d4-5c8e9f01b234");

  private final IngestPayloadFactory factory = new IngestPayloadFactory();

  /** The example of section 7.3 of the plan, byte for byte. */
  @Test
  void orderPaidIsTheExactIngestionBody() {
    Order order = paidOrder(Instant.parse("2026-10-06T21:14:05.987654321Z"));

    assertThat(factory.orderPaid(order))
        .isEqualTo(
            "{\"external_id\":\"pos:7b1e2c4a-9f0d-4c1e-8a55-0d6f3b2a9c10\","
                + "\"status\":\"paid\","
                + "\"occurred_at\":\"2026-10-06T21:14:05Z\","
                + "\"currency\":\"BRL\","
                + "\"customer\":{\"external_id\":\"pos:cus:3f6a0d2e-1b7c-4e90-a2d4-5c8e9f01b234\","
                + "\"name\":\"Ana Lima\",\"email\":\"ana.lima@cliente.pos.example\"},"
                + "\"items\":["
                + "{\"sku\":\"PB-001-ESS\",\"quantity\":2,\"unit_price\":\"79.90\"},"
                + "{\"sku\":\"PB-002-ESS\",\"quantity\":1,\"unit_price\":\"249.90\"}],"
                + "\"total_amount\":\"409.70\"}");
  }

  @Test
  void orderRefundedIsTheExactStatusChangeBody() {
    Order order = paidOrder(Instant.parse("2026-10-06T21:14:05Z"));
    order.refund(Instant.parse("2026-10-06T22:03:40.5Z"));

    assertThat(factory.orderRefunded(order))
        .isEqualTo("{\"status\":\"refunded\",\"occurred_at\":\"2026-10-06T22:03:40Z\"}");
  }

  @Test
  void keepsWholeUnitsWithTwoDecimals() {
    Order order =
        Order.place(
            1, customer(), "BRL", List.of(new Order.Line(product("PB-003-ESS", "10"), 3)), CREATED);
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    order.pay(PaymentMethod.CASH, Instant.parse("2026-10-06T21:14:05Z"));

    assertThat(factory.orderPaid(order))
        .contains("{\"sku\":\"PB-003-ESS\",\"quantity\":3,\"unit_price\":\"10.00\"}")
        .endsWith("\"total_amount\":\"30.00\"}");
  }

  @Test
  void refusesAnOrderThatIsNotPaid() {
    Order pending =
        Order.place(
            1,
            customer(),
            "BRL",
            List.of(new Order.Line(product("PB-001-ESS", "79.90"), 1)),
            CREATED);

    assertThatThrownBy(() -> factory.orderPaid(pending)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void externalIdsArePrefixedUuids() {
    assertThat(IngestPayloadFactory.orderExternalId(ORDER_ID))
        .isEqualTo("pos:7b1e2c4a-9f0d-4c1e-8a55-0d6f3b2a9c10");
    assertThat(IngestPayloadFactory.customerExternalId(CUSTOMER_ID))
        .isEqualTo("pos:cus:3f6a0d2e-1b7c-4e90-a2d4-5c8e9f01b234");
  }

  private Order paidOrder(Instant paidAt) {
    Order order =
        Order.place(
            512,
            customer(),
            "BRL",
            List.of(
                new Order.Line(product("PB-001-ESS", "79.90"), 2),
                new Order.Line(product("PB-002-ESS", "249.90"), 1)),
            CREATED);
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    order.pay(PaymentMethod.PIX, paidAt);
    return order;
  }

  /** The document is internal to the POS: present here, absent from the payload. */
  private static Customer customer() {
    Customer customer =
        new Customer("Ana Lima", "ana.lima@cliente.pos.example", "123.456.789-00", CREATED);
    ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
    return customer;
  }

  private static Product product(String sku, String price) {
    Product product = new Product(sku, "Produto " + sku, new BigDecimal(price), true, CREATED);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    return product;
  }
}

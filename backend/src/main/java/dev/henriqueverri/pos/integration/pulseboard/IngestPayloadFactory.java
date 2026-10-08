package dev.henriqueverri.pos.integration.pulseboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.henriqueverri.pos.customer.Customer;
import dev.henriqueverri.pos.order.Order;
import dev.henriqueverri.pos.order.OrderItem;
import dev.henriqueverri.pos.shared.money.Money;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the ingestion bodies from the order at the moment the event is created. The result is
 * stored as the event's snapshot and re-sent unchanged on every attempt, which keeps PulseBoard's
 * idempotency fingerprint stable. Money goes as decimal strings, instants as UTC seconds with
 * {@code Z}; {@code document}, {@code payment_method} and {@code number} stay in the POS.
 */
@Component
public class IngestPayloadFactory {

  private static final ObjectMapper JSON = new ObjectMapper();

  public static String orderExternalId(UUID orderId) {
    return "pos:" + orderId;
  }

  public static String customerExternalId(UUID customerId) {
    return "pos:cus:" + customerId;
  }

  /** Body of {@code POST /ingest/transactions}. */
  public String orderPaid(Order order) {
    ObjectNode body = JSON.createObjectNode();
    body.put("external_id", orderExternalId(order.getId()));
    body.put("status", "paid");
    body.put("occurred_at", utc(order.getPaidAt()));
    body.put("currency", order.getCurrency());

    Customer customer = order.getCustomer();
    ObjectNode customerNode = body.putObject("customer");
    customerNode.put("external_id", customerExternalId(customer.getId()));
    customerNode.put("name", customer.getName());
    customerNode.put("email", customer.getEmail());

    ArrayNode items = body.putArray("items");
    for (OrderItem item : order.getItems()) {
      items
          .addObject()
          .put("sku", item.getSku())
          .put("quantity", item.getQuantity())
          .put("unit_price", Money.format(item.getUnitPrice()));
    }
    body.put("total_amount", Money.format(order.getTotal()));
    return write(body);
  }

  /** Body of {@code POST /ingest/transactions/{external_id}/status-changes}. */
  public String orderRefunded(Order order) {
    ObjectNode body = JSON.createObjectNode();
    body.put("status", "refunded");
    body.put("occurred_at", utc(order.getRefundedAt()));
    return write(body);
  }

  private static String utc(Instant instant) {
    if (instant == null) {
      throw new IllegalStateException("the order has no timestamp for this event");
    }
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }

  private static String write(ObjectNode body) {
    try {
      return JSON.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize the ingestion payload", e);
    }
  }
}

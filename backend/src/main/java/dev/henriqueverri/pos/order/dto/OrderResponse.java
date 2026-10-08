package dev.henriqueverri.pos.order.dto;

import dev.henriqueverri.pos.order.Order;
import dev.henriqueverri.pos.order.OrderItem;
import dev.henriqueverri.pos.order.OrderStatus;
import dev.henriqueverri.pos.order.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    long number,
    OrderStatus status,
    String currency,
    PaymentMethod paymentMethod,
    OrderCustomer customer,
    List<Item> items,
    BigDecimal total,
    Instant createdAt,
    Instant updatedAt,
    Instant paidAt,
    Instant canceledAt,
    Instant refundedAt) {

  public record Item(
      UUID id,
      UUID productId,
      String sku,
      String productName,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal) {

    static Item from(OrderItem item) {
      return new Item(
          item.getId(),
          item.getProductId(),
          item.getSku(),
          item.getProductName(),
          item.getQuantity(),
          item.getUnitPrice(),
          item.getLineTotal());
    }
  }

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getNumber(),
        order.getStatus(),
        order.getCurrency(),
        order.getPaymentMethod(),
        OrderCustomer.from(order.getCustomer()),
        order.getItems().stream().map(Item::from).toList(),
        order.getTotal(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getPaidAt(),
        order.getCanceledAt(),
        order.getRefundedAt());
  }
}

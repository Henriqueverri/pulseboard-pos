package dev.henriqueverri.pos.order.dto;

import dev.henriqueverri.pos.order.Order;
import dev.henriqueverri.pos.order.OrderStatus;
import dev.henriqueverri.pos.order.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** List row: the order without its items. */
public record OrderSummaryResponse(
    UUID id,
    long number,
    OrderStatus status,
    String currency,
    PaymentMethod paymentMethod,
    OrderCustomer customer,
    BigDecimal total,
    Instant createdAt,
    Instant paidAt,
    Instant canceledAt,
    Instant refundedAt) {

  public static OrderSummaryResponse from(Order order) {
    return new OrderSummaryResponse(
        order.getId(),
        order.getNumber(),
        order.getStatus(),
        order.getCurrency(),
        order.getPaymentMethod(),
        OrderCustomer.from(order.getCustomer()),
        order.getTotal(),
        order.getCreatedAt(),
        order.getPaidAt(),
        order.getCanceledAt(),
        order.getRefundedAt());
  }
}

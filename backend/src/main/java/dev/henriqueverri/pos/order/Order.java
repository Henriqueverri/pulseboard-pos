package dev.henriqueverri.pos.order;

import dev.henriqueverri.pos.catalog.Product;
import dev.henriqueverri.pos.customer.Customer;
import dev.henriqueverri.pos.shared.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Order aggregate. The server is the authority on prices and totals: they come from the catalog and
 * are computed here, never taken from the client. {@code @Version} turns two concurrent changes of
 * the same order (e.g. pay and cancel) into an optimistic locking conflict.
 */
@Entity(name = "PosOrder")
@Table(name = "orders")
public class Order {

  public static final int MAX_ITEMS = 100;
  public static final int MAX_QUANTITY = 10_000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private long number;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false, updatable = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private OrderStatus status;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", length = 16)
  private PaymentMethod paymentMethod;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sku")
  private List<OrderItem> items = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  @Version
  @Column(nullable = false)
  private Integer version;

  protected Order() {}

  public record Line(Product product, int quantity) {}

  /** Places a {@code PENDING} order with catalog prices and a server-computed total. */
  public static Order place(
      long number, Customer customer, String currency, List<Line> lines, Instant now) {
    if (lines.isEmpty() || lines.size() > MAX_ITEMS) {
      throw new IllegalArgumentException("an order has 1 to " + MAX_ITEMS + " items");
    }
    Order order = new Order();
    order.number = number;
    order.customer = customer;
    order.currency = currency;
    order.status = OrderStatus.PENDING;
    order.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    order.updatedAt = order.createdAt;
    Set<UUID> products = new HashSet<>();
    for (Line line : lines) {
      if (!products.add(line.product().getId())) {
        throw new IllegalArgumentException("a product appears only once per order");
      }
      if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
        throw new IllegalArgumentException("quantity must be 1 to " + MAX_QUANTITY);
      }
      order.items.add(new OrderItem(order, line.product(), line.quantity()));
    }
    order.total = Money.sum(order.items.stream().map(OrderItem::getLineTotal).toList());
    return order;
  }

  public void pay(PaymentMethod method, Instant now) {
    transitionTo(OrderStatus.PAID, now);
    this.paymentMethod = method;
    this.paidAt = toSeconds(now);
  }

  public void cancel(Instant now) {
    transitionTo(OrderStatus.CANCELED, now);
    this.canceledAt = toSeconds(now);
  }

  public void refund(Instant now) {
    transitionTo(OrderStatus.REFUNDED, now);
    this.refundedAt = toSeconds(now);
  }

  private void transitionTo(OrderStatus target, Instant now) {
    if (!status.canTransitionTo(target)) {
      throw new InvalidOrderTransitionException(status, target);
    }
    this.status = target;
    this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
  }

  /** Lifecycle timestamps become the PulseBoard {@code occurred_at}, which uses whole seconds. */
  private static Instant toSeconds(Instant instant) {
    return instant.truncatedTo(ChronoUnit.SECONDS);
  }

  public UUID getId() {
    return id;
  }

  public long getNumber() {
    return number;
  }

  public Customer getCustomer() {
    return customer;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public Instant getRefundedAt() {
    return refundedAt;
  }

  public Integer getVersion() {
    return version;
  }
}

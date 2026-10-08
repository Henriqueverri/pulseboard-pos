package dev.henriqueverri.pos.order;

import dev.henriqueverri.pos.catalog.Product;
import dev.henriqueverri.pos.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** A line of an order. SKU, name and price are snapshots taken when the order was created. */
@Entity
@Table(name = "order_items")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @Column(nullable = false, length = 64, updatable = false)
  private String sku;

  @Column(name = "product_name", nullable = false, length = 120, updatable = false)
  private String productName;

  @Column(nullable = false, updatable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
  private BigDecimal unitPrice;

  @Column(name = "line_total", nullable = false, precision = 12, scale = 2, updatable = false)
  private BigDecimal lineTotal;

  protected OrderItem() {}

  OrderItem(Order order, Product product, int quantity) {
    this.order = order;
    this.product = product;
    this.sku = product.getSku();
    this.productName = product.getName();
    this.quantity = quantity;
    this.unitPrice = product.getPrice();
    this.lineTotal = Money.multiply(unitPrice, quantity);
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return product.getId();
  }

  public String getSku() {
    return sku;
  }

  public String getProductName() {
    return productName;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }
}

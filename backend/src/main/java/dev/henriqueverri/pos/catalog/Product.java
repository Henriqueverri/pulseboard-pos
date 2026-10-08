package dev.henriqueverri.pos.catalog;

import dev.henriqueverri.pos.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 64)
  private String sku;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Product() {}

  public Product(String sku, String name, BigDecimal price, boolean active, Instant now) {
    this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    update(sku, name, price, active, now);
  }

  public void update(String sku, String name, BigDecimal price, boolean active, Instant now) {
    this.sku = sku;
    this.name = name;
    this.price = Money.normalize(price);
    this.active = active;
    this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
  }

  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

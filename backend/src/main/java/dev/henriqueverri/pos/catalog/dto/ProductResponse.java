package dev.henriqueverri.pos.catalog.dto;

import dev.henriqueverri.pos.catalog.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String sku,
    String name,
    BigDecimal price,
    boolean active,
    boolean trackStock,
    int stockQuantity,
    int stockMin,
    boolean lowStock,
    Instant createdAt,
    Instant updatedAt) {

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getSku(),
        product.getName(),
        product.getPrice(),
        product.isActive(),
        product.isTrackStock(),
        product.getStockQuantity(),
        product.getStockMin(),
        product.isLowStock(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }
}

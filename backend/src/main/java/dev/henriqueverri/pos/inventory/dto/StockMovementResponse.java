package dev.henriqueverri.pos.inventory.dto;

import dev.henriqueverri.pos.inventory.StockMovementKind;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
    UUID id,
    UUID productId,
    StockMovementKind kind,
    int quantity,
    int balanceAfter,
    UUID orderId,
    Long orderNumber,
    String reason,
    String userName,
    Instant createdAt) {}

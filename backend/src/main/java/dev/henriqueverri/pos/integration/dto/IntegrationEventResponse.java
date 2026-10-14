package dev.henriqueverri.pos.integration.dto;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.integration.outbox.FailureKind;
import dev.henriqueverri.pos.integration.outbox.OutboxEvent;
import dev.henriqueverri.pos.integration.outbox.OutboxEventType;
import dev.henriqueverri.pos.integration.outbox.OutboxStatus;
import dev.henriqueverri.pos.integration.pulseboard.IngestPayloadFactory;
import java.time.Instant;
import java.util.UUID;

/**
 * One outbox event as the Integration screen shows it. Order events fill the {@code order*} fields
 * and stock events the {@code product*} ones; {@code externalId} is how PulseBoard knows the
 * subject (the order's external id, or the product's SKU). {@code blockedBy} is the {@code
 * sequence} of the earlier, not yet sent event of the same order that holds this one back; {@code
 * payload} (the exact body sent to PulseBoard) only comes in the detail.
 */
public record IntegrationEventResponse(
    UUID id,
    long sequence,
    String aggregateType,
    UUID orderId,
    Long orderNumber,
    UUID productId,
    String productSku,
    String productName,
    String externalId,
    OutboxEventType eventType,
    OutboxStatus status,
    FailureKind failureKind,
    int attempts,
    int attemptsAtRetry,
    Instant nextAttemptAt,
    Instant lastAttemptAt,
    Integer lastHttpStatus,
    String lastErrorCode,
    String lastError,
    String requestId,
    String lastRequestId,
    UUID remoteId,
    Instant processedAt,
    Instant createdAt,
    Instant updatedAt,
    Long blockedBy,
    JsonNode payload) {

  public static IntegrationEventResponse from(
      OutboxEvent event, Long orderNumber, String productName, Long blockedBy, JsonNode payload) {
    boolean order = event.getEventType().isOrderEvent();
    String sku = order ? null : IngestPayloadFactory.stockSku(event.getPayload());
    return new IntegrationEventResponse(
        event.getId(),
        event.getSequence(),
        event.getAggregateType(),
        order ? event.getAggregateId() : null,
        order ? orderNumber : null,
        order ? null : event.getAggregateId(),
        sku,
        order ? null : productName,
        order ? IngestPayloadFactory.orderExternalId(event.getAggregateId()) : sku,
        event.getEventType(),
        event.getStatus(),
        event.getFailureKind(),
        event.getAttempts(),
        event.getAttemptsAtRetry(),
        event.getStatus() == OutboxStatus.PENDING ? event.getNextAttemptAt() : null,
        event.getLastAttemptAt(),
        event.getLastHttpStatus(),
        event.getLastErrorCode(),
        event.getLastError(),
        event.requestId(),
        event.getLastRequestId(),
        event.getRemoteId(),
        event.getProcessedAt(),
        event.getCreatedAt(),
        event.getUpdatedAt(),
        blockedBy,
        payload);
  }
}

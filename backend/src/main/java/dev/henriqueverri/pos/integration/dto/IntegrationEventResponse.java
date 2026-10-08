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
 * One outbox event as the Integration screen shows it. {@code blockedBy} is the {@code sequence} of
 * the earlier, not yet sent event of the same order that holds this one back; {@code payload} (the
 * exact body sent to PulseBoard) only comes in the detail.
 */
public record IntegrationEventResponse(
    UUID id,
    long sequence,
    UUID orderId,
    Long orderNumber,
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
      OutboxEvent event, Long orderNumber, Long blockedBy, JsonNode payload) {
    return new IntegrationEventResponse(
        event.getId(),
        event.getSequence(),
        event.getAggregateId(),
        orderNumber,
        IngestPayloadFactory.orderExternalId(event.getAggregateId()),
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

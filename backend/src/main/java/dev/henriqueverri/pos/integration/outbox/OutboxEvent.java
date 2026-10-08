package dev.henriqueverri.pos.integration.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One delivery to PulseBoard, written in the same transaction as the order change. The payload is
 * the exact body to send, frozen when the event is created; delivery state only changes through the
 * worker's conditional updates in {@link OutboxRepository}.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  public static final String AGGREGATE_ORDER = "ORDER";

  @Id private UUID id;

  @Column(insertable = false, updatable = false)
  private Long sequence;

  @Column(name = "aggregate_type", nullable = false, updatable = false, length = 32)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, updatable = false, length = 32)
  private OutboxEventType eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "last_http_status")
  private Integer lastHttpStatus;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "last_request_id", length = 64)
  private String lastRequestId;

  @Column(name = "remote_id")
  private UUID remoteId;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OutboxEvent() {}

  static OutboxEvent forOrder(UUID orderId, OutboxEventType type, String payload, Instant now) {
    OutboxEvent event = new OutboxEvent();
    event.id = UUID.randomUUID();
    event.aggregateType = AGGREGATE_ORDER;
    event.aggregateId = orderId;
    event.eventType = type;
    event.payload = payload;
    event.status = OutboxStatus.PENDING;
    event.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    event.updatedAt = event.createdAt;
    return event;
  }

  /** Correlates both sides' logs; the same value on every attempt of this event. */
  public String requestId() {
    return "pos-" + id;
  }

  public UUID getId() {
    return id;
  }

  public Long getSequence() {
    return sequence;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public OutboxEventType getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getLastAttemptAt() {
    return lastAttemptAt;
  }

  public Integer getLastHttpStatus() {
    return lastHttpStatus;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public String getLastError() {
    return lastError;
  }

  public String getLastRequestId() {
    return lastRequestId;
  }

  public UUID getRemoteId() {
    return remoteId;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

package dev.henriqueverri.pos.integration.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The worker's side of the outbox: claiming a batch and recording the outcome of each delivery,
 * each in its own short transaction. The HTTP call happens between them, outside any transaction,
 * so it never holds a database connection or a row lock.
 */
@Repository
public class OutboxStore {

  /**
   * {@code FOR UPDATE SKIP LOCKED}: concurrent claims (two instances, or overlapping runs) never
   * get the same row. An event whose lease expired (worker died mid-delivery) is claimable again.
   */
  private static final String CLAIM =
      """
      UPDATE outbox_events e
      SET status = 'PROCESSING',
          locked_until = :lockedUntil,
          attempts = e.attempts + 1,
          last_attempt_at = :now,
          updated_at = :now
      WHERE e.id IN (
        SELECT c.id
        FROM outbox_events c
        WHERE c.status = 'PENDING'
           OR (c.status = 'PROCESSING' AND c.locked_until < :now)
        ORDER BY c.sequence
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED)
      RETURNING e.id, e.sequence, e.aggregate_id, e.event_type, e.payload::text AS payload, e.attempts
      """;

  private static final String MARK_SENT =
      """
      UPDATE outbox_events
      SET status = 'SENT',
          locked_until = NULL,
          processed_at = :now,
          updated_at = :now,
          last_http_status = :httpStatus,
          last_request_id = :requestId,
          last_error_code = NULL,
          last_error = NULL,
          remote_id = :remoteId
      WHERE id = :id AND status = 'PROCESSING' AND attempts = :attempts
      """;

  private static final String MARK_FAILED =
      """
      UPDATE outbox_events
      SET status = 'FAILED',
          locked_until = NULL,
          updated_at = :now,
          last_http_status = :httpStatus,
          last_request_id = :requestId,
          last_error_code = :errorCode,
          last_error = :error
      WHERE id = :id AND status = 'PROCESSING' AND attempts = :attempts
      """;

  private final JdbcClient jdbc;

  public OutboxStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public List<ClaimedEvent> claim(Instant now, Instant lockedUntil, int batchSize) {
    return jdbc
        .sql(CLAIM)
        .param("now", Timestamp.from(now))
        .param("lockedUntil", Timestamp.from(lockedUntil))
        .param("batchSize", batchSize)
        .query(
            (rs, row) ->
                new ClaimedEvent(
                    rs.getObject("id", UUID.class),
                    rs.getLong("sequence"),
                    rs.getObject("aggregate_id", UUID.class),
                    OutboxEventType.valueOf(rs.getString("event_type")),
                    rs.getString("payload"),
                    rs.getInt("attempts")))
        .list()
        .stream()
        .sorted(Comparator.comparingLong(ClaimedEvent::sequence))
        .toList();
  }

  /** Returns false when the claim was lost (lease expired and re-claimed): nothing is recorded. */
  @Transactional
  public boolean markSent(
      ClaimedEvent event, Instant now, int httpStatus, String requestId, UUID remoteId) {
    return jdbc.sql(MARK_SENT)
            .param("id", event.id())
            .param("attempts", event.attempts())
            .param("now", Timestamp.from(now))
            .param("httpStatus", httpStatus)
            .param("requestId", requestId)
            .param("remoteId", remoteId)
            .update()
        == 1;
  }

  @Transactional
  public boolean markFailed(
      ClaimedEvent event,
      Instant now,
      Integer httpStatus,
      String requestId,
      String errorCode,
      String error) {
    return jdbc.sql(MARK_FAILED)
            .param("id", event.id())
            .param("attempts", event.attempts())
            .param("now", Timestamp.from(now))
            .param("httpStatus", httpStatus)
            .param("requestId", requestId)
            .param("errorCode", errorCode)
            .param("error", error)
            .update()
        == 1;
  }
}

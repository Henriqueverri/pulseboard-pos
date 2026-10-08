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
 * Every change of an event's delivery state, each in its own short transaction. The worker claims a
 * batch, calls PulseBoard outside any transaction (so the call never holds a connection or a row
 * lock) and records the outcome; results are conditioned on the claim still being the worker's.
 */
@Repository
public class OutboxStore {

  /**
   * Section 8.4 of the plan. {@code FOR UPDATE SKIP LOCKED}: concurrent claims (two instances, or
   * overlapping runs) never get the same row. An event whose lease expired (worker died
   * mid-delivery) is claimable again. {@code NOT EXISTS} keeps the order of each sale: an event is
   * only eligible once every earlier event of the same order is {@code SENT}.
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
        WHERE ((c.status = 'PENDING' AND c.next_attempt_at <= :now)
            OR (c.status = 'PROCESSING' AND c.locked_until < :now))
          AND NOT EXISTS (
            SELECT 1 FROM outbox_events p
            WHERE p.aggregate_id = c.aggregate_id
              AND p.sequence < c.sequence
              AND p.status <> 'SENT')
        ORDER BY c.sequence
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED)
      RETURNING e.id, e.sequence, e.aggregate_id, e.event_type, e.payload::text AS payload,
                e.attempts, e.attempts_at_retry
      """;

  private static final String OWNED =
      " WHERE id = :id AND status = 'PROCESSING' AND attempts = :attempts";

  private static final String MARK_SENT =
      """
      UPDATE outbox_events
      SET status = 'SENT',
          failure_kind = NULL,
          locked_until = NULL,
          processed_at = :now,
          updated_at = :now,
          last_http_status = :httpStatus,
          last_request_id = :requestId,
          last_error_code = NULL,
          last_error = NULL,
          remote_id = :remoteId
      """
          + OWNED;

  private static final String MARK_FAILED =
      """
      UPDATE outbox_events
      SET status = 'FAILED',
          failure_kind = :failureKind,
          locked_until = NULL,
          updated_at = :now,
          last_http_status = :httpStatus,
          last_request_id = :requestId,
          last_error_code = :errorCode,
          last_error = :error
      """
          + OWNED;

  private static final String RESCHEDULE =
      """
      UPDATE outbox_events
      SET status = 'PENDING',
          next_attempt_at = :nextAttemptAt,
          locked_until = NULL,
          updated_at = :now,
          last_http_status = :httpStatus,
          last_request_id = :requestId,
          last_error_code = :errorCode,
          last_error = :error
      """
          + OWNED;

  /** Gives back a claim that was never attempted: the attempt does not count. */
  private static final String RELEASE =
      """
      UPDATE outbox_events
      SET status = 'PENDING',
          attempts = attempts - 1,
          next_attempt_at = :now,
          locked_until = NULL,
          updated_at = :now
      """
          + OWNED;

  /**
   * Manual reprocessing. {@code attempts} keeps the whole history; {@code attempts_at_retry} marks
   * where the attempt limit and the backoff start counting again.
   */
  private static final String RETRY =
      """
      UPDATE outbox_events
      SET status = 'PENDING',
          failure_kind = NULL,
          next_attempt_at = :now,
          attempts_at_retry = attempts,
          updated_at = :now
      WHERE status = 'FAILED'
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
                    rs.getInt("attempts"),
                    rs.getInt("attempts_at_retry")))
        .list()
        .stream()
        .sorted(Comparator.comparingLong(ClaimedEvent::sequence))
        .toList();
  }

  /** Returns false when the claim was lost (lease expired and re-claimed): nothing is recorded. */
  @Transactional
  public boolean markSent(
      ClaimedEvent event, Instant now, int httpStatus, String requestId, UUID remoteId) {
    return owned(MARK_SENT, event, now)
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
      FailureKind kind,
      Integer httpStatus,
      String requestId,
      String errorCode,
      String error) {
    return owned(MARK_FAILED, event, now)
            .param("failureKind", kind.name())
            .param("httpStatus", httpStatus)
            .param("requestId", requestId)
            .param("errorCode", errorCode)
            .param("error", error)
            .update()
        == 1;
  }

  /** A transient failure: back to {@code PENDING}, claimable again at {@code nextAttemptAt}. */
  @Transactional
  public boolean reschedule(
      ClaimedEvent event,
      Instant now,
      Instant nextAttemptAt,
      Integer httpStatus,
      String requestId,
      String errorCode,
      String error) {
    return owned(RESCHEDULE, event, now)
            .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .param("httpStatus", httpStatus)
            .param("requestId", requestId)
            .param("errorCode", errorCode)
            .param("error", error)
            .update()
        == 1;
  }

  @Transactional
  public boolean release(ClaimedEvent event, Instant now) {
    return owned(RELEASE, event, now).update() == 1;
  }

  /** {@code FAILED → PENDING}; false when the event does not exist or is not {@code FAILED}. */
  @Transactional
  public boolean retry(UUID id, Instant now) {
    return jdbc.sql(RETRY + " AND id = :id")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
        == 1;
  }

  /** Every {@code FAILED/CONFIGURATION} event back to {@code PENDING}; returns how many. */
  @Transactional
  public int retryConfigurationFailures(Instant now) {
    return jdbc.sql(RETRY + " AND failure_kind = 'CONFIGURATION'")
        .param("now", Timestamp.from(now))
        .update();
  }

  private JdbcClient.StatementSpec owned(String sql, ClaimedEvent event, Instant now) {
    return jdbc.sql(sql)
        .param("id", event.id())
        .param("attempts", event.attempts())
        .param("now", Timestamp.from(now));
  }
}

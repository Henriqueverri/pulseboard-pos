package dev.henriqueverri.pos.integration.outbox;

import dev.henriqueverri.pos.integration.pulseboard.DeliveryResult;
import dev.henriqueverri.pos.integration.pulseboard.IngestPayloadFactory;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import dev.henriqueverri.pos.shared.logging.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers outbox events to PulseBoard: claim a batch (short transaction), send each event over
 * HTTP (no transaction), record the classified outcome (another short transaction). Events of a
 * batch are sent one at a time in {@code sequence} order.
 *
 * <p>Outcomes: success is {@code SENT}; a transient failure goes back to {@code PENDING} with
 * backoff until the attempt limit ({@code FAILED/EXHAUSTED}); a rejection is {@code
 * FAILED/PERMANENT}; a 401 is {@code FAILED/CONFIGURATION} and pauses the integration; a refund
 * PulseBoard calls an invalid transition is reconciled against the remote state.
 *
 * <p>Logging: while an event is handled, the MDC holds {@code event_id} and {@code request_id}
 * ({@code pos-<event id>}, the {@code X-Request-Id} sent to PulseBoard), so every line of one
 * attempt — claim, HTTP call, outcome, persistence — can be found by either. Lines carry fixed
 * key-value fields only; payloads, customer data and PulseBoard's error messages are never logged.
 * An empty poll logs nothing.
 */
@Component
public class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

  private final OutboxStore store;
  private final PulseBoardClient client;
  private final IngestResponseClassifier classifier;
  private final RetryPolicy retryPolicy;
  private final IntegrationPause pause;
  private final PulseBoardProperties pulseBoard;
  private final OutboxProperties settings;
  private final Clock clock;

  public OutboxWorker(
      OutboxStore store,
      PulseBoardClient client,
      IngestResponseClassifier classifier,
      RetryPolicy retryPolicy,
      IntegrationPause pause,
      PulseBoardProperties pulseBoard,
      OutboxProperties settings,
      Clock clock) {
    this.store = store;
    this.client = client;
    this.classifier = classifier;
    this.retryPolicy = retryPolicy;
    this.pause = pause;
    this.pulseBoard = pulseBoard;
    this.settings = settings;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${pos.outbox.poll-interval:2s}")
  public void poll() {
    runOnce();
  }

  /** One claim → send → record cycle. Returns how many events were claimed. */
  public int runOnce() {
    if (!pulseBoard.isActive()) {
      return 0;
    }
    Instant now = clock.instant();
    if (pause.isPaused(now)) {
      log.debug("PulseBoard integration paused; nothing claimed");
      return 0;
    }
    List<ClaimedEvent> batch = store.claim(now, now.plus(settings.lease()), settings.batchSize());
    for (ClaimedEvent event : batch) {
      try (MDC.MDCCloseable requestId =
              MDC.putCloseable(RequestIdFilter.MDC_KEY, event.requestId());
          MDC.MDCCloseable eventId = MDC.putCloseable("event_id", event.id().toString())) {
        handle(event);
      }
    }
    return batch.size();
  }

  private void handle(ClaimedEvent event) {
    if (pause.isPaused(clock.instant())) {
      // A 401 earlier in this batch: the rest would fail the same way. Not an attempt.
      boolean released = store.release(event, clock.instant());
      fields(log.atInfo(), event)
          .addKeyValue("outcome", "released")
          .addKeyValue("recorded", released)
          .log("Outbox event released: integration paused");
      return;
    }
    fields(log.atInfo(), event)
        .addKeyValue("outcome", "claimed")
        .log("Outbox event claimed; delivering to PulseBoard");
    try {
      deliver(event);
    } catch (RuntimeException e) {
      // The event keeps its lease and becomes claimable again when it expires.
      fields(log.atError(), event)
          .addKeyValue("outcome", "error")
          .addKeyValue("error_type", e.getClass().getSimpleName())
          .setCause(e)
          .log("Unexpected error delivering outbox event");
    }
  }

  private void deliver(ClaimedEvent event) {
    DeliveryResult result =
        switch (event.type()) {
          case ORDER_PAID -> client.createTransaction(event.payload(), event.requestId());
          case ORDER_REFUNDED ->
              client.changeStatus(externalId(event), event.payload(), event.requestId());
        };
    DeliveryOutcome outcome = classifier.classify(result, event.type(), clock.instant());
    if (outcome instanceof DeliveryOutcome.Reconcile conflict) {
      outcome = reconcile(event, conflict);
    }
    if (!record(event, outcome, clock.instant())) {
      fields(log.atWarn(), event)
          .addKeyValue("outcome", "discarded")
          .log("Outbox event was re-claimed by another worker; result discarded");
    }
  }

  /**
   * PulseBoard refused {@code paid → refunded}. If the transaction is already {@code refunded}
   * (e.g. the first refund was recorded but its response was lost), the event is done; otherwise
   * the states diverge and a person has to look.
   */
  private DeliveryOutcome reconcile(ClaimedEvent event, DeliveryOutcome.Reconcile conflict) {
    fields(log.atInfo(), event)
        .addKeyValue("outcome", "reconciling")
        .addKeyValue("http_status", conflict.httpStatus())
        .addKeyValue("error_code", conflict.code())
        .log("Refund refused as an invalid transition; checking the transaction in PulseBoard");
    DeliveryResult lookup = client.getTransaction(externalId(event), event.requestId());
    DeliveryOutcome current = classifier.classify(lookup, event.type(), clock.instant());
    return switch (current) {
      case DeliveryOutcome.Sent found when "refunded".equals(found.remoteStatus()) -> {
        fields(log.atInfo(), event)
            .addKeyValue("outcome", "reconciled")
            .log("Outbox event reconciled: transaction already refunded");
        yield new DeliveryOutcome.Sent(
            conflict.httpStatus(), conflict.requestId(), false, found.remoteId(), "refunded");
      }
      case DeliveryOutcome.Sent found ->
          diverged(conflict, "status no PulseBoard: " + found.remoteStatus());
      case DeliveryOutcome.Reconcile unexpected -> diverged(conflict, "consulta inconclusiva");
      // The lookup itself failed: retry later, or stop on a bad key / unknown transaction.
      case DeliveryOutcome.Transient failure -> failure;
      case DeliveryOutcome.Failed failure -> failure;
    };
  }

  private static DeliveryOutcome diverged(DeliveryOutcome.Reconcile conflict, String detail) {
    return new DeliveryOutcome.Failed(
        FailureKind.PERMANENT,
        conflict.httpStatus(),
        conflict.requestId(),
        conflict.code(),
        conflict.message() + " | " + detail);
  }

  private boolean record(ClaimedEvent event, DeliveryOutcome outcome, Instant now) {
    return switch (outcome) {
      case DeliveryOutcome.Sent sent -> {
        boolean recorded =
            store.markSent(event, now, sent.httpStatus(), sent.requestId(), sent.remoteId());
        fields(log.atInfo(), event)
            .addKeyValue("outcome", "sent")
            .addKeyValue("http_status", sent.httpStatus())
            .addKeyValue("replayed", sent.replayed())
            .addKeyValue("recorded", recorded)
            .log("Outbox event sent to PulseBoard");
        yield recorded;
      }
      case DeliveryOutcome.Transient failure
          when retryPolicy.exhausted(event.attemptsSinceRetry()) -> {
        boolean recorded =
            store.markFailed(
                event,
                now,
                FailureKind.EXHAUSTED,
                failure.httpStatus(),
                failure.requestId(),
                failure.code(),
                failure.message());
        failureFields(log.atWarn(), event, failure.httpStatus(), failure.code(), recorded)
            .addKeyValue("outcome", "failed")
            .addKeyValue("failure_kind", FailureKind.EXHAUSTED)
            .log("Outbox event exhausted its attempts");
        warnIfBlocking(event, recorded);
        yield recorded;
      }
      case DeliveryOutcome.Transient failure -> {
        Instant next =
            retryPolicy.nextAttemptAt(now, event.attemptsSinceRetry(), failure.retryAfter());
        boolean recorded =
            store.reschedule(
                event,
                now,
                next,
                failure.httpStatus(),
                failure.requestId(),
                failure.code(),
                failure.message());
        failureFields(log.atWarn(), event, failure.httpStatus(), failure.code(), recorded)
            .addKeyValue("outcome", "retry_scheduled")
            .addKeyValue("next_attempt_at", next)
            .log("Outbox event failed transiently; retry scheduled");
        yield recorded;
      }
      case DeliveryOutcome.Failed failure -> {
        boolean recorded =
            store.markFailed(
                event,
                now,
                failure.kind(),
                failure.httpStatus(),
                failure.requestId(),
                failure.code(),
                failure.message());
        failureFields(log.atWarn(), event, failure.httpStatus(), failure.code(), recorded)
            .addKeyValue("outcome", "failed")
            .addKeyValue("failure_kind", failure.kind())
            .log("Outbox event failed permanently");
        if (failure.kind() == FailureKind.CONFIGURATION) {
          Instant until = pause.pause(now);
          log.atError()
              .addKeyValue("outcome", "integration_paused")
              .addKeyValue("http_status", failure.httpStatus())
              .addKeyValue("error_code", failure.code())
              .addKeyValue("paused_until", until)
              .log("PulseBoard rejected the API key: integration paused");
        }
        warnIfBlocking(event, recorded);
        yield recorded;
      }
      case DeliveryOutcome.Reconcile conflict ->
          throw new IllegalStateException("Reconciliation not resolved for " + event.id());
    };
  }

  /** A failed event holds back the later events of its order until it is reprocessed. */
  private void warnIfBlocking(ClaimedEvent event, boolean recorded) {
    if (!recorded) {
      return;
    }
    long blocked = store.countWaitingBehind(event);
    if (blocked > 0) {
      fields(log.atWarn(), event)
          .addKeyValue("outcome", "blocking")
          .addKeyValue("blocked_events", blocked)
          .log("Later events of the order are blocked until this event is reprocessed");
    }
  }

  private static LoggingEventBuilder failureFields(
      LoggingEventBuilder builder,
      ClaimedEvent event,
      Integer httpStatus,
      String errorCode,
      boolean recorded) {
    return fields(builder, event)
        .addKeyValue("http_status", httpStatus)
        .addKeyValue("error_code", errorCode)
        .addKeyValue("recorded", recorded);
  }

  /** The identity of the attempt; {@code event_id} and {@code request_id} are in the MDC. */
  private static LoggingEventBuilder fields(LoggingEventBuilder builder, ClaimedEvent event) {
    return builder
        .addKeyValue("order_id", event.aggregateId())
        .addKeyValue("external_id", externalId(event))
        .addKeyValue("event_type", event.type())
        .addKeyValue("attempt", event.attempts());
  }

  private static String externalId(ClaimedEvent event) {
    return IngestPayloadFactory.orderExternalId(event.aggregateId());
  }
}

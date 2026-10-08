package dev.henriqueverri.pos.integration.outbox;

import dev.henriqueverri.pos.integration.pulseboard.DeliveryResult;
import dev.henriqueverri.pos.integration.pulseboard.IngestPayloadFactory;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
      return 0;
    }
    List<ClaimedEvent> batch = store.claim(now, now.plus(settings.lease()), settings.batchSize());
    for (ClaimedEvent event : batch) {
      if (pause.isPaused(clock.instant())) {
        // A 401 earlier in this batch: the rest would fail the same way. Not an attempt.
        store.release(event, clock.instant());
        continue;
      }
      try {
        deliver(event);
      } catch (RuntimeException e) {
        // The event keeps its lease and becomes claimable again when it expires.
        log.error("Unexpected error delivering outbox event {}", event.id(), e);
      }
    }
    return batch.size();
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
      log.warn("Outbox event {} was re-claimed by another worker; result discarded", event.id());
    }
  }

  /**
   * PulseBoard refused {@code paid → refunded}. If the transaction is already {@code refunded}
   * (e.g. the first refund was recorded but its response was lost), the event is done; otherwise
   * the states diverge and a person has to look.
   */
  private DeliveryOutcome reconcile(ClaimedEvent event, DeliveryOutcome.Reconcile conflict) {
    DeliveryResult lookup = client.getTransaction(externalId(event), event.requestId());
    DeliveryOutcome current = classifier.classify(lookup, event.type(), clock.instant());
    return switch (current) {
      case DeliveryOutcome.Sent found when "refunded".equals(found.remoteStatus()) -> {
        log.info("Outbox event {} reconciled: transaction already refunded", event.id());
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
        log.info(
            "Outbox event {} ({}) sent: HTTP {}{}",
            event.id(),
            event.type(),
            sent.httpStatus(),
            sent.replayed() ? " (idempotent replay)" : "");
        yield store.markSent(event, now, sent.httpStatus(), sent.requestId(), sent.remoteId());
      }
      case DeliveryOutcome.Transient failure
          when retryPolicy.exhausted(event.attemptsSinceRetry()) -> {
        log.warn(
            "Outbox event {} ({}) exhausted after {} attempts: {}",
            event.id(),
            event.type(),
            event.attemptsSinceRetry(),
            failure.code());
        yield store.markFailed(
            event,
            now,
            FailureKind.EXHAUSTED,
            failure.httpStatus(),
            failure.requestId(),
            failure.code(),
            failure.message());
      }
      case DeliveryOutcome.Transient failure -> {
        Instant next =
            retryPolicy.nextAttemptAt(now, event.attemptsSinceRetry(), failure.retryAfter());
        log.warn(
            "Outbox event {} ({}) failed transiently ({}), next attempt at {}",
            event.id(),
            event.type(),
            failure.code(),
            next);
        yield store.reschedule(
            event,
            now,
            next,
            failure.httpStatus(),
            failure.requestId(),
            failure.code(),
            failure.message());
      }
      case DeliveryOutcome.Failed failure -> {
        if (failure.kind() == FailureKind.CONFIGURATION) {
          Instant until = pause.pause(now);
          log.error(
              "PulseBoard rejected the API key (HTTP {}): integration paused until {}",
              failure.httpStatus(),
              until);
        } else {
          log.warn(
              "Outbox event {} ({}) failed permanently: HTTP {} {}",
              event.id(),
              event.type(),
              failure.httpStatus(),
              failure.code());
        }
        yield store.markFailed(
            event,
            now,
            failure.kind(),
            failure.httpStatus(),
            failure.requestId(),
            failure.code(),
            failure.message());
      }
      case DeliveryOutcome.Reconcile conflict ->
          throw new IllegalStateException("Reconciliation not resolved for " + event.id());
    };
  }

  private static String externalId(ClaimedEvent event) {
    return IngestPayloadFactory.orderExternalId(event.aggregateId());
  }
}

package dev.henriqueverri.pos.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henriqueverri.pos.integration.pulseboard.DeliveryResult;
import dev.henriqueverri.pos.integration.pulseboard.IngestPayloadFactory;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers outbox events to PulseBoard: claim a batch (short transaction), send each event over
 * HTTP (no transaction), record the outcome (another short transaction). Events of a batch are sent
 * one at a time in {@code sequence} order.
 *
 * <p>Outcome rules of this phase: 200/201 is {@code SENT}; anything else is {@code FAILED} with the
 * HTTP status, PulseBoard's {@code code} and message kept for diagnosis.
 */
@Component
public class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_ERROR_LENGTH = 500;

  private final OutboxStore store;
  private final PulseBoardClient client;
  private final PulseBoardProperties pulseBoard;
  private final OutboxProperties settings;
  private final Clock clock;

  public OutboxWorker(
      OutboxStore store,
      PulseBoardClient client,
      PulseBoardProperties pulseBoard,
      OutboxProperties settings,
      Clock clock) {
    this.store = store;
    this.client = client;
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
    List<ClaimedEvent> batch = store.claim(now, now.plus(settings.lease()), settings.batchSize());
    for (ClaimedEvent event : batch) {
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
              client.changeStatus(
                  IngestPayloadFactory.orderExternalId(event.aggregateId()),
                  event.payload(),
                  event.requestId());
        };
    Instant now = clock.instant();
    boolean recorded =
        switch (result) {
          case DeliveryResult.Response response when isSuccess(response) -> {
            log.info(
                "Outbox event {} ({}) sent: HTTP {}{}",
                event.id(),
                event.type(),
                response.status(),
                response.replayed() ? " (idempotent replay)" : "");
            yield store.markSent(
                event, now, response.status(), response.requestId(), remoteId(response.body()));
          }
          case DeliveryResult.Response response -> {
            ErrorBody error = errorBody(response);
            log.warn(
                "Outbox event {} ({}) rejected: HTTP {} {}",
                event.id(),
                event.type(),
                response.status(),
                error.code());
            yield store.markFailed(
                event, now, response.status(), response.requestId(), error.code(), error.message());
          }
          case DeliveryResult.NoResponse noResponse -> {
            log.warn(
                "Outbox event {} ({}) failed: {}", event.id(), event.type(), noResponse.code());
            yield store.markFailed(event, now, null, null, noResponse.code(), noResponse.message());
          }
        };
    if (!recorded) {
      log.warn("Outbox event {} was re-claimed by another worker; result discarded", event.id());
    }
  }

  private static boolean isSuccess(DeliveryResult.Response response) {
    return response.status() == 200 || response.status() == 201;
  }

  private record ErrorBody(String code, String message) {}

  private static ErrorBody errorBody(DeliveryResult.Response response) {
    JsonNode body = parse(response.body());
    String code = text(body, "code");
    String message = text(body, "message");
    JsonNode errors = body == null ? null : body.get("errors");
    if (errors != null && errors.isObject() && !errors.isEmpty()) {
      message = (message == null ? "" : message + " ") + errors;
    }
    return new ErrorBody(
        code != null ? code : "HTTP_" + response.status(),
        truncate(message != null ? message : "HTTP " + response.status()));
  }

  private static UUID remoteId(String body) {
    JsonNode root = parse(body);
    String id = root == null ? null : text(root.path("data"), "id");
    try {
      return id == null ? null : UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static JsonNode parse(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return JSON.readTree(body);
    } catch (IOException e) {
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static String truncate(String text) {
    return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
  }
}

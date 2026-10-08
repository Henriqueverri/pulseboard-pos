package dev.henriqueverri.pos.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henriqueverri.pos.integration.pulseboard.DeliveryResult;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps a PulseBoard result to what happens to the event, following the error table of PulseBoard's
 * {@code docs/integration.md} (section 8.5 of the plan). Error bodies are {@code {message, code,
 * errors}}; messages are kept short and never contain the request headers.
 */
@Component
public class IngestResponseClassifier {

  static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(60);
  static final int MAX_MESSAGE_LENGTH = 500;

  private static final ObjectMapper JSON = new ObjectMapper();

  public DeliveryOutcome classify(DeliveryResult result, OutboxEventType type, Instant now) {
    return switch (result) {
      case DeliveryResult.NoResponse none ->
          new DeliveryOutcome.Transient(null, null, none.code(), none.message(), null);
      case DeliveryResult.Response response -> classify(response, type, now);
    };
  }

  private DeliveryOutcome classify(
      DeliveryResult.Response response, OutboxEventType type, Instant now) {
    int status = response.status();
    String requestId = response.requestId();
    JsonNode body = parse(response.body());

    if (status == 200 || status == 201) {
      JsonNode data = body == null ? null : body.path("data");
      return new DeliveryOutcome.Sent(
          status, requestId, response.replayed(), uuid(text(data, "id")), text(data, "status"));
    }

    String code = text(body, "code");
    String message = summary(body, status);
    if (status == 429) {
      Duration wait = retryAfter(response.retryAfter(), now);
      return new DeliveryOutcome.Transient(
          status,
          requestId,
          code != null ? code : "rate_limited",
          message,
          wait != null ? wait : DEFAULT_RETRY_AFTER);
    }
    if (status >= 500) {
      return new DeliveryOutcome.Transient(status, requestId, "HTTP_5XX", message, null);
    }
    if (status == 401) {
      return new DeliveryOutcome.Failed(
          FailureKind.CONFIGURATION,
          status,
          requestId,
          "invalid_api_key",
          "API Key inválida, revogada ou expirada. Corrija PULSEBOARD_API_KEY, reinicie a API e"
              + " reprocesse as falhas de configuração.");
    }
    if (status == 409
        && "invalid_transition".equals(code)
        && type == OutboxEventType.ORDER_REFUNDED) {
      return new DeliveryOutcome.Reconcile(status, requestId, code, message);
    }
    if (status == 409 && "customer_email_conflict".equals(code)) {
      message =
          truncate(
              message + " Vincule o cliente pelo ID externo no PulseBoard e reprocesse o evento.");
    }
    // 422, 413, 409, 404 and any other answer: the same payload would be rejected again.
    return new DeliveryOutcome.Failed(
        FailureKind.PERMANENT, status, requestId, code != null ? code : "HTTP_" + status, message);
  }

  /** {@code Retry-After} is either delta-seconds or an HTTP-date (RFC 9110). */
  static Duration retryAfter(String header, Instant now) {
    if (header == null || header.isBlank()) {
      return null;
    }
    String value = header.trim();
    try {
      long seconds = Long.parseLong(value);
      return seconds < 0 ? null : Duration.ofSeconds(seconds);
    } catch (NumberFormatException notSeconds) {
      try {
        Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        return at.isAfter(now) ? Duration.between(now, at) : Duration.ZERO;
      } catch (DateTimeParseException notADate) {
        return null;
      }
    }
  }

  /** PulseBoard's {@code message} plus each field error ({@code items.1.sku: ...}). */
  private static String summary(JsonNode body, int status) {
    String message = text(body, "message");
    List<String> parts = new ArrayList<>();
    parts.add(message != null ? message : "HTTP " + status);
    JsonNode errors = body == null ? null : body.get("errors");
    if (errors != null && errors.isObject()) {
      for (Map.Entry<String, JsonNode> field : errors.properties()) {
        List<String> messages = new ArrayList<>();
        field.getValue().forEach(item -> messages.add(item.asText()));
        if (field.getValue().isTextual()) {
          messages.add(field.getValue().asText());
        }
        parts.add(field.getKey() + ": " + String.join(" ", messages));
      }
    }
    return truncate(String.join(" | ", parts));
  }

  private static JsonNode parse(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      JsonNode node = JSON.readTree(body);
      return node.isObject() ? node : null;
    } catch (IOException notJson) {
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static UUID uuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException notAUuid) {
      return null;
    }
  }

  private static String truncate(String text) {
    return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH);
  }
}

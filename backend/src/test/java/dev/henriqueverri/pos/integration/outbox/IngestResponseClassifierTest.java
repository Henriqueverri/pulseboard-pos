package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.henriqueverri.pos.integration.outbox.DeliveryOutcome.Failed;
import dev.henriqueverri.pos.integration.outbox.DeliveryOutcome.Reconcile;
import dev.henriqueverri.pos.integration.outbox.DeliveryOutcome.Sent;
import dev.henriqueverri.pos.integration.outbox.DeliveryOutcome.Transient;
import dev.henriqueverri.pos.integration.pulseboard.DeliveryResult;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Every row of the classification table (section 8.5 of the plan). */
class IngestResponseClassifierTest {

  private static final Instant NOW = Instant.parse("2026-10-06T21:00:00Z");
  private static final UUID REMOTE_ID = UUID.fromString("01a1099b-0000-7000-8000-000000000001");

  private final IngestResponseClassifier classifier = new IngestResponseClassifier();

  @Test
  void createdIsSent() {
    DeliveryOutcome outcome =
        classifyPaid(
            response(
                201, "{\"data\":{\"id\":\"" + REMOTE_ID + "\",\"status\":\"paid\"}}", false, null));

    assertThat(outcome).isEqualTo(new Sent(201, "pb-req", false, REMOTE_ID, "paid"));
  }

  @Test
  void idempotentReplayIsSent() {
    DeliveryOutcome outcome =
        classifyPaid(
            response(
                200, "{\"data\":{\"id\":\"" + REMOTE_ID + "\",\"status\":\"paid\"}}", true, null));

    assertThat(outcome).isEqualTo(new Sent(200, "pb-req", true, REMOTE_ID, "paid"));
  }

  @Test
  void successWithoutAReadableBodyIsStillSent() {
    assertThat(classifyPaid(response(201, "not json", false, null)))
        .isEqualTo(new Sent(201, "pb-req", false, null, null));
  }

  @Test
  void rateLimitedWaitsForRetryAfter() {
    DeliveryOutcome outcome =
        classifyPaid(
            response(
                429,
                "{\"message\":\"Too Many Attempts.\",\"code\":\"rate_limited\"}",
                false,
                "120"));

    assertThat(outcome)
        .isEqualTo(
            new Transient(
                429, "pb-req", "rate_limited", "Too Many Attempts.", Duration.ofSeconds(120)));
  }

  @Test
  void rateLimitedAcceptsAnHttpDate() {
    DeliveryOutcome outcome =
        classifyPaid(response(429, "", false, "Tue, 06 Oct 2026 21:01:30 GMT"));

    assertThat(((Transient) outcome).retryAfter()).isEqualTo(Duration.ofSeconds(90));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "soon", "-5"})
  void rateLimitedWithoutAUsableRetryAfterWaitsSixtySeconds(String header) {
    Transient outcome = (Transient) classifyPaid(response(429, "", false, header));

    assertThat(outcome.retryAfter()).isEqualTo(Duration.ofSeconds(60));
    assertThat(outcome.code()).isEqualTo("rate_limited");
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 504})
  void serverErrorsAreTransientWithBackoff(int status) {
    Transient outcome =
        (Transient) classifyPaid(response(status, "{\"message\":\"Server Error\"}", false, "30"));

    assertThat(outcome)
        .isEqualTo(new Transient(status, "pb-req", "HTTP_5XX", "Server Error", null));
  }

  @Test
  void aNonJsonErrorPageKeepsOnlyTheStatus() {
    Transient outcome =
        (Transient) classifyPaid(response(503, "<html>Service Unavailable</html>", false, null));

    assertThat(outcome.message()).isEqualTo("HTTP 503");
  }

  @ParameterizedTest
  @CsvSource({"TIMEOUT, O PulseBoard não respondeu.", "NETWORK, Conexão recusada."})
  void noResponseIsTransientWithBackoff(String code, String message) {
    DeliveryOutcome outcome =
        classifier.classify(
            new DeliveryResult.NoResponse(code, message), OutboxEventType.ORDER_PAID, NOW);

    assertThat(outcome).isEqualTo(new Transient(null, null, code, message, null));
  }

  @Test
  void validationFailureIsPermanentWithTheFieldErrors() {
    Failed outcome =
        (Failed)
            classifyPaid(
                response(
                    422,
                    "{\"message\":\"The selected product SKU is invalid.\","
                        + "\"code\":\"validation_failed\","
                        + "\"errors\":{\"items.1.sku\":[\"The selected product SKU is invalid.\"]}}",
                    false,
                    null));

    assertThat(outcome)
        .isEqualTo(
            new Failed(
                FailureKind.PERMANENT,
                422,
                "pb-req",
                "validation_failed",
                "The selected product SKU is invalid. | items.1.sku: The selected product SKU is"
                    + " invalid."));
  }

  @ParameterizedTest
  @CsvSource({"422, currency_mismatch", "422, total_mismatch", "409, transaction_conflict"})
  void contentRejectionsArePermanent(int status, String code) {
    Failed outcome =
        (Failed)
            classifyPaid(
                response(
                    status, "{\"message\":\"Rejected.\",\"code\":\"" + code + "\"}", false, null));

    assertThat(outcome.kind()).isEqualTo(FailureKind.PERMANENT);
    assertThat(outcome.code()).isEqualTo(code);
    assertThat(outcome.httpStatus()).isEqualTo(status);
  }

  @Test
  void payloadTooLargeIsPermanent() {
    Failed outcome = (Failed) classifyPaid(response(413, "", false, null));

    assertThat(outcome)
        .isEqualTo(new Failed(FailureKind.PERMANENT, 413, "pb-req", "HTTP_413", "HTTP 413"));
  }

  @Test
  void customerEmailConflictTellsWhatToDo() {
    Failed outcome =
        (Failed)
            classifyPaid(
                response(
                    409,
                    "{\"message\":\"Another customer already uses this email.\","
                        + "\"code\":\"customer_email_conflict\"}",
                    false,
                    null));

    assertThat(outcome.kind()).isEqualTo(FailureKind.PERMANENT);
    assertThat(outcome.code()).isEqualTo("customer_email_conflict");
    assertThat(outcome.message())
        .startsWith("Another customer already uses this email.")
        .contains("Vincule o cliente pelo ID externo no PulseBoard e reprocesse");
  }

  @Test
  void invalidTransitionOfARefundIsReconciled() {
    DeliveryOutcome outcome =
        classifier.classify(
            response(
                409,
                "{\"message\":\"Cannot change status.\",\"code\":\"invalid_transition\"}",
                false,
                null),
            OutboxEventType.ORDER_REFUNDED,
            NOW);

    assertThat(outcome)
        .isEqualTo(new Reconcile(409, "pb-req", "invalid_transition", "Cannot change status."));
  }

  @Test
  void invalidTransitionOfASaleIsPermanent() {
    Failed outcome =
        (Failed)
            classifyPaid(
                response(409, "{\"message\":\"x\",\"code\":\"invalid_transition\"}", false, null));

    assertThat(outcome.kind()).isEqualTo(FailureKind.PERMANENT);
  }

  @Test
  void unknownTransactionOfARefundIsPermanent() {
    Failed outcome =
        (Failed)
            classifier.classify(
                response(404, "{\"message\":\"Not found.\",\"code\":\"not_found\"}", false, null),
                OutboxEventType.ORDER_REFUNDED,
                NOW);

    assertThat(outcome)
        .isEqualTo(new Failed(FailureKind.PERMANENT, 404, "pb-req", "not_found", "Not found."));
  }

  @Test
  void invalidApiKeyIsAConfigurationFailure() {
    Failed outcome =
        (Failed)
            classifyPaid(
                response(
                    401,
                    "{\"message\":\"Unauthenticated.\",\"code\":\"invalid_api_key\"}",
                    false,
                    null));

    assertThat(outcome.kind()).isEqualTo(FailureKind.CONFIGURATION);
    assertThat(outcome.code()).isEqualTo("invalid_api_key");
    assertThat(outcome.httpStatus()).isEqualTo(401);
    assertThat(outcome.message()).contains("API Key inválida, revogada ou expirada");
  }

  @Test
  void otherClientErrorsArePermanent() {
    Failed outcome = (Failed) classifyPaid(response(400, "{\"message\":\"Bad.\"}", false, null));

    assertThat(outcome)
        .isEqualTo(new Failed(FailureKind.PERMANENT, 400, "pb-req", "HTTP_400", "Bad."));
  }

  @Test
  void longMessagesAreTruncated() {
    String message = "x".repeat(2_000);
    Failed outcome =
        (Failed)
            classifyPaid(
                response(
                    422,
                    "{\"message\":\"" + message + "\",\"code\":\"validation_failed\"}",
                    false,
                    null));

    assertThat(outcome.message()).hasSize(IngestResponseClassifier.MAX_MESSAGE_LENGTH);
  }

  private DeliveryOutcome classifyPaid(DeliveryResult result) {
    return classifier.classify(result, OutboxEventType.ORDER_PAID, NOW);
  }

  private static DeliveryResult.Response response(
      int status, String body, boolean replayed, String retryAfter) {
    return new DeliveryResult.Response(status, body, "pb-req", replayed, retryAfter);
  }
}

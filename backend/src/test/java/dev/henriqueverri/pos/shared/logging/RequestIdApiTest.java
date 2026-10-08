package dev.henriqueverri.pos.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.henriqueverri.pos.integration.outbox.OutboxService;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import dev.henriqueverri.pos.support.CapturedLogs;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/** The request id through the whole stack: filter order, security, MDC and response. */
class RequestIdApiTest extends ApiIntegrationTest {

  @Test
  void returnsTheReceivedRequestId() throws Exception {
    mvc.perform(authenticate(get("/api/products")).header("X-Request-Id", "pos-web-0000000001"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "pos-web-0000000001"));
  }

  @Test
  void generatesARequestIdWhenAbsent() throws Exception {
    String generated =
        getJson("/api/products")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("X-Request-Id");

    assertThat(UUID.fromString(generated)).isNotNull();
  }

  /** The filter runs before Spring Security: rejected requests can be correlated too. */
  @Test
  void unauthenticatedResponsesCarryTheRequestId() throws Exception {
    mvc.perform(get("/api/orders").header("X-Request-Id", "anonymous-call-01"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Request-Id", "anonymous-call-01"));
  }

  /**
   * Logs written while handling the request carry its id — here, the event enqueued by a payment,
   * which links the HTTP request to the event's deliveries — and the MDC is empty afterwards.
   */
  @Test
  void logsOfTheRequestCarryItsIdAndTheMdcIsClearedAfterwards() throws Exception {
    UUID orderId = UUID.fromString(createPendingOrder().get("id").asText());

    try (CapturedLogs logs = CapturedLogs.start()) {
      mvc.perform(
              authenticate(post("/api/orders/" + orderId + "/pay"))
                  .header("X-Request-Id", "checkout-pay-0001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"paymentMethod\":\"PIX\"}"))
          .andExpect(status().isOk())
          .andExpect(header().string("X-Request-Id", "checkout-pay-0001"));

      List<ILoggingEvent> enqueued = logs.from(OutboxService.class);
      assertThat(enqueued).hasSize(1);
      assertThat(enqueued.get(0).getMDCPropertyMap())
          .containsEntry("request_id", "checkout-pay-0001");
      assertThat(CapturedLogs.fields(enqueued.get(0)))
          .containsEntry("order_id", orderId)
          .containsEntry("external_id", "pos:" + orderId)
          .containsEntry("outcome", "enqueued")
          .containsKey("event_id");
    }
    assertThat(MDC.get("request_id")).isNull();
  }
}

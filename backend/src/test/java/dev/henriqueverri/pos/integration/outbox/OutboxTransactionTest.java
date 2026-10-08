package dev.henriqueverri.pos.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** The order change and its outbox event are one unit: both are committed or neither is. */
class OutboxTransactionTest extends ApiIntegrationTest {

  @Autowired private OutboxRepository events;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void payingWritesTheOrderPaidSnapshotInTheSameTransaction() throws Exception {
    JsonNode order = createPendingOrder();
    UUID orderId = UUID.fromString(order.get("id").asText());

    JsonNode paid = json(pay(orderId).andExpect(status().isOk()));

    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(orderId);
    assertThat(written).hasSize(1);
    OutboxEvent event = written.get(0);
    assertThat(event.getEventType()).isEqualTo(OutboxEventType.ORDER_PAID);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(event.getAggregateType()).isEqualTo("ORDER");
    assertThat(event.getAttempts()).isZero();
    assertThat(event.getSequence()).isNotNull();

    JsonNode customer = paid.get("customer");
    JsonNode item = paid.get("items").get(0);
    assertThat(objectMapper.readTree(event.getPayload()))
        .isEqualTo(
            objectMapper.readTree(
                """
                {"external_id": "pos:%s", "status": "paid", "occurred_at": "%s", "currency": "BRL",
                 "customer": {"external_id": "pos:cus:%s", "name": "%s", "email": "%s"},
                 "items": [{"sku": "PB-001-ESS", "quantity": 1, "unit_price": "%s"}],
                 "total_amount": "%s"}
                """
                    .formatted(
                        orderId,
                        paid.get("paidAt").asText(),
                        customer.get("id").asText(),
                        customer.get("name").asText(),
                        customer.get("email").asText(),
                        item.get("unitPrice").asText(),
                        paid.get("total").asText())));
  }

  @Test
  void refundingAddsOrderRefundedAfterOrderPaid() throws Exception {
    UUID orderId = UUID.fromString(createPendingOrder().get("id").asText());
    pay(orderId).andExpect(status().isOk());

    JsonNode refunded =
        json(postJson("/api/orders/" + orderId + "/refund", "").andExpect(status().isOk()));

    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(orderId);
    assertThat(written)
        .extracting(OutboxEvent::getEventType)
        .containsExactly(OutboxEventType.ORDER_PAID, OutboxEventType.ORDER_REFUNDED);
    assertThat(written.get(1).getSequence()).isGreaterThan(written.get(0).getSequence());
    assertThat(objectMapper.readTree(written.get(1).getPayload()))
        .isEqualTo(
            objectMapper.readTree(
                "{\"status\":\"refunded\",\"occurred_at\":\"%s\"}"
                    .formatted(refunded.get("refundedAt").asText())));
  }

  @Test
  void changesThatPulseBoardNeverSeesWriteNoEvent() throws Exception {
    UUID canceled = UUID.fromString(createPendingOrder().get("id").asText());
    postJson("/api/orders/" + canceled + "/cancel", "").andExpect(status().isOk());

    UUID pending = UUID.fromString(createPendingOrder().get("id").asText());
    postJson("/api/orders/" + pending + "/refund", "")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("invalid_order_transition"));

    assertThat(events.findByAggregateIdOrderBySequence(canceled)).isEmpty();
    assertThat(events.findByAggregateIdOrderBySequence(pending)).isEmpty();
  }

  @Test
  void failingToWriteTheEventRollsBackThePayment() throws Exception {
    UUID orderId = UUID.fromString(createPendingOrder().get("id").asText());
    rejectOutboxInsertsFor(orderId);
    try {
      pay(orderId)
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("internal_error"));

      getJson("/api/orders/" + orderId)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.paidAt").doesNotExist())
          .andExpect(jsonPath("$.paymentMethod").doesNotExist());
      assertThat(events.findByAggregateIdOrderBySequence(orderId)).isEmpty();
    } finally {
      jdbc.execute("DROP TRIGGER outbox_fail_for_test ON outbox_events");
      jdbc.execute("DROP FUNCTION outbox_fail_for_test()");
    }

    // Nothing was left half-done: the same payment now goes through, with exactly one event.
    pay(orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
    assertThat(events.findByAggregateIdOrderBySequence(orderId)).hasSize(1);
  }

  /** A real database failure while inserting the event of this order (and only this one). */
  private void rejectOutboxInsertsFor(UUID orderId) {
    jdbc.execute(
        """
        CREATE FUNCTION outbox_fail_for_test() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.aggregate_id = '%s' THEN
            RAISE EXCEPTION 'simulated outbox failure';
          END IF;
          RETURN NEW;
        END $$
        """
            .formatted(orderId));
    jdbc.execute(
        "CREATE TRIGGER outbox_fail_for_test BEFORE INSERT ON outbox_events"
            + " FOR EACH ROW EXECUTE FUNCTION outbox_fail_for_test()");
  }

  private ResultActions pay(UUID orderId) throws Exception {
    return postJson("/api/orders/" + orderId + "/pay", "{\"paymentMethod\":\"PIX\"}");
  }
}

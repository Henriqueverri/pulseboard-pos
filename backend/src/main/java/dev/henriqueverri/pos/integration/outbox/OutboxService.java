package dev.henriqueverri.pos.integration.outbox;

import dev.henriqueverri.pos.integration.pulseboard.IngestPayloadFactory;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

  private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

  private final EntityManager entityManager;
  private final Clock clock;

  public OutboxService(EntityManager entityManager, Clock clock) {
    this.entityManager = entityManager;
    this.clock = clock;
  }

  /**
   * Records the intent to deliver {@code payload}. Must run inside the transaction that changes the
   * order: either both are committed or neither is, so a paid order always has its event and an
   * event never exists for a change that was rolled back.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent enqueue(UUID orderId, OutboxEventType type, String payload) {
    OutboxEvent event = OutboxEvent.forOrder(orderId, type, payload, clock.instant());
    entityManager.persist(event);
    // Links the HTTP request that changed the order (request_id in the MDC) to the event, whose
    // deliveries are logged with request_id "pos-<event id>".
    log.atInfo()
        .addKeyValue("event_id", event.getId())
        .addKeyValue("order_id", orderId)
        .addKeyValue("external_id", IngestPayloadFactory.orderExternalId(orderId))
        .addKeyValue("event_type", type)
        .addKeyValue("outcome", "enqueued")
        .log("Outbox event enqueued");
    return event;
  }
}

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
    if (!type.isOrderEvent()) {
      throw new IllegalArgumentException(type + " is not an order event");
    }
    OutboxEvent event = persist(orderId, type, payload);
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

  /** A {@code STOCK_CHANGED} of the product, in the transaction that changed its stock. */
  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent enqueueStock(UUID productId, String sku, String payload) {
    OutboxEvent event = persist(productId, OutboxEventType.STOCK_CHANGED, payload);
    log.atInfo()
        .addKeyValue("event_id", event.getId())
        .addKeyValue("product_id", productId)
        .addKeyValue("sku", sku)
        .addKeyValue("event_type", OutboxEventType.STOCK_CHANGED)
        .addKeyValue("outcome", "enqueued")
        .log("Outbox event enqueued");
    return event;
  }

  private OutboxEvent persist(UUID aggregateId, OutboxEventType type, String payload) {
    OutboxEvent event = OutboxEvent.of(aggregateId, type, payload, clock.instant());
    entityManager.persist(event);
    return event;
  }
}

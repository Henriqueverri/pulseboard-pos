package dev.henriqueverri.pos.integration.outbox;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

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
    return event;
  }
}

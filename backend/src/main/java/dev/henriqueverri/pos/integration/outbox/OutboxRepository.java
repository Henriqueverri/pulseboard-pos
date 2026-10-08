package dev.henriqueverri.pos.integration.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read side of the outbox. Delivery state changes go through {@link OutboxStore}. */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByAggregateIdOrderBySequence(UUID aggregateId);
}

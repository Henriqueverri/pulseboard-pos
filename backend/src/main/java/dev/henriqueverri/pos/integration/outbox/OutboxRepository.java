package dev.henriqueverri.pos.integration.outbox;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Read side of the outbox. Delivery state changes go through {@link OutboxStore}. */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByAggregateIdOrderBySequence(UUID aggregateId);

  Page<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);

  /** Events that hold back later events of the same orders (section 8.4 of the plan). */
  List<OutboxEvent> findByAggregateIdInAndStatusNotOrderBySequence(
      Collection<UUID> aggregateIds, OutboxStatus status);

  long countByStatusIn(Collection<OutboxStatus> statuses);

  @Query(
      "select max(e.processedAt) from OutboxEvent e"
          + " where e.status = dev.henriqueverri.pos.integration.outbox.OutboxStatus.SENT")
  Instant lastSentAt();
}

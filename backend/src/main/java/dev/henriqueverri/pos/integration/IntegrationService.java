package dev.henriqueverri.pos.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henriqueverri.pos.integration.dto.IntegrationEventResponse;
import dev.henriqueverri.pos.integration.dto.IntegrationHealthResponse;
import dev.henriqueverri.pos.integration.dto.RetryConfigurationFailuresResponse;
import dev.henriqueverri.pos.integration.outbox.IntegrationPause;
import dev.henriqueverri.pos.integration.outbox.OutboxEvent;
import dev.henriqueverri.pos.integration.outbox.OutboxRepository;
import dev.henriqueverri.pos.integration.outbox.OutboxStatus;
import dev.henriqueverri.pos.integration.outbox.OutboxStore;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import dev.henriqueverri.pos.shared.PageResponse;
import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Integration screen: outbox events, manual reprocessing and the integration's health. */
@Service
public class IntegrationService {

  private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);
  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "sequence");

  private final OutboxRepository events;
  private final OutboxStore store;
  private final IntegrationPause pause;
  private final PulseBoardProperties pulseBoard;
  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public IntegrationService(
      OutboxRepository events,
      OutboxStore store,
      IntegrationPause pause,
      PulseBoardProperties pulseBoard,
      JdbcClient jdbc,
      ObjectMapper objectMapper,
      Clock clock) {
    this.events = events;
    this.store = store;
    this.pause = pause;
    this.pulseBoard = pulseBoard;
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<IntegrationEventResponse> list(OutboxStatus status, int page, int size) {
    PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
    Page<OutboxEvent> found =
        status == null ? events.findAll(pageable) : events.findByStatus(status, pageable);
    List<IntegrationEventResponse> content = views(found.getContent());
    return new PageResponse<>(
        content,
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  @Transactional(readOnly = true)
  public IntegrationEventResponse get(UUID id) {
    OutboxEvent event = events.findById(id).orElseThrow(() -> ApiException.notFound("Evento"));
    IntegrationEventResponse view = views(List.of(event)).get(0);
    return IntegrationEventResponse.from(
        event, view.orderNumber(), view.blockedBy(), payload(event));
  }

  /** The events of one order, oldest first, for the order detail. */
  @Transactional(readOnly = true)
  public List<IntegrationEventResponse> ofOrder(UUID orderId) {
    return views(events.findByAggregateIdOrderBySequence(orderId));
  }

  /**
   * {@code FAILED → PENDING}, due now. {@code attempts} is kept as history; the attempt limit and
   * the backoff count again from here.
   */
  @Transactional
  public IntegrationEventResponse retry(UUID id) {
    if (!store.retry(id, clock.instant())) {
      events.findById(id).orElseThrow(() -> ApiException.notFound("Evento"));
      throw ApiException.conflict(
          ErrorCodes.INTEGRATION_EVENT_NOT_FAILED, "Só eventos FAILED podem ser reprocessados.");
    }
    log.info("Outbox event {} queued again by manual retry", id);
    return get(id);
  }

  /** After fixing the API key: every configuration failure back to the queue, pause lifted. */
  @Transactional
  public RetryConfigurationFailuresResponse retryConfigurationFailures() {
    int retried = store.retryConfigurationFailures(clock.instant());
    pause.clear();
    log.info("{} configuration failures queued again; integration pause cleared", retried);
    return new RetryConfigurationFailuresResponse(retried);
  }

  @Transactional(readOnly = true)
  public IntegrationHealthResponse health() {
    return new IntegrationHealthResponse(
        pulseBoard.isActive(),
        pause.pausedUntil(clock.instant()),
        pulseBoard.apiUrl().toString(),
        pulseBoard.keyPrefix(),
        events.countByStatusIn(Set.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING)),
        events.countByStatusIn(Set.of(OutboxStatus.FAILED)),
        events.lastSentAt());
  }

  private List<IntegrationEventResponse> views(List<OutboxEvent> page) {
    if (page.isEmpty()) {
      return List.of();
    }
    Set<UUID> orderIds = page.stream().map(OutboxEvent::getAggregateId).collect(Collectors.toSet());
    Map<UUID, Long> orderNumbers = orderNumbers(orderIds);
    Map<UUID, List<OutboxEvent>> unsent =
        events.findByAggregateIdInAndStatusNotOrderBySequence(orderIds, OutboxStatus.SENT).stream()
            .collect(Collectors.groupingBy(OutboxEvent::getAggregateId));
    return page.stream()
        .map(
            event ->
                IntegrationEventResponse.from(
                    event,
                    orderNumbers.get(event.getAggregateId()),
                    blockedBy(event, unsent.getOrDefault(event.getAggregateId(), List.of())),
                    null))
        .toList();
  }

  /** The first earlier event of the same order that is not {@code SENT}, for a waiting event. */
  private static Long blockedBy(OutboxEvent event, List<OutboxEvent> unsentOfOrder) {
    if (event.getStatus() != OutboxStatus.PENDING) {
      return null;
    }
    return unsentOfOrder.stream()
        .map(OutboxEvent::getSequence)
        .filter(sequence -> sequence < event.getSequence())
        .findFirst()
        .orElse(null);
  }

  private Map<UUID, Long> orderNumbers(Collection<UUID> orderIds) {
    return jdbc
        .sql("SELECT id, number FROM orders WHERE id IN (:ids)")
        .param("ids", orderIds)
        .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getLong("number")))
        .list()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private JsonNode payload(OutboxEvent event) {
    try {
      return objectMapper.readTree(event.getPayload());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Stored payload of event " + event.getId() + " is not JSON", e);
    }
  }
}

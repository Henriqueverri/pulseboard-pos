package dev.henriqueverri.pos.integration.outbox;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardClient;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** The claim query of section 8.4 against the real PostgreSQL: lease, ordering, concurrency. */
class OutboxClaimTest extends OutboxIntegrationTest {

  @Autowired private TransactionTemplate transactions;
  @Autowired private PulseBoardClient client;
  @Autowired private IngestResponseClassifier classifier;
  @Autowired private RetryPolicy retryPolicy;
  @Autowired private PulseBoardProperties pulseBoard;

  /** A refund is only eligible after the sale of the same order was sent. */
  @Test
  void eventsOfAnOrderAreClaimedInSequence() throws Exception {
    UUID orderId = payNewOrder();
    refund(orderId);
    List<OutboxEvent> written = events.findByAggregateIdOrderBySequence(orderId);
    OutboxEvent paid = written.get(0);
    OutboxEvent refunded = written.get(1);

    Instant now = clock.instant();
    List<ClaimedEvent> first = store.claim(now, now.plusSeconds(120), 20);
    assertThat(first).extracting(ClaimedEvent::id).containsExactly(paid.getId());

    // The sale failed for good: the refund stays blocked, and the screen says by what.
    store.markFailed(first.get(0), now, FailureKind.PERMANENT, 422, null, "validation_failed", "x");
    assertThat(store.claim(now, now.plusSeconds(120), 20)).isEmpty();
    getJson("/api/integration/events/" + refunded.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.blockedBy").value(paid.getSequence()));

    // Retried and sent: now the refund goes.
    store.retry(paid.getId(), now);
    ClaimedEvent again = store.claim(now, now.plusSeconds(120), 20).get(0);
    assertThat(again.id()).isEqualTo(paid.getId());
    store.markSent(again, now, 201, null, null);
    assertThat(store.claim(now, now.plusSeconds(120), 20))
        .extracting(ClaimedEvent::id)
        .containsExactly(refunded.getId());
  }

  @Test
  void anEventInBackoffIsNotClaimedBeforeItsTime() throws Exception {
    payNewOrder();
    Instant now = clock.instant();
    ClaimedEvent event = store.claim(now, now.plusSeconds(120), 20).get(0);
    store.reschedule(event, now, now.plusSeconds(30), 503, null, "HTTP_5XX", "x");

    assertThat(store.claim(now.plusSeconds(29), now.plusSeconds(150), 20)).isEmpty();
    assertThat(store.claim(now.plusSeconds(30), now.plusSeconds(150), 20))
        .extracting(ClaimedEvent::id)
        .containsExactly(event.id());
  }

  @Test
  void aLiveLeaseIsRespectedAndAnExpiredOneIsReclaimed() throws Exception {
    payNewOrder();
    Instant now = clock.instant();
    ClaimedEvent held = store.claim(now, now.plus(Duration.ofMinutes(2)), 20).get(0);

    assertThat(store.claim(now.plusSeconds(119), now.plusSeconds(239), 20)).isEmpty();

    List<ClaimedEvent> reclaimed = store.claim(now.plusSeconds(121), now.plusSeconds(241), 20);
    assertThat(reclaimed).extracting(ClaimedEvent::id).containsExactly(held.id());
    assertThat(reclaimed.get(0).attempts()).isEqualTo(held.attempts() + 1);
  }

  /** While one transaction holds rows, a concurrent claim skips them instead of waiting. */
  @Test
  void concurrentClaimsSkipLockedRows() throws Exception {
    for (int i = 0; i < 4; i++) {
      payNewOrder();
    }
    Instant now = clock.instant();
    CountDownLatch claimed = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<List<ClaimedEvent>> holder =
          executor.submit(
              () ->
                  transactions.execute(
                      tx -> {
                        List<ClaimedEvent> mine = store.claim(now, now.plusSeconds(120), 2);
                        claimed.countDown();
                        await(release);
                        return mine;
                      }));
      assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();

      List<ClaimedEvent> others = store.claim(now, now.plusSeconds(120), 20);
      release.countDown();
      List<ClaimedEvent> mine = holder.get(10, TimeUnit.SECONDS);

      assertThat(mine).hasSize(2);
      assertThat(others).hasSize(2);
      assertThat(others).extracting(ClaimedEvent::id).doesNotContainAnyElementsOf(ids(mine));
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  /** Two workers polling at the same time: every event is sent exactly once. */
  @Test
  void twoWorkersNeverSendTheSameEvent() throws Exception {
    int total = 12;
    for (int i = 0; i < total; i++) {
      payNewOrder();
    }
    PULSEBOARD.stubFor(
        post(urlEqualTo(TRANSACTIONS))
            .willReturn(aResponse().withStatus(201).withBody("{}").withFixedDelay(50)));
    OutboxProperties smallBatches =
        new OutboxProperties(
            Duration.ofSeconds(2),
            2,
            Duration.ofMinutes(2),
            10,
            Duration.ofSeconds(10),
            Duration.ofMinutes(15),
            Duration.ofMinutes(15));
    OutboxWorker a = newWorker(smallBatches);
    OutboxWorker b = newWorker(smallBatches);
    CountDownLatch start = new CountDownLatch(1);

    CompletableFuture<Integer> byA = CompletableFuture.supplyAsync(() -> drain(a, start));
    CompletableFuture<Integer> byB = CompletableFuture.supplyAsync(() -> drain(b, start));
    start.countDown();
    int claimedByA = byA.get(60, TimeUnit.SECONDS);
    int claimedByB = byB.get(60, TimeUnit.SECONDS);

    assertThat(claimedByA + claimedByB).isEqualTo(total);
    List<LoggedRequest> requests = PULSEBOARD.findAll(postRequestedFor(urlMatching(".*")));
    assertThat(requests).hasSize(total);
    assertThat(requests.stream().map(r -> r.getHeader("X-Request-Id")).distinct()).hasSize(total);
    assertThat(events.findAll())
        .hasSize(total)
        .allSatisfy(
            event -> {
              assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
              assertThat(event.getAttempts()).isEqualTo(1);
            });
  }

  private OutboxWorker newWorker(OutboxProperties settings) {
    return new OutboxWorker(
        store, client, classifier, retryPolicy, pause, pulseBoard, settings, clock);
  }

  private static int drain(OutboxWorker worker, CountDownLatch start) {
    await(start);
    int claimed = 0;
    for (int batch = worker.runOnce(); batch > 0; batch = worker.runOnce()) {
      claimed += batch;
    }
    return claimed;
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static List<UUID> ids(List<ClaimedEvent> claimed) {
    List<UUID> ids = new ArrayList<>();
    claimed.forEach(event -> ids.add(event.id()));
    return ids;
  }
}

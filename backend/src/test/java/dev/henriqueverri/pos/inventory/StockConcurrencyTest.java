package dev.henriqueverri.pos.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import dev.henriqueverri.pos.order.OrderService;
import dev.henriqueverri.pos.order.PaymentMethod;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real concurrent transactions against PostgreSQL: the conditional {@code UPDATE} is the only
 * guard, with no lock taken in Java.
 */
class StockConcurrencyTest extends ApiIntegrationTest {

  @Autowired private OrderService orders;

  @Test
  void twoPaymentsForTheLastUnitExactlyOneWins() throws Exception {
    UUID beer = id(createTrackedProduct(unique("LAST"), 1, 0).get("id").asText());
    UUID first = pendingOrder("[" + item(beer, 1) + "]");
    UUID second = pendingOrder("[" + item(beer, 1) + "]");

    List<Outcome> outcomes = payConcurrently(first, second);

    assertThat(outcomes).containsExactlyInAnyOrder(Outcome.PAID, Outcome.INSUFFICIENT_STOCK);
    assertThat(stockOf(beer)).isZero();
    getJson("/api/products/" + beer + "/stock-movements")
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].kind").value("SALE"))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(0));
  }

  /**
   * Both orders hold the same two products, listed in opposite order. Taking them in {@code
   * product_id} order means neither payment can hold one row while waiting for the other's.
   */
  @Test
  void paymentsSharingProductsDoNotDeadlock() throws Exception {
    UUID a = id(createTrackedProduct(unique("PAIR-A"), 1, 0).get("id").asText());
    UUID b = id(createTrackedProduct(unique("PAIR-B"), 1, 0).get("id").asText());
    UUID first = pendingOrder("[" + item(a, 1) + "," + item(b, 1) + "]");
    UUID second = pendingOrder("[" + item(b, 1) + "," + item(a, 1) + "]");

    List<Outcome> outcomes = payConcurrently(first, second);

    assertThat(outcomes).containsExactlyInAnyOrder(Outcome.PAID, Outcome.INSUFFICIENT_STOCK);
    assertThat(stockOf(a)).isZero();
    assertThat(stockOf(b)).isZero();
  }

  private enum Outcome {
    PAID,
    INSUFFICIENT_STOCK
  }

  private List<Outcome> payConcurrently(UUID... orderIds) throws Exception {
    CountDownLatch ready = new CountDownLatch(orderIds.length);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(orderIds.length);
    try {
      List<Future<Outcome>> futures = new ArrayList<>();
      for (UUID orderId : orderIds) {
        Callable<Outcome> pay =
            () -> {
              ready.countDown();
              start.await();
              try {
                orders.pay(orderId, PaymentMethod.CASH);
                return Outcome.PAID;
              } catch (InsufficientStockException e) {
                return Outcome.INSUFFICIENT_STOCK;
              }
            };
        futures.add(executor.submit(pay));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<Outcome> outcomes = new ArrayList<>();
      for (Future<Outcome> future : futures) {
        outcomes.add(future.get(30, TimeUnit.SECONDS));
      }
      return outcomes;
    } finally {
      executor.shutdownNow();
    }
  }

  private UUID pendingOrder(String items) throws Exception {
    return id(createOrder(createCustomer(), items).get("id").asText());
  }

  private static String item(UUID product, int quantity) {
    return "{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}";
  }

  private static UUID id(String value) {
    return UUID.fromString(value);
  }
}

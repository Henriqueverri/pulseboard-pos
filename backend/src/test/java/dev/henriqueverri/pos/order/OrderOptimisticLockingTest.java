package dev.henriqueverri.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two transactions read the same order version; one pays and commits, the other cancels based on
 * the stale state. {@code @Version} must reject the second write instead of letting a paid order
 * end up canceled.
 */
class OrderOptimisticLockingTest extends ApiIntegrationTest {

  @Autowired private OrderRepository orders;

  @Autowired private OrderService orderService;

  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void staleConcurrentChangeIsRejected() throws Exception {
    UUID id = UUID.fromString(createPendingOrder().get("id").asText());
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
    concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    assertThatThrownBy(
            () ->
                outer.executeWithoutResult(
                    tx -> {
                      Order stale = orders.findById(id).orElseThrow();
                      assertThat(stale.getStatus()).isEqualTo(OrderStatus.PENDING);

                      concurrent.executeWithoutResult(
                          other -> orderService.pay(id, PaymentMethod.CASH));

                      stale.cancel(Instant.now());
                      orders.flush();
                    }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    Order stored = orders.findById(id).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(stored.getCanceledAt()).isNull();
    assertThat(stored.getVersion()).isEqualTo(1);
  }
}

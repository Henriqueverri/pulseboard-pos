package dev.henriqueverri.pos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.henriqueverri.pos.catalog.Product;
import dev.henriqueverri.pos.catalog.ProductRepository;
import dev.henriqueverri.pos.customer.Customer;
import dev.henriqueverri.pos.customer.CustomerRepository;
import dev.henriqueverri.pos.order.Order;
import dev.henriqueverri.pos.order.OrderRepository;
import dev.henriqueverri.pos.support.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Repositories and constraints on the real PostgreSQL schema built by Flyway (never H2). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RepositoryIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-06T21:00:00Z");

  @Autowired private ProductRepository products;
  @Autowired private CustomerRepository customers;
  @Autowired private OrderRepository orders;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void seedsTheFortyDemoSkusWithDemoPrices() {
    List<String> skus = jdbc.queryForList("SELECT sku FROM products ORDER BY sku", String.class);

    assertThat(skus).hasSize(40);
    assertThat(skus).allMatch(sku -> sku.matches("PB-0(0[1-9]|1\\d|20)-(ESS|PRO)"));
    assertThat(skus).first().isEqualTo("PB-001-ESS");
    assertThat(skus).last().isEqualTo("PB-020-PRO");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE active", Integer.class))
        .isEqualTo(40);

    assertProduct("PB-001-ESS", "Mouse sem fio Essential", "79.90");
    assertProduct("PB-002-ESS", "Teclado mecânico Essential", "249.90");
    assertProduct("PB-010-PRO", "Monitor 24\" Pro", "1049.90");
    assertProduct("PB-013-PRO", "Mesa regulável Pro", "2399.90");
    assertProduct("PB-016-PRO", "Apoio de punho Pro", "62.90");
    assertProduct("PB-020-PRO", "Ring light Pro", "139.90");
  }

  private void assertProduct(String sku, String name, String price) {
    Product product = products.findBySku(sku).orElseThrow();
    assertThat(product.getName()).isEqualTo(name);
    assertThat(product.getPrice()).isEqualTo(new BigDecimal(price));
  }

  @Test
  void skuUniqueIndexIsTheFinalGuard() {
    products.saveAndFlush(new Product("REPO-SKU", "A", new BigDecimal("1.00"), true, NOW));

    assertThatThrownBy(
            () ->
                products.saveAndFlush(
                    new Product("REPO-SKU", "B", new BigDecimal("2.00"), true, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void customerEmailIsStoredLowerCaseAndUnique() {
    customers.saveAndFlush(new Customer("Ana", "Ana@Cliente.POS.example", null, NOW));

    assertThat(customers.existsByEmail("ana@cliente.pos.example")).isTrue();
    assertThatThrownBy(
            () ->
                customers.saveAndFlush(
                    new Customer("Outra Ana", "ANA@cliente.pos.example", null, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsUpperCaseEmail() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO customers (id, name, email, created_at, updated_at)"
                        + " VALUES (gen_random_uuid(), 'X', 'UPPER@x.example', now(), now())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void persistsOrderWithItemsAndSequentialNumbers() {
    Customer customer =
        customers.saveAndFlush(new Customer("Ana", "order-repo@cliente.pos.example", null, NOW));
    Product mouse = products.findBySku("PB-001-ESS").orElseThrow();
    Product keyboard = products.findBySku("PB-002-ESS").orElseThrow();

    long first = orders.nextNumber();
    long second = orders.nextNumber();
    assertThat(second).isEqualTo(first + 1);

    Order order =
        orders.saveAndFlush(
            Order.place(
                second,
                customer,
                "BRL",
                List.of(new Order.Line(mouse, 2), new Order.Line(keyboard, 1)),
                NOW));

    Order stored = orders.findWithDetailsById(order.getId()).orElseThrow();
    assertThat(stored.getNumber()).isEqualTo(second);
    assertThat(stored.getVersion()).isZero();
    assertThat(stored.getItems()).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(line_total)::text FROM order_items WHERE order_id = ?",
                String.class,
                order.getId()))
        .isEqualTo("409.70");
    assertThat(
            jdbc.queryForObject(
                "SELECT total::text FROM orders WHERE id = ?", String.class, order.getId()))
        .isEqualTo("409.70");
  }

  @Test
  void databaseRejectsUnknownStatus() {
    Order order = savedOrder("status@cliente.pos.example");

    assertThatThrownBy(
            () -> jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", order.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsDuplicateProductPerOrder() {
    Order order = savedOrder("duplicate-item@cliente.pos.example");
    Product mouse = products.findBySku("PB-001-ESS").orElseThrow();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO order_items (id, order_id, product_id, sku, product_name, quantity,"
                        + " unit_price, line_total) VALUES (gen_random_uuid(), ?, ?, 'PB-001-ESS',"
                        + " 'Mouse', 1, 79.90, 79.90)",
                    order.getId(),
                    mouse.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Order savedOrder(String email) {
    Customer customer = customers.saveAndFlush(new Customer("Ana", email, null, NOW));
    Product mouse = products.findBySku("PB-001-ESS").orElseThrow();
    return orders.saveAndFlush(
        Order.place(orders.nextNumber(), customer, "BRL", List.of(new Order.Line(mouse, 1)), NOW));
  }
}

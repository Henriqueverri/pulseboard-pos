package dev.henriqueverri.pos.inventory;

import dev.henriqueverri.pos.inventory.dto.StockMovementResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The SQL behind the stock. Balances change only through {@link #move}, a single conditional {@code
 * UPDATE}: the row lock lasts until the surrounding transaction ends, and the {@code WHERE}
 * re-checks the balance after waiting for it, so two sales can never both take the last unit.
 */
@Repository
class StockStore {

  private static final String MOVE =
      """
      UPDATE products
      SET stock_quantity = stock_quantity + :delta
      WHERE id = :productId AND track_stock AND stock_quantity + :delta >= 0
      RETURNING stock_quantity
      """;

  private static final String MOVEMENTS =
      """
      SELECT m.id, m.product_id, m.kind, m.quantity, m.balance_after, m.order_id,
             o.number AS order_number, m.reason, u.name AS user_name, m.created_at
      FROM stock_movements m
      LEFT JOIN orders o ON o.id = m.order_id
      LEFT JOIN users u ON u.id = m.user_id
      """;

  record Stock(boolean tracked, int quantity) {}

  record SoldLine(UUID productId, int quantity) {}

  private final JdbcClient jdbc;

  StockStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * New balance, or empty when the product has no stock control or the balance would go negative.
   */
  OptionalInt move(UUID productId, int delta) {
    return jdbc.sql(MOVE)
        .param("delta", delta)
        .param("productId", productId, Types.OTHER)
        .query(Integer.class)
        .optional()
        .map(OptionalInt::of)
        .orElse(OptionalInt.empty());
  }

  Optional<Stock> stock(UUID productId) {
    return jdbc.sql("SELECT track_stock, stock_quantity FROM products WHERE id = :productId")
        .param("productId", productId, Types.OTHER)
        .query((rs, row) -> new Stock(rs.getBoolean("track_stock"), rs.getInt("stock_quantity")))
        .optional();
  }

  /** Units each product lost to the sale of this order (positive), by product. */
  List<SoldLine> soldBy(UUID orderId) {
    return jdbc.sql(
            """
            SELECT product_id, -quantity AS quantity
            FROM stock_movements
            WHERE order_id = :orderId AND kind = 'SALE'
            """)
        .param("orderId", orderId, Types.OTHER)
        .query((rs, row) -> new SoldLine(rs.getObject("product_id", UUID.class), rs.getInt(2)))
        .list();
  }

  UUID insert(
      UUID productId,
      StockMovementKind kind,
      int quantity,
      int balanceAfter,
      UUID orderId,
      String reason,
      UUID userId,
      Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO stock_movements
              (id, product_id, kind, quantity, balance_after, order_id, reason, user_id, created_at)
            VALUES
              (:id, :productId, :kind, :quantity, :balanceAfter, :orderId, :reason, :userId, :now)
            """)
        .param("id", id, Types.OTHER)
        .param("productId", productId, Types.OTHER)
        .param("kind", kind.name())
        .param("quantity", quantity)
        .param("balanceAfter", balanceAfter)
        .param("orderId", orderId, Types.OTHER)
        .param("reason", reason, Types.VARCHAR)
        .param("userId", userId, Types.OTHER)
        .param("now", Timestamp.from(now))
        .update();
    return id;
  }

  List<StockMovementResponse> history(UUID productId, int limit, long offset) {
    return jdbc.sql(
            MOVEMENTS
                + """
                WHERE m.product_id = :productId
                ORDER BY m.sequence DESC
                LIMIT :limit OFFSET :offset
                """)
        .param("productId", productId, Types.OTHER)
        .param("limit", limit)
        .param("offset", offset)
        .query((rs, row) -> movement(rs))
        .list();
  }

  long count(UUID productId) {
    return jdbc.sql("SELECT count(*) FROM stock_movements WHERE product_id = :productId")
        .param("productId", productId, Types.OTHER)
        .query(Long.class)
        .single();
  }

  Optional<StockMovementResponse> find(UUID id) {
    return jdbc.sql(MOVEMENTS + "WHERE m.id = :id")
        .param("id", id, Types.OTHER)
        .query((rs, row) -> movement(rs))
        .optional();
  }

  private static StockMovementResponse movement(ResultSet rs) throws SQLException {
    return new StockMovementResponse(
        rs.getObject("id", UUID.class),
        rs.getObject("product_id", UUID.class),
        StockMovementKind.valueOf(rs.getString("kind")),
        rs.getInt("quantity"),
        rs.getInt("balance_after"),
        rs.getObject("order_id", UUID.class),
        rs.getObject("order_number", Long.class),
        rs.getString("reason"),
        rs.getString("user_name"),
        rs.getTimestamp("created_at").toInstant());
  }
}

package dev.henriqueverri.pos.inventory;

import dev.henriqueverri.pos.catalog.ProductRepository;
import dev.henriqueverri.pos.inventory.dto.StockMovementRequest;
import dev.henriqueverri.pos.inventory.dto.StockMovementResponse;
import dev.henriqueverri.pos.security.CurrentUser;
import dev.henriqueverri.pos.shared.PageResponse;
import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock is taken when an order is paid, not when it is created: a PENDING order reserves nothing,
 * and canceling it has nothing to give back. Every change of a balance writes a movement with the
 * resulting balance, in the same transaction.
 */
@Service
public class InventoryService {

  private final StockStore store;
  private final ProductRepository products;
  private final Clock clock;

  public InventoryService(StockStore store, ProductRepository products, Clock clock) {
    this.store = store;
    this.products = products;
    this.clock = clock;
  }

  public record Line(UUID productId, String sku, int quantity) {}

  /**
   * Takes the stock of a paid order. Products without stock control are skipped. If any product is
   * short, throws {@link InsufficientStockException} listing all of them; the caller's transaction
   * is rolled back, including the units already taken from the other products.
   *
   * <p>Lines are locked in {@code product_id} order, so two payments sharing products always lock
   * them in the same order and cannot deadlock each other.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void sell(UUID orderId, List<Line> lines) {
    Instant now = now();
    UUID userId = CurrentUser.id().orElse(null);
    Map<String, List<String>> shortages = new LinkedHashMap<>();
    for (Line line : sortedByProduct(lines, Line::productId)) {
      OptionalInt balance = store.move(line.productId(), -line.quantity());
      if (balance.isPresent()) {
        store.insert(
            line.productId(),
            StockMovementKind.SALE,
            -line.quantity(),
            balance.getAsInt(),
            orderId,
            null,
            userId,
            now);
        continue;
      }
      StockStore.Stock stock = store.stock(line.productId()).orElseThrow();
      if (stock.tracked()) {
        shortages.put(line.sku(), InsufficientStockException.available(stock.quantity()));
      }
    }
    if (!shortages.isEmpty()) {
      throw new InsufficientStockException(shortages);
    }
  }

  /**
   * Gives back what the sale of this order took, read from its SALE movements, so a product whose
   * stock control was turned on after the sale gets nothing it never lost.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void restock(UUID orderId) {
    Instant now = now();
    UUID userId = CurrentUser.id().orElse(null);
    for (StockStore.SoldLine sold :
        sortedByProduct(store.soldBy(orderId), StockStore.SoldLine::productId)) {
      OptionalInt balance = store.move(sold.productId(), sold.quantity());
      if (balance.isPresent()) {
        store.insert(
            sold.productId(),
            StockMovementKind.REFUND,
            sold.quantity(),
            balance.getAsInt(),
            orderId,
            null,
            userId,
            now);
      }
    }
  }

  @Transactional
  public StockMovementResponse record(UUID productId, StockMovementRequest request) {
    StockStore.Stock stock =
        store.stock(productId).orElseThrow(() -> ApiException.notFound("Produto"));
    if (!stock.tracked()) {
      throw ApiException.unprocessable(
          ErrorCodes.STOCK_NOT_TRACKED,
          "Ative o controle de estoque do produto antes de movimentar o saldo.");
    }
    int quantity = request.quantity();
    String reason = request.reason() == null ? null : request.reason().strip();
    if (reason != null && reason.isEmpty()) {
      reason = null;
    }
    StockMovementKind kind;
    if (request.kind() == StockMovementRequest.Kind.RECEIPT) {
      if (quantity <= 0) {
        throw ApiException.invalidField("quantity", "A entrada precisa ser maior que zero.");
      }
      kind = StockMovementKind.RECEIPT;
    } else {
      if (quantity == 0) {
        throw ApiException.invalidField("quantity", "O ajuste precisa ser diferente de zero.");
      }
      if (reason == null) {
        throw ApiException.invalidField("reason", "Informe o motivo do ajuste.");
      }
      kind = StockMovementKind.ADJUSTMENT;
    }

    OptionalInt balance = store.move(productId, quantity);
    if (balance.isEmpty()) {
      String sku = products.findById(productId).orElseThrow().getSku();
      int available = store.stock(productId).orElseThrow().quantity();
      throw new InsufficientStockException(
          Map.of(sku, InsufficientStockException.available(available)));
    }
    UUID id =
        store.insert(
            productId,
            kind,
            quantity,
            balance.getAsInt(),
            null,
            reason,
            CurrentUser.id().orElse(null),
            now());
    return store.find(id).orElseThrow();
  }

  @Transactional(readOnly = true)
  public PageResponse<StockMovementResponse> history(UUID productId, int page, int size) {
    if (!products.existsById(productId)) {
      throw ApiException.notFound("Produto");
    }
    long total = store.count(productId);
    return new PageResponse<>(
        store.history(productId, size, (long) page * size),
        page,
        size,
        total,
        (int) ((total + size - 1) / size));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private static <T> List<T> sortedByProduct(List<T> lines, Function<T, UUID> productId) {
    return lines.stream().sorted(Comparator.comparing(productId)).toList();
  }
}

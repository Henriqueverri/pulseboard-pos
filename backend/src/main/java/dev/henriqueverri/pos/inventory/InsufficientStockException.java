package dev.henriqueverri.pos.inventory;

import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code insufficient_stock}, with one entry in {@code errors} per SKU short of stock. Thrown
 * inside the transaction that moves the stock, so everything it did is rolled back.
 */
public class InsufficientStockException extends ApiException {

  public InsufficientStockException(Map<String, List<String>> shortages) {
    super(
        HttpStatus.CONFLICT,
        ErrorCodes.INSUFFICIENT_STOCK,
        shortages.size() == 1
            ? "Saldo insuficiente para " + shortages.keySet().iterator().next() + "."
            : "Saldo insuficiente para " + shortages.size() + " produtos.",
        shortages);
  }

  static List<String> available(int available) {
    return List.of(available == 1 ? "Resta 1 unidade." : "Restam " + available + " unidades.");
  }
}

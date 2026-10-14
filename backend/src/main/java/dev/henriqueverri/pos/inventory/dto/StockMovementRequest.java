package dev.henriqueverri.pos.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sales and refunds move the stock on their own; by hand there are only receipts and adjustments.
 */
public record StockMovementRequest(
    @NotNull Kind kind,
    @Schema(
            description =
                "Entrada: unidades recebidas (positivo). Ajuste: diferença com sinal (-3 para"
                    + " três quebras).",
            example = "24")
        @NotNull
        @Min(-1_000_000)
        @Max(1_000_000)
        Integer quantity,
    @Schema(description = "Obrigatório no ajuste.", example = "Nota fiscal 1234") @Size(max = 200)
        String reason) {

  public enum Kind {
    RECEIPT,
    ADJUSTMENT
  }
}

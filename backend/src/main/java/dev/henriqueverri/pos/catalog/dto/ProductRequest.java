package dev.henriqueverri.pos.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The balance is not part of the product: it only changes through stock movements. {@code
 * trackStock} and {@code stockMin} are optional; when omitted, a new product has no stock control
 * and an update keeps the current values.
 */
public record ProductRequest(
    @Schema(example = "PB-001-ESS") @NotBlank @Size(max = 64) String sku,
    @Schema(example = "Mouse sem fio Essential") @NotBlank @Size(max = 120) String name,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @Schema(description = "Produtos inativos não podem ser vendidos.") @NotNull Boolean active,
    @Schema(description = "Controla o saldo: a venda baixa e o pagamento é recusado sem saldo.")
        Boolean trackStock,
    @Schema(description = "Saldo igual ou abaixo deste valor aparece como estoque baixo.")
        @Min(0)
        @Max(1_000_000)
        Integer stockMin) {}

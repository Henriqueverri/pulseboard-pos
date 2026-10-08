package dev.henriqueverri.pos.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
    @Schema(example = "PB-001-ESS") @NotBlank @Size(max = 64) String sku,
    @Schema(example = "Mouse sem fio Essential") @NotBlank @Size(max = 120) String name,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @Schema(description = "Produtos inativos não podem ser vendidos.") @NotNull Boolean active) {}

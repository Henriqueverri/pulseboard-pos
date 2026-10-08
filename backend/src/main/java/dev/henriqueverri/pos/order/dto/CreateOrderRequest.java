package dev.henriqueverri.pos.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Preços e totais são definidos pelo servidor a partir do catálogo; valores enviados pelo"
            + " cliente são ignorados.")
public record CreateOrderRequest(
    @NotNull UUID customerId,
    @NotEmpty @Size(max = 100) List<@Valid @NotNull OrderItemRequest> items) {}

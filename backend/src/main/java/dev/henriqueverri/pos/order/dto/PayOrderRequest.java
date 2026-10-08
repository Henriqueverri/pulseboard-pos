package dev.henriqueverri.pos.order.dto;

import dev.henriqueverri.pos.order.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PayOrderRequest(@NotNull PaymentMethod paymentMethod) {}

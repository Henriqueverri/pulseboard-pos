package dev.henriqueverri.pos.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
    @Schema(example = "Ana Lima") @NotBlank @Size(max = 120) String name,
    @Schema(
            example = "ana.lima@cliente.pos.example",
            description = "Único (sem diferenciar maiúsculas); armazenado em minúsculas.")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
    @Schema(description = "Opcional e interno ao POS (CPF/CNPJ).") @Size(max = 20)
        String document) {}

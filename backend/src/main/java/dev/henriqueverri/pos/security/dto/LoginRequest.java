package dev.henriqueverri.pos.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @Schema(example = "caixa@pos.example") @NotBlank @Size(max = 254) String email,
    @NotBlank @Size(max = 200) String password) {}

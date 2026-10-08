package dev.henriqueverri.pos.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** OAuth-style token response (snake_case, as in RFC 6749). */
public record LoginResponse(
    @JsonProperty("access_token") @Schema(name = "access_token") String accessToken,
    @JsonProperty("token_type") @Schema(name = "token_type", example = "Bearer") String tokenType,
    @JsonProperty("expires_in") @Schema(name = "expires_in", example = "28800") long expiresIn,
    UserResponse user) {}

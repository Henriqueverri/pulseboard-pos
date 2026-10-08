package dev.henriqueverri.pos.customer.dto;

import dev.henriqueverri.pos.customer.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
    UUID id, String name, String email, String document, Instant createdAt, Instant updatedAt) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(
        customer.getId(),
        customer.getName(),
        customer.getEmail(),
        customer.getDocument(),
        customer.getCreatedAt(),
        customer.getUpdatedAt());
  }
}

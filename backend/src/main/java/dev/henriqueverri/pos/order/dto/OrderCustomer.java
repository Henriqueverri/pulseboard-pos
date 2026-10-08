package dev.henriqueverri.pos.order.dto;

import dev.henriqueverri.pos.customer.Customer;
import java.util.UUID;

public record OrderCustomer(UUID id, String name, String email) {

  static OrderCustomer from(Customer customer) {
    return new OrderCustomer(customer.getId(), customer.getName(), customer.getEmail());
  }
}

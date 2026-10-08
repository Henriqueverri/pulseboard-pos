package dev.henriqueverri.pos.security.dto;

import dev.henriqueverri.pos.security.Role;
import dev.henriqueverri.pos.security.User;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
  }
}

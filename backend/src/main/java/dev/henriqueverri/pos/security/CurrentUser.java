package dev.henriqueverri.pos.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The user behind the current request, from the JWT {@code sub}; empty outside a request. */
public final class CurrentUser {

  private CurrentUser() {}

  public static Optional<UUID> id() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return Optional.of(UUID.fromString(jwt.getSubject()));
    }
    return Optional.empty();
  }
}

package dev.henriqueverri.pos.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pos.security.*}. Binding fails, and so does startup, when the JWT secret is missing or
 * shorter than 32 bytes: HS256 needs a key at least as long as its 256-bit output.
 */
@ConfigurationProperties("pos.security")
public record SecurityProperties(Jwt jwt, Users users) {

  public static final int MIN_SECRET_BYTES = 32;

  public SecurityProperties {
    if (jwt == null) {
      throw new IllegalArgumentException("pos.security.jwt is required");
    }
    users = users == null ? new Users(null, null) : users;
  }

  public record Jwt(String secret, Duration ttl) {

    public Jwt {
      if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
        throw new IllegalArgumentException(
            "POS_JWT_SECRET (pos.security.jwt.secret) must have at least "
                + MIN_SECRET_BYTES
                + " bytes");
      }
      if (ttl == null || ttl.isNegative() || ttl.isZero()) {
        throw new IllegalArgumentException("pos.security.jwt.ttl must be positive");
      }
    }

    public byte[] secretBytes() {
      return secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  /** Users created at startup; an entry without e-mail or password is skipped. */
  public record Users(SeedUser admin, SeedUser cashier) {}

  public record SeedUser(String email, String password, String name) {

    public boolean isConfigured() {
      return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
  }
}

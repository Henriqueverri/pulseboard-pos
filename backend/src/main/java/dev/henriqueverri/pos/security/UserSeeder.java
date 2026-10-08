package dev.henriqueverri.pos.security;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the ADMIN and CASHIER users from {@code POS_ADMIN_*} / {@code POS_CASHIER_*} at startup,
 * and keeps an existing user in sync when its password, name or role changes in the environment.
 * There is no sign-up endpoint.
 */
@Component
public class UserSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final SecurityProperties properties;
  private final Clock clock;

  public UserSeeder(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      SecurityProperties properties,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seed(properties.users().admin(), Role.ADMIN);
    seed(properties.users().cashier(), Role.CASHIER);
  }

  void seed(SecurityProperties.SeedUser seed, Role role) {
    if (seed == null || !seed.isConfigured()) {
      log.warn("No {} user configured (POS_{}_EMAIL / POS_{}_PASSWORD)", role, role, role);
      return;
    }
    String email = User.normalizeEmail(seed.email());
    String name = seed.name() == null || seed.name().isBlank() ? email : seed.name().trim();
    Instant now = clock.instant();
    users
        .findByEmail(email)
        .ifPresentOrElse(
            user -> {
              boolean samePassword =
                  passwordEncoder.matches(seed.password(), user.getPasswordHash());
              if (!samePassword || !user.getName().equals(name) || user.getRole() != role) {
                String hash =
                    samePassword ? user.getPasswordHash() : passwordEncoder.encode(seed.password());
                user.update(hash, name, role, now);
                log.info("Updated {} user {}", role, email);
              }
            },
            () -> {
              users.save(new User(email, passwordEncoder.encode(seed.password()), name, role, now));
              log.info("Created {} user {}", role, email);
            });
  }
}

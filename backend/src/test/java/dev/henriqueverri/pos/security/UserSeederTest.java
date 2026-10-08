package dev.henriqueverri.pos.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.henriqueverri.pos.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class UserSeederTest extends ApiIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private UserSeeder seeder;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void createsConfiguredUsersWithBcryptHashes() {
    User admin = users.findByEmail(ADMIN_EMAIL).orElseThrow();
    User cashier = users.findByEmail(CASHIER_EMAIL).orElseThrow();

    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(cashier.getRole()).isEqualTo(Role.CASHIER);
    assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain(ADMIN_PASSWORD);
    assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
  }

  @Test
  @Transactional
  void isIdempotentAndSyncsChangedSettings() {
    int before = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
    String email = "seed-" + unique("x") + "@test.pos.example";

    seeder.seed(new SecurityProperties.SeedUser(email, "first-password", "Primeiro"), Role.CASHIER);
    seeder.seed(new SecurityProperties.SeedUser(email, "first-password", "Primeiro"), Role.CASHIER);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class))
        .isEqualTo(before + 1);

    seeder.seed(
        new SecurityProperties.SeedUser(email.toUpperCase(), "second-password", "Segundo"),
        Role.ADMIN);
    User user = users.findByEmail(email).orElseThrow();
    assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    assertThat(user.getName()).isEqualTo("Segundo");
    assertThat(passwordEncoder.matches("second-password", user.getPasswordHash())).isTrue();

    seeder.seed(new SecurityProperties.SeedUser(null, null, null), Role.ADMIN);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class))
        .isEqualTo(before + 1);
  }
}

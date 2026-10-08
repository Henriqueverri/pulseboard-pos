package dev.henriqueverri.pos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SecurityPropertiesTest {

  private static final String STRONG = "0123456789abcdef0123456789abcdef";

  @Configuration
  @EnableConfigurationProperties(SecurityProperties.class)
  static class PropertiesOnly {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

  @Test
  void rejectsSecretShorterThan32Bytes() {
    assertThatThrownBy(() -> new SecurityProperties.Jwt(STRONG.substring(1), Duration.ofHours(8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
    assertThatThrownBy(() -> new SecurityProperties.Jwt(null, Duration.ofHours(8)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new SecurityProperties.Jwt(STRONG, Duration.ofHours(8)).secretBytes()).hasSize(32);
  }

  @Test
  void startupFailsWithoutAStrongSecret() {
    runner
        .withPropertyValues("pos.security.jwt.secret=too-short", "pos.security.jwt.ttl=8h")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("pos.security.jwt.secret=", "pos.security.jwt.ttl=8h")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("pos.security.jwt.secret=" + STRONG, "pos.security.jwt.ttl=8h")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(SecurityProperties.class).jwt().ttl())
                  .isEqualTo(Duration.ofHours(8));
            });
  }

  @Test
  void seedUserNeedsEmailAndPassword() {
    assertThat(new SecurityProperties.SeedUser("a@b.example", "secret", null).isConfigured())
        .isTrue();
    assertThat(new SecurityProperties.SeedUser("a@b.example", "", null).isConfigured()).isFalse();
    assertThat(new SecurityProperties.SeedUser(null, "secret", null).isConfigured()).isFalse();
  }
}

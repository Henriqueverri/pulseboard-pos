package dev.henriqueverri.pos.integration;

import dev.henriqueverri.pos.integration.outbox.OutboxProperties;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PulseBoardProperties.class, OutboxProperties.class})
public class IntegrationConfig {

  private static final Logger log = LoggerFactory.getLogger(IntegrationConfig.class);

  @Bean
  ApplicationRunner logIntegrationState(PulseBoardProperties pulseBoard) {
    return args -> {
      if (pulseBoard.isActive()) {
        log.info("PulseBoard integration active: {}", pulseBoard.apiUrl());
      } else {
        log.warn(
            "PulseBoard integration off ({}): sales are kept as PENDING outbox events until it is"
                + " enabled",
            pulseBoard.enabled() ? "PULSEBOARD_API_KEY is empty" : "disabled");
      }
    };
  }

  /** Tests turn the scheduler off and run the worker explicitly. */
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "pos.outbox.scheduler-enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class Scheduling {}
}

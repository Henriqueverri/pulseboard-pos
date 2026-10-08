package dev.henriqueverri.pos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.henriqueverri.pos.integration.outbox.IntegrationPause;
import dev.henriqueverri.pos.integration.pulseboard.PulseBoardProperties;
import dev.henriqueverri.pos.security.Role;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** {@code /actuator/health}: public status only; the integration details for ADMIN, no secrets. */
class IntegrationHealthActuatorTest extends ApiIntegrationTest {

  @Autowired private IntegrationPause pause;
  @Autowired private PulseBoardProperties pulseBoard;
  @Autowired private Clock clock;

  @AfterEach
  void unpause() {
    pause.clear();
  }

  @Test
  void anonymousCallersOnlySeeTheStatus() throws Exception {
    String body =
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("pulseBoard").doesNotContain("pb_");
  }

  @Test
  void cashiersDoNotSeeTheDetails() throws Exception {
    mvc.perform(
            get("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(Role.CASHIER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void adminsSeeTheConfiguredIntegrationWithoutTheKey() throws Exception {
    String body =
        getJson("/actuator/health")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db.status").value("UP"))
            .andExpect(jsonPath("$.components.pulseBoard.status").value("UP"))
            .andExpect(jsonPath("$.components.pulseBoard.details.state").value("ACTIVE"))
            .andExpect(jsonPath("$.components.pulseBoard.details.enabled").value(true))
            .andExpect(jsonPath("$.components.pulseBoard.details.keyPrefix").value("testprefix00"))
            .andExpect(jsonPath("$.components.pulseBoard.details.pending").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String key = pulseBoard.apiKey();
    assertThat(body)
        .doesNotContain(key)
        .doesNotContain(key.substring(key.lastIndexOf('_') + 1))
        .doesNotContain("test-only-secret");
  }

  /** A paused integration is visible, but the application stays UP: the POS keeps selling. */
  @Test
  void aPausedIntegrationIsReportedWithoutTakingTheApplicationDown() throws Exception {
    pause.pause(clock.instant());

    getJson("/actuator/health")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components.pulseBoard.details.state").value("PAUSED"))
        .andExpect(jsonPath("$.components.pulseBoard.details.pausedUntil").isString());
  }
}

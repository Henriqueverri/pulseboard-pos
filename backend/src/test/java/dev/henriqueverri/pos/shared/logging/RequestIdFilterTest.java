package dev.henriqueverri.pos.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void reusesAWellFormedRequestIdAndKeepsItInTheMdcDuringTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    request.addHeader("X-Request-Id", "client-req.0001");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> inside = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            inside.set(MDC.get("request_id"));
          }
        });

    assertThat(response.getHeader("X-Request-Id")).isEqualTo("client-req.0001");
    assertThat(inside.get()).isEqualTo("client-req.0001");
    assertThat(MDC.get("request_id")).isNull();
  }

  @Test
  void generatesAnIdWhenTheRequestHasNone() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());

    assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
    assertThat(MDC.get("request_id")).isNull();
  }

  /** Too short, too long, or characters that could forge log lines: replaced, never echoed. */
  @ParameterizedTest
  @ValueSource(strings = {"short", "has space-123", "line\nbreak-1234", "", "é-acentuado-123"})
  void replacesAMalformedRequestId(String received) {
    String resolved = RequestIdFilter.resolve(received);

    assertThat(resolved).isNotEqualTo(received);
    assertThat(UUID.fromString(resolved)).isNotNull();
  }

  @Test
  void rejectsAnIdLongerThan64Characters() {
    assertThat(RequestIdFilter.resolve("a".repeat(64))).isEqualTo("a".repeat(64));
    assertThat(RequestIdFilter.resolve("a".repeat(65))).isNotEqualTo("a".repeat(65));
  }

  @Test
  void clearsTheMdcEvenWhenTheRequestFails() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    request.addHeader("X-Request-Id", "failing-request-1");

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> {
                      throw new ServletException("boom");
                    }))
        .isInstanceOf(ServletException.class);
    assertThat(MDC.get("request_id")).isNull();
  }
}

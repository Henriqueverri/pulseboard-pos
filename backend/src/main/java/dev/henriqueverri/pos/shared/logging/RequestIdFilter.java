package dev.henriqueverri.pos.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates every log line of an HTTP request. The caller's {@code X-Request-Id} is reused when it
 * is well formed (same rule as PulseBoard: 8–64 chars of {@code A-Za-z0-9._-}); otherwise a UUID is
 * generated. The id is returned in the response and kept in the MDC as {@code request_id} for the
 * duration of the request only. Runs before Spring Security, so 401/403 responses carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "request_id";

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = resolve(request.getHeader(HEADER));
    response.setHeader(HEADER, requestId);
    try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)) {
      chain.doFilter(request, response);
    }
  }

  /** A malformed value is replaced rather than echoed, so it can never inject into the logs. */
  static String resolve(String received) {
    return received != null && VALID.matcher(received).matches()
        ? received
        : UUID.randomUUID().toString();
  }
}

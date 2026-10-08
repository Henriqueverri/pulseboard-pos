package dev.henriqueverri.pos.integration.pulseboard;

/** What happened to one request to PulseBoard: an HTTP response, or no response at all. */
public sealed interface DeliveryResult {

  /**
   * PulseBoard answered. {@code requestId} is the {@code X-Request-Id} of the response; {@code
   * retryAfter} is the raw {@code Retry-After} header, if any.
   */
  record Response(int status, String body, String requestId, boolean replayed, String retryAfter)
      implements DeliveryResult {}

  /** No response: {@code code} is {@code TIMEOUT} or {@code NETWORK}; the outcome is unknown. */
  record NoResponse(String code, String message) implements DeliveryResult {}
}

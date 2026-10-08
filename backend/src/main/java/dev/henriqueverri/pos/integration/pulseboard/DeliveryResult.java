package dev.henriqueverri.pos.integration.pulseboard;

/** What happened to one request to PulseBoard: an HTTP response, or no response at all. */
public sealed interface DeliveryResult {

  /** PulseBoard answered. {@code requestId} is the {@code X-Request-Id} of the response. */
  record Response(int status, String body, String requestId, boolean replayed)
      implements DeliveryResult {}

  /** No response: {@code code} is {@code TIMEOUT} or {@code NETWORK}; the outcome is unknown. */
  record NoResponse(String code, String message) implements DeliveryResult {}
}

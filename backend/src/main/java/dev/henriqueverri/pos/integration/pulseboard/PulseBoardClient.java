package dev.henriqueverri.pos.integration.pulseboard;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * HTTP client of the PulseBoard ingestion API ({@code docs/integration.md} in PulseBoard). Every
 * response, success or error, is returned as data for the caller to classify; nothing here retries.
 * The API key only travels in the {@code Authorization} header and never appears in results or
 * messages. Log lines get {@code request_id} and {@code event_id} from the caller's MDC (the
 * worker).
 */
@Component
public class PulseBoardClient {

  private static final Logger log = LoggerFactory.getLogger(PulseBoardClient.class);

  static final String REQUEST_ID = "X-Request-Id";
  static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

  private final RestClient rest;

  public PulseBoardClient(PulseBoardProperties properties, RestClient.Builder builder) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    // Encodes only what is illegal in each URI component: "pos:<uuid>" keeps its ':' in the path.
    DefaultUriBuilderFactory uriFactory =
        new DefaultUriBuilderFactory(properties.apiUrl().toString());
    uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
    this.rest =
        builder
            .uriBuilderFactory(uriFactory)
            .requestFactory(requestFactory)
            .defaultHeaders(
                headers -> {
                  headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                  if (!properties.apiKey().isBlank()) {
                    headers.setBearerAuth(properties.apiKey());
                  }
                })
            .build();
  }

  /** {@code POST /ingest/transactions}. */
  public DeliveryResult createTransaction(String payload, String requestId) {
    return send("create_transaction", rest.post().uri("/ingest/transactions"), payload, requestId);
  }

  /** {@code POST /ingest/transactions/{external_id}/status-changes}. */
  public DeliveryResult changeStatus(String externalId, String payload, String requestId) {
    return send(
        "change_status",
        rest.post().uri("/ingest/transactions/{externalId}/status-changes", externalId),
        payload,
        requestId);
  }

  /** {@code GET /ingest/transactions/{external_id}}: the current state, for reconciliation. */
  public DeliveryResult getTransaction(String externalId, String requestId) {
    return exchange(
        "get_transaction",
        rest.get()
            .uri("/ingest/transactions/{externalId}", externalId)
            .header(REQUEST_ID, requestId));
  }

  private DeliveryResult send(
      String operation, RestClient.RequestBodySpec request, String payload, String requestId) {
    return exchange(
        operation,
        request
            .header(REQUEST_ID, requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload));
  }

  /**
   * Logs one line per call with the status (or failure kind) and the duration. Headers, bodies and
   * exception messages are left out: they may carry the key, customer data or the URL.
   */
  private static DeliveryResult exchange(
      String operation, RestClient.RequestHeadersSpec<?> request) {
    long started = System.nanoTime();
    try {
      DeliveryResult.Response result =
          request.exchange(
              (req, response) -> {
                HttpHeaders headers = response.getHeaders();
                return new DeliveryResult.Response(
                    response.getStatusCode().value(),
                    readBody(response.getBody()),
                    headers.getFirst(REQUEST_ID),
                    "true".equalsIgnoreCase(headers.getFirst(IDEMPOTENT_REPLAYED)),
                    headers.getFirst(HttpHeaders.RETRY_AFTER));
              });
      log.atInfo()
          .addKeyValue("target", "pulseboard")
          .addKeyValue("operation", operation)
          .addKeyValue("http_status", result.status())
          .addKeyValue("replayed", result.replayed())
          .addKeyValue("duration_ms", elapsedMillis(started))
          .addKeyValue("response_request_id", result.requestId())
          .log("PulseBoard request completed");
      return result;
    } catch (ResourceAccessException e) {
      DeliveryResult.NoResponse result =
          isTimeout(e)
              ? new DeliveryResult.NoResponse(
                  "TIMEOUT", "O PulseBoard não respondeu dentro do prazo.")
              : new DeliveryResult.NoResponse(
                  "NETWORK", "Não foi possível conectar ao PulseBoard.");
      log.atWarn()
          .addKeyValue("target", "pulseboard")
          .addKeyValue("operation", operation)
          .addKeyValue("error_code", result.code())
          .addKeyValue("error_type", rootCause(e).getClass().getSimpleName())
          .addKeyValue("duration_ms", elapsedMillis(started))
          .log("PulseBoard request got no response");
      return result;
    }
  }

  private static long elapsedMillis(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }

  private static Throwable rootCause(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause;
  }

  private static String readBody(InputStream body) throws IOException {
    return body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
  }

  private static boolean isTimeout(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }
}

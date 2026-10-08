package dev.henriqueverri.pos.integration.pulseboard;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * messages.
 */
@Component
public class PulseBoardClient {

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
    return send(rest.post().uri("/ingest/transactions"), payload, requestId);
  }

  /** {@code POST /ingest/transactions/{external_id}/status-changes}. */
  public DeliveryResult changeStatus(String externalId, String payload, String requestId) {
    return send(
        rest.post().uri("/ingest/transactions/{externalId}/status-changes", externalId),
        payload,
        requestId);
  }

  /** {@code GET /ingest/transactions/{external_id}}: the current state, for reconciliation. */
  public DeliveryResult getTransaction(String externalId, String requestId) {
    return exchange(
        rest.get()
            .uri("/ingest/transactions/{externalId}", externalId)
            .header(REQUEST_ID, requestId));
  }

  private DeliveryResult send(
      RestClient.RequestBodySpec request, String payload, String requestId) {
    return exchange(
        request
            .header(REQUEST_ID, requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload));
  }

  private static DeliveryResult exchange(RestClient.RequestHeadersSpec<?> request) {
    try {
      return request.exchange(
          (req, response) -> {
            HttpHeaders headers = response.getHeaders();
            return new DeliveryResult.Response(
                response.getStatusCode().value(),
                readBody(response.getBody()),
                headers.getFirst(REQUEST_ID),
                "true".equalsIgnoreCase(headers.getFirst(IDEMPOTENT_REPLAYED)),
                headers.getFirst(HttpHeaders.RETRY_AFTER));
          });
    } catch (ResourceAccessException e) {
      return isTimeout(e)
          ? new DeliveryResult.NoResponse("TIMEOUT", "O PulseBoard não respondeu dentro do prazo.")
          : new DeliveryResult.NoResponse("NETWORK", "Não foi possível conectar ao PulseBoard.");
    }
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

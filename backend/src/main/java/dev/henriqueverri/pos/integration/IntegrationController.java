package dev.henriqueverri.pos.integration;

import dev.henriqueverri.pos.integration.dto.IntegrationEventResponse;
import dev.henriqueverri.pos.integration.dto.IntegrationHealthResponse;
import dev.henriqueverri.pos.integration.dto.RetryConfigurationFailuresResponse;
import dev.henriqueverri.pos.integration.outbox.OutboxStatus;
import dev.henriqueverri.pos.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration")
@Tag(name = "Integração", description = "Outbox de entrega ao PulseBoard (somente ADMIN)")
public class IntegrationController {

  private final IntegrationService service;

  public IntegrationController(IntegrationService service) {
    this.service = service;
  }

  @GetMapping("/events")
  @Operation(summary = "Lista eventos da outbox (mais recentes primeiro)")
  public PageResponse<IntegrationEventResponse> list(
      @RequestParam(required = false) OutboxStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/events/{id}")
  @Operation(summary = "Detalha um evento, com o payload enviado ao PulseBoard")
  public IntegrationEventResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/events/{id}/retry")
  @Operation(summary = "Reprocessa um evento FAILED (volta para PENDING)")
  @ApiResponse(
      responseCode = "409",
      description = "integration_event_not_failed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public IntegrationEventResponse retry(@PathVariable UUID id) {
    return service.retry(id);
  }

  @PostMapping("/events/retry-configuration-failures")
  @Operation(summary = "Reprocessa as falhas de configuração (401) e retoma a integração pausada")
  public RetryConfigurationFailuresResponse retryConfigurationFailures() {
    return service.retryConfigurationFailures();
  }

  @GetMapping("/health")
  @Operation(summary = "Estado da integração: ligada, pausada, destino, backlog")
  public IntegrationHealthResponse health() {
    return service.health();
  }
}

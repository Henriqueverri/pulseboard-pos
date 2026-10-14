package dev.henriqueverri.pos.inventory;

import dev.henriqueverri.pos.inventory.dto.StockMovementRequest;
import dev.henriqueverri.pos.inventory.dto.StockMovementResponse;
import dev.henriqueverri.pos.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productId}/stock-movements")
@Tag(name = "Estoque", description = "Saldo por movimentação: venda, estorno, entrada e ajuste")
public class InventoryController {

  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Histórico de movimentações do produto (mais recentes primeiro)")
  public PageResponse<StockMovementResponse> list(
      @PathVariable UUID productId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.history(productId, page, size);
  }

  @PostMapping
  @Operation(summary = "Registra uma entrada ou um ajuste de estoque")
  @ApiResponse(responseCode = "201", description = "Movimentação registrada")
  @ApiResponse(
      responseCode = "409",
      description = "insufficient_stock: o ajuste deixaria o saldo negativo",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "stock_not_tracked: o produto não controla estoque",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<StockMovementResponse> create(
      @PathVariable UUID productId, @Valid @RequestBody StockMovementRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.record(productId, request));
  }
}

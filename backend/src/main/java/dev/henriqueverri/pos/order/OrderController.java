package dev.henriqueverri.pos.order;

import dev.henriqueverri.pos.order.dto.CreateOrderRequest;
import dev.henriqueverri.pos.order.dto.OrderResponse;
import dev.henriqueverri.pos.order.dto.OrderSummaryResponse;
import dev.henriqueverri.pos.order.dto.PayOrderRequest;
import dev.henriqueverri.pos.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/orders")
@Tag(name = "Pedidos", description = "Pedidos e ciclo de vida PENDING → PAID → REFUNDED")
public class OrderController {

  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Lista pedidos (mais recentes primeiro)")
  public PageResponse<OrderSummaryResponse> list(
      @RequestParam(required = false) OrderStatus status,
      @Parameter(description = "Criados a partir deste instante (ISO 8601 com offset)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @Parameter(description = "Criados antes deste instante (ISO 8601 com offset)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(required = false) Long number,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    OrderService.Filter filter =
        new OrderService.Filter(
            status,
            from == null ? null : from.toInstant(),
            to == null ? null : to.toInstant(),
            number);
    return service.search(filter, page, size);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Detalha um pedido com seus itens e eventos de integração")
  public OrderResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Cria um pedido PENDING; preços e total são calculados pelo servidor")
  @ApiResponse(responseCode = "201", description = "Pedido criado")
  @ApiResponse(
      responseCode = "422",
      description =
          "Cliente ou produto inexistente, produto inativo, produto repetido ou total acima do"
              + " máximo",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    OrderResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
  }

  @PostMapping("/{id}/pay")
  @Operation(summary = "Paga um pedido PENDING")
  @ApiResponse(responseCode = "200", description = "Pedido pago")
  @ApiResponse(
      responseCode = "409",
      description = "invalid_order_transition ou concurrent_modification",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public OrderResponse pay(@PathVariable UUID id, @Valid @RequestBody PayOrderRequest request) {
    return service.pay(id, request.paymentMethod());
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancela um pedido PENDING")
  @ApiResponse(responseCode = "200", description = "Pedido cancelado")
  @ApiResponse(
      responseCode = "409",
      description = "invalid_order_transition ou concurrent_modification",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public OrderResponse cancel(@PathVariable UUID id) {
    return service.cancel(id);
  }

  @PostMapping("/{id}/refund")
  @Operation(summary = "Estorna um pedido PAID")
  @ApiResponse(responseCode = "200", description = "Pedido estornado")
  @ApiResponse(
      responseCode = "409",
      description = "invalid_order_transition ou concurrent_modification",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public OrderResponse refund(@PathVariable UUID id) {
    return service.refund(id);
  }
}

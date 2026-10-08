package dev.henriqueverri.pos.customer;

import dev.henriqueverri.pos.customer.dto.CustomerRequest;
import dev.henriqueverri.pos.customer.dto.CustomerResponse;
import dev.henriqueverri.pos.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Clientes", description = "Clientes locais do POS")
public class CustomerController {

  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Lista clientes (busca por nome ou e-mail)")
  public PageResponse<CustomerResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.search(q, page, size);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Detalha um cliente")
  public CustomerResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Cria um cliente")
  public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
    CustomerResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza um cliente")
  public CustomerResponse update(
      @PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
    return service.update(id, request);
  }
}

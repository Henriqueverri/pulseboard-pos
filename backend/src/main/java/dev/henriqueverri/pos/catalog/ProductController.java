package dev.henriqueverri.pos.catalog;

import dev.henriqueverri.pos.catalog.dto.ProductRequest;
import dev.henriqueverri.pos.catalog.dto.ProductResponse;
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
@RequestMapping("/api/products")
@Tag(name = "Produtos", description = "Catálogo do POS")
public class ProductController {

  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Lista produtos (busca por nome ou SKU)")
  public PageResponse<ProductResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.search(q, active, page, size);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Detalha um produto")
  public ProductResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Cria um produto")
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    ProductResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza um produto (inclui ativar/desativar)")
  public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
    return service.update(id, request);
  }
}

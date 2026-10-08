package dev.henriqueverri.pos.catalog;

import dev.henriqueverri.pos.catalog.dto.ProductRequest;
import dev.henriqueverri.pos.catalog.dto.ProductResponse;
import dev.henriqueverri.pos.shared.PageResponse;
import dev.henriqueverri.pos.shared.Search;
import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private static final Sort SORT = Sort.by("sku");

  private final ProductRepository products;
  private final Clock clock;

  public ProductService(ProductRepository products, Clock clock) {
    this.products = products;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> search(String q, Boolean active, int page, int size) {
    Specification<Product> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (Search.hasTerm(q)) {
            String pattern = Search.containsPattern(q);
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, Search.ESCAPE),
                    cb.like(cb.lower(root.get("sku")), pattern, Search.ESCAPE)));
          }
          if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    return PageResponse.from(
        products.findAll(spec, PageRequest.of(page, size, SORT)), ProductResponse::from);
  }

  @Transactional(readOnly = true)
  public ProductResponse get(UUID id) {
    return ProductResponse.from(find(id));
  }

  @Transactional
  public ProductResponse create(ProductRequest request) {
    String sku = request.sku().trim();
    if (products.existsBySku(sku)) {
      throw duplicateSku(sku);
    }
    Product product =
        new Product(sku, request.name().trim(), request.price(), request.active(), clock.instant());
    return ProductResponse.from(saveUnique(product, sku));
  }

  @Transactional
  public ProductResponse update(UUID id, ProductRequest request) {
    Product product = find(id);
    String sku = request.sku().trim();
    if (products.existsBySkuAndIdNot(sku, id)) {
      throw duplicateSku(sku);
    }
    product.update(sku, request.name().trim(), request.price(), request.active(), clock.instant());
    return ProductResponse.from(saveUnique(product, sku));
  }

  private Product find(UUID id) {
    return products.findById(id).orElseThrow(() -> ApiException.notFound("Produto"));
  }

  /** The unique index is the real guard; the pre-check above only gives the common case a 409. */
  private Product saveUnique(Product product, String sku) {
    try {
      return products.saveAndFlush(product);
    } catch (DataIntegrityViolationException e) {
      throw duplicateSku(sku);
    }
  }

  private static ApiException duplicateSku(String sku) {
    return ApiException.conflict(
        ErrorCodes.DUPLICATE_SKU, "Já existe um produto com o SKU " + sku + ".");
  }
}

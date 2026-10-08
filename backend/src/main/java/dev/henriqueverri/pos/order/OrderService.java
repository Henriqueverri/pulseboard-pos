package dev.henriqueverri.pos.order;

import dev.henriqueverri.pos.catalog.Product;
import dev.henriqueverri.pos.catalog.ProductRepository;
import dev.henriqueverri.pos.customer.Customer;
import dev.henriqueverri.pos.customer.CustomerRepository;
import dev.henriqueverri.pos.order.dto.CreateOrderRequest;
import dev.henriqueverri.pos.order.dto.OrderItemRequest;
import dev.henriqueverri.pos.order.dto.OrderResponse;
import dev.henriqueverri.pos.order.dto.OrderSummaryResponse;
import dev.henriqueverri.pos.shared.PageResponse;
import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import dev.henriqueverri.pos.shared.money.Money;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Sort SORT = Sort.by(Sort.Direction.DESC, "number");

  private final OrderRepository orders;
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final Clock clock;
  private final String currency;

  public OrderService(
      OrderRepository orders,
      CustomerRepository customers,
      ProductRepository products,
      Clock clock,
      @Value("${pos.currency}") String currency) {
    this.orders = orders;
    this.customers = customers;
    this.products = products;
    this.clock = clock;
    this.currency = currency;
  }

  public record Filter(OrderStatus status, Instant from, Instant to, Long number) {}

  @Transactional(readOnly = true)
  public PageResponse<OrderSummaryResponse> search(Filter filter, int page, int size) {
    Specification<Order> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (filter.status() != null) {
            predicates.add(cb.equal(root.get("status"), filter.status()));
          }
          if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
          }
          if (filter.to() != null) {
            predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
          }
          if (filter.number() != null) {
            predicates.add(cb.equal(root.get("number"), filter.number()));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    return PageResponse.from(
        orders.findAll(spec, PageRequest.of(page, size, SORT)), OrderSummaryResponse::from);
  }

  @Transactional(readOnly = true)
  public OrderResponse get(UUID id) {
    return OrderResponse.from(
        orders.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Pedido")));
  }

  @Transactional
  public OrderResponse create(CreateOrderRequest request) {
    Customer customer =
        customers
            .findById(request.customerId())
            .orElseThrow(
                () ->
                    ApiException.unprocessable(
                        ErrorCodes.UNKNOWN_CUSTOMER, "Cliente informado não existe."));

    List<UUID> productIds = request.items().stream().map(OrderItemRequest::productId).toList();
    if (productIds.stream().distinct().count() != productIds.size()) {
      throw ApiException.unprocessable(
          ErrorCodes.DUPLICATE_ORDER_ITEM,
          "Cada produto aparece uma única vez no pedido; ajuste a quantidade.");
    }
    Map<UUID, Product> catalog =
        products.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

    List<Order.Line> lines = new ArrayList<>();
    for (OrderItemRequest item : request.items()) {
      Product product = catalog.get(item.productId());
      if (product == null) {
        throw ApiException.unprocessable(
            ErrorCodes.UNKNOWN_PRODUCT, "Produto " + item.productId() + " não existe.");
      }
      if (!product.isActive()) {
        throw ApiException.unprocessable(
            ErrorCodes.PRODUCT_INACTIVE,
            "Produto " + product.getSku() + " está inativo e não pode ser vendido.");
      }
      lines.add(new Order.Line(product, item.quantity()));
    }

    Order order = Order.place(orders.nextNumber(), customer, currency, lines, clock.instant());
    if (Money.exceedsMax(order.getTotal())) {
      throw ApiException.unprocessable(
          ErrorCodes.ORDER_TOTAL_TOO_LARGE, "O total do pedido excede o valor máximo permitido.");
    }
    return OrderResponse.from(orders.save(order));
  }

  @Transactional
  public OrderResponse pay(UUID id, PaymentMethod method) {
    Order order = find(id);
    order.pay(method, clock.instant());
    return OrderResponse.from(order);
  }

  @Transactional
  public OrderResponse cancel(UUID id) {
    Order order = find(id);
    order.cancel(clock.instant());
    return OrderResponse.from(order);
  }

  @Transactional
  public OrderResponse refund(UUID id) {
    Order order = find(id);
    order.refund(clock.instant());
    return OrderResponse.from(order);
  }

  private Order find(UUID id) {
    return orders.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Pedido"));
  }
}

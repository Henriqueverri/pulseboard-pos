package dev.henriqueverri.pos.customer;

import dev.henriqueverri.pos.customer.dto.CustomerRequest;
import dev.henriqueverri.pos.customer.dto.CustomerResponse;
import dev.henriqueverri.pos.shared.PageResponse;
import dev.henriqueverri.pos.shared.Search;
import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final Sort SORT = Sort.by("name").and(Sort.by("email"));

  private final CustomerRepository customers;
  private final Clock clock;

  public CustomerService(CustomerRepository customers, Clock clock) {
    this.customers = customers;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> search(String q, int page, int size) {
    Specification<Customer> spec =
        (root, query, cb) -> {
          if (!Search.hasTerm(q)) {
            return cb.conjunction();
          }
          String pattern = Search.containsPattern(q);
          return cb.or(
              cb.like(cb.lower(root.get("name")), pattern, Search.ESCAPE),
              cb.like(root.get("email"), pattern, Search.ESCAPE));
        };
    return PageResponse.from(
        customers.findAll(spec, PageRequest.of(page, size, SORT)), CustomerResponse::from);
  }

  @Transactional(readOnly = true)
  public CustomerResponse get(UUID id) {
    return CustomerResponse.from(find(id));
  }

  @Transactional
  public CustomerResponse create(CustomerRequest request) {
    String email = Customer.normalizeEmail(request.email());
    if (customers.existsByEmail(email)) {
      throw duplicateEmail(email);
    }
    Customer customer = new Customer(request.name(), email, request.document(), clock.instant());
    return CustomerResponse.from(saveUnique(customer, email));
  }

  @Transactional
  public CustomerResponse update(UUID id, CustomerRequest request) {
    Customer customer = find(id);
    String email = Customer.normalizeEmail(request.email());
    if (customers.existsByEmailAndIdNot(email, id)) {
      throw duplicateEmail(email);
    }
    customer.update(request.name(), email, request.document(), clock.instant());
    return CustomerResponse.from(saveUnique(customer, email));
  }

  private Customer find(UUID id) {
    return customers.findById(id).orElseThrow(() -> ApiException.notFound("Cliente"));
  }

  private Customer saveUnique(Customer customer, String email) {
    try {
      return customers.saveAndFlush(customer);
    } catch (DataIntegrityViolationException e) {
      throw duplicateEmail(email);
    }
  }

  private static ApiException duplicateEmail(String email) {
    return ApiException.conflict(
        ErrorCodes.DUPLICATE_EMAIL, "Já existe um cliente com o e-mail " + email + ".");
  }
}

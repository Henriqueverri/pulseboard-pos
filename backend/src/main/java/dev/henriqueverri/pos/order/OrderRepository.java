package dev.henriqueverri.pos.order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository
    extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

  @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
  long nextNumber();

  @EntityGraph(attributePaths = {"customer", "items"})
  Optional<Order> findWithDetailsById(UUID id);

  @Override
  @EntityGraph(attributePaths = "customer")
  Page<Order> findAll(Specification<Order> spec, Pageable pageable);
}

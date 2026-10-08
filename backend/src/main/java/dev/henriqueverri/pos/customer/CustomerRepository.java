package dev.henriqueverri.pos.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository
    extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

  boolean existsByEmail(String email);

  boolean existsByEmailAndIdNot(String email, UUID id);
}

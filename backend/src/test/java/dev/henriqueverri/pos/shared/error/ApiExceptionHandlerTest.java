package dev.henriqueverri.pos.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void optimisticLockingFailureBecomes409ConcurrentModification() {
    ResponseEntity<Object> response =
        handler.handleOptimisticLocking(
            new ObjectOptimisticLockingFailureException("PosOrder", "id"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem.getProperties()).containsEntry("code", "concurrent_modification");
    assertThat(problem.getDetail()).doesNotContain("PosOrder");
  }

  @Test
  void unexpectedErrorsHideInternals() {
    ResponseEntity<Object> response =
        handler.handleUnexpected(new IllegalStateException("SELECT secret FROM table"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem.getProperties()).containsEntry("code", "internal_error");
    assertThat(problem.getDetail()).doesNotContain("secret");
  }
}

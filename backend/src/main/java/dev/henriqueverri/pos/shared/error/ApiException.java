package dev.henriqueverri.pos.shared.error;

import org.springframework.http.HttpStatus;

/**
 * A business error with an HTTP status and a stable {@code code}, rendered as ProblemDetail by
 * {@link ApiExceptionHandler}.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public static ApiException notFound(String resource) {
    return new ApiException(
        HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, resource + " não encontrado.");
  }

  public static ApiException conflict(String code, String detail) {
    return new ApiException(HttpStatus.CONFLICT, code, detail);
  }

  public static ApiException unprocessable(String code, String detail) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}

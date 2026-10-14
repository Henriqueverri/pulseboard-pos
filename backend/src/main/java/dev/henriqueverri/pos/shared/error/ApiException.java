package dev.henriqueverri.pos.shared.error;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

/**
 * A business error with an HTTP status and a stable {@code code}, rendered as ProblemDetail by
 * {@link ApiExceptionHandler}. {@code errors}, when present, follows the same shape as validation
 * errors: messages keyed by field (or by SKU, for stock).
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  @Nullable private final Map<String, List<String>> errors;

  public ApiException(HttpStatus status, String code, String detail) {
    this(status, code, detail, null);
  }

  public ApiException(
      HttpStatus status, String code, String detail, @Nullable Map<String, List<String>> errors) {
    super(detail);
    this.status = status;
    this.code = code;
    this.errors = errors;
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

  /** Same response as a Bean Validation failure, for rules that depend on more than one field. */
  public static ApiException invalidField(String field, String message) {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Os dados enviados são inválidos.",
        Map.of(field, List.of(message)));
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  @Nullable
  public Map<String, List<String>> getErrors() {
    return errors;
  }
}

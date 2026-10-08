package dev.henriqueverri.pos.shared.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single error format of the API: ProblemDetail (RFC 9457) with a stable {@code code} and, for
 * validation, {@code errors} keyed by field. Stack traces and internal messages never reach the
 * client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final String INVALID_VALUE = "valor inválido";

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApiException(ApiException ex) {
    return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<Object> handleOptimisticLocking(OptimisticLockingFailureException ex) {
    return problem(
        HttpStatus.CONFLICT,
        ErrorCodes.CONCURRENT_MODIFICATION,
        "O registro foi alterado por outra operação. Recarregue e tente novamente.",
        null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCodes.INTERNAL_ERROR,
        "Erro inesperado. Tente novamente.",
        null);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, List<String>> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      add(errors, error.getField(), error.getDefaultMessage());
    }
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(error -> add(errors, error.getObjectName(), error.getDefaultMessage()));
    return validationProblem(errors);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, List<String>> errors = new LinkedHashMap<>();
    ex.getParameterValidationResults()
        .forEach(
            result ->
                result
                    .getResolvableErrors()
                    .forEach(
                        error ->
                            add(
                                errors,
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())));
    return validationProblem(errors);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      Map<String, List<String>> errors = new LinkedHashMap<>();
      add(errors, jsonPath(mapping.getPath()), INVALID_VALUE);
      return validationProblem(errors);
    }
    return problem(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.MALFORMED_REQUEST,
        "Corpo da requisição inválido.",
        null);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String name =
        ex instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : ex.getPropertyName();
    Map<String, List<String>> errors = new LinkedHashMap<>();
    add(errors, name == null ? "request" : name, INVALID_VALUE);
    return validationProblem(errors);
  }

  /** Framework errors (404 route, 405, 415...) get a code derived from their status. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (body instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
      HttpStatus status = HttpStatus.resolve(statusCode.value());
      problem.setProperty(
          "code", status == null ? "error" : status.name().toLowerCase(Locale.ROOT));
    }
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }

  private ResponseEntity<Object> validationProblem(Map<String, List<String>> errors) {
    return problem(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Os dados enviados são inválidos.",
        errors);
  }

  static ResponseEntity<Object> problem(
      HttpStatus status, String code, String detail, @Nullable Map<String, List<String>> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    if (errors != null) {
      problem.setProperty("errors", errors);
    }
    return ResponseEntity.status(status).body(problem);
  }

  private static void add(Map<String, List<String>> errors, String field, String message) {
    errors
        .computeIfAbsent(field, key -> new ArrayList<>())
        .add(message == null ? INVALID_VALUE : message);
  }

  private static String jsonPath(List<JsonMappingException.Reference> path) {
    StringBuilder result = new StringBuilder();
    for (JsonMappingException.Reference reference : path) {
      if (reference.getFieldName() != null) {
        if (!result.isEmpty()) {
          result.append('.');
        }
        result.append(reference.getFieldName());
      } else {
        result.append('[').append(reference.getIndex()).append(']');
      }
    }
    return result.toString();
  }
}

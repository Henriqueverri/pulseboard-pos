package dev.henriqueverri.pos.shared.error;

/**
 * Stable values of the ProblemDetail {@code code} property. Clients branch on these, never on the
 * human-readable {@code detail}.
 */
public final class ErrorCodes {

  public static final String VALIDATION_FAILED = "validation_failed";
  public static final String MALFORMED_REQUEST = "malformed_request";
  public static final String NOT_FOUND = "not_found";
  public static final String DUPLICATE_SKU = "duplicate_sku";
  public static final String DUPLICATE_EMAIL = "duplicate_email";
  public static final String UNKNOWN_PRODUCT = "unknown_product";
  public static final String UNKNOWN_CUSTOMER = "unknown_customer";
  public static final String PRODUCT_INACTIVE = "product_inactive";
  public static final String DUPLICATE_ORDER_ITEM = "duplicate_order_item";
  public static final String ORDER_TOTAL_TOO_LARGE = "order_total_too_large";
  public static final String INVALID_ORDER_TRANSITION = "invalid_order_transition";
  public static final String CONCURRENT_MODIFICATION = "concurrent_modification";
  public static final String INTEGRATION_EVENT_NOT_FAILED = "integration_event_not_failed";
  public static final String INTERNAL_ERROR = "internal_error";

  private ErrorCodes() {}
}

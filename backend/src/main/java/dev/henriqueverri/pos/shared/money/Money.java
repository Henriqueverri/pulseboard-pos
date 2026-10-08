package dev.henriqueverri.pos.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Money rules of the POS: {@link BigDecimal} with exactly two decimal places, never floating point.
 *
 * <p>There is no rounding anywhere. Inputs with more than two decimals are rejected (validation and
 * {@link #normalize}), and the only arithmetic is multiplying a two-decimal price by an integer
 * quantity and summing line totals, which is always exact. {@link RoundingMode#UNNECESSARY} turns
 * any accidental precision loss into an error instead of a silent change.
 */
public final class Money {

  public static final int SCALE = 2;

  /** Largest value a {@code NUMERIC(12,2)} column holds. */
  public static final BigDecimal MAX = new BigDecimal("9999999999.99");

  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

  private Money() {}

  public static BigDecimal of(String value) {
    return normalize(new BigDecimal(value));
  }

  /**
   * @throws ArithmeticException if the value has more than two significant decimal places
   */
  public static BigDecimal normalize(BigDecimal value) {
    return value.setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
    return normalize(unitPrice.multiply(BigDecimal.valueOf(quantity)));
  }

  public static BigDecimal sum(Collection<BigDecimal> values) {
    return values.stream().reduce(ZERO, BigDecimal::add);
  }

  public static boolean exceedsMax(BigDecimal value) {
    return value.compareTo(MAX) > 0;
  }

  /** The JSON representation: a plain decimal string with two places, e.g. {@code "409.70"}. */
  public static String format(BigDecimal value) {
    return normalize(value).toPlainString();
  }
}

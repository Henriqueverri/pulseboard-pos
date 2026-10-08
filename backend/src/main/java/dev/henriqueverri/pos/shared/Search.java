package dev.henriqueverri.pos.shared;

import java.util.Locale;

/** Helpers for the case-insensitive "contains" search used by the list endpoints. */
public final class Search {

  public static final char ESCAPE = '\\';

  private Search() {}

  /** Blank terms mean "no filter". */
  public static boolean hasTerm(String term) {
    return term != null && !term.isBlank();
  }

  /** {@code LIKE} pattern for a lower-cased "contains", with {@code %} and {@code _} escaped. */
  public static String containsPattern(String term) {
    String escaped =
        term.trim()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}

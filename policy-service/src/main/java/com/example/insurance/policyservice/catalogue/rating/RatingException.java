package com.example.insurance.policyservice.catalogue.rating;

/**
 * A premium cannot be computed. {@code code} is stable and documented in docs/catalogue-schema.md,
 * so the quote API (M4) can return it in a problem response.
 */
public class RatingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final String UNKNOWN_RATING_MODEL = "UNKNOWN_RATING_MODEL";
  public static final String RATING_CONFIG_INVALID = "RATING_CONFIG_INVALID";
  public static final String AGE_OUTSIDE_RATING_BANDS = "AGE_OUTSIDE_RATING_BANDS";
  public static final String NO_TIER_TABLE_FOR_AGE = "NO_TIER_TABLE_FOR_AGE";
  public static final String TIER_OVERFLOW_NOT_PRICED = "TIER_OVERFLOW_NOT_PRICED";
  public static final String PRINCIPAL_REQUIRED = "PRINCIPAL_REQUIRED";
  public static final String NO_MEMBERS = "NO_MEMBERS";
  public static final String DATE_OF_BIRTH_AFTER_START = "DATE_OF_BIRTH_AFTER_START";
  public static final String MODULE_NOT_FOUND = "MODULE_NOT_FOUND";
  public static final String OPTION_NOT_FOUND = "OPTION_NOT_FOUND";
  public static final String MANDATORY_MODULE_MISSING = "MANDATORY_MODULE_MISSING";

  private final String code;

  public RatingException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}

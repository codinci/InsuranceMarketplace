package com.example.insurance.policyservice.catalogue.api;

/**
 * The catalogue cannot answer: the plan is not available, or the module selection or members do not
 * fit it. {@code code} is stable (docs/catalogue-schema.md).
 */
public class CatalogueApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final String PLAN_NOT_FOUND = "PLAN_NOT_FOUND";

  private final String code;

  public CatalogueApiException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}

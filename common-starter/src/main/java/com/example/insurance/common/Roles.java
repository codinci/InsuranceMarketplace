package com.example.insurance.common;

/** Role names carried in JWTs. Single source of truth (PLAN 4, CLAUDE.md Domain rules). */
public final class Roles {

  public static final String CUSTOMER = "CUSTOMER";
  public static final String ADJUSTER = "ADJUSTER";
  public static final String ADMIN = "ADMIN";
  public static final String SERVICE = "SERVICE";

  private Roles() {
    // Constants only; not instantiable.
  }
}

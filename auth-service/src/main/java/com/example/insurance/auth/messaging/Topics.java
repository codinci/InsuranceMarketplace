package com.example.insurance.auth.messaging;

/** Kafka topics this service writes (PLAN 10, docs/events.md). */
public final class Topics {

  public static final String USER_EVENTS = "user.events";

  private Topics() {
    // Constants only; not instantiable.
  }
}

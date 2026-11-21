package com.example.insurance.messaging;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The consumer side of ADR-013: an event is applied only if its id is not recorded yet. Call both
 * methods inside the same transaction as the business change, so the record and the change commit
 * together.
 */
public class Inbox {

  private static final Logger LOG = LoggerFactory.getLogger(Inbox.class);

  private final ProcessedEventRepository processed;
  private final Clock clock;
  private final String consumerName;

  public Inbox(ProcessedEventRepository processed, Clock clock, String consumerName) {
    this.processed = processed;
    this.clock = clock;
    this.consumerName = consumerName;
  }

  /** True if this consumer has already applied the event; the caller then does nothing. */
  public boolean alreadyApplied(UUID eventId) {
    if (processed.existsById(eventId)) {
      LOG.debug("Event {} already applied; skipped.", eventId);
      return true;
    }
    return false;
  }

  /**
   * Records the event as applied. A second record of the same id fails on the primary key, which
   * rolls back a racing duplicate.
   */
  public void markApplied(UUID eventId) {
    processed.save(new ProcessedEvent(eventId, consumerName, clock.instant()));
  }

  public String consumerName() {
    return consumerName;
  }
}

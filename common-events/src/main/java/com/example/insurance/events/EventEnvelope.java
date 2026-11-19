package com.example.insurance.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * The shared event envelope (PLAN 10, ADR-005). Every event on every topic has this shape.
 *
 * <p>Additive changes only: new fields may be added, existing ones are never renamed or removed.
 * Unknown fields are ignored so older consumers keep working when a producer adds a field.
 *
 * @param eventId unique id of this event; consumers use it as the inbox key
 * @param type event name, for example {@code PolicyActivated}; selects the payload class
 * @param version schema version of the payload for this {@code type}
 * @param occurredAt when the business fact happened, in UTC
 * @param aggregateId id of the aggregate the event is about; the Kafka message key
 * @param insurerId insurer the event belongs to, or null for platform-level events
 * @param correlationId request correlation id, carried from the originating HTTP request
 * @param payload event-specific data, typed by the caller
 * @param <T> payload type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
    UUID eventId,
    String type,
    int version,
    Instant occurredAt,
    String aggregateId,
    String insurerId,
    String correlationId,
    T payload) {}

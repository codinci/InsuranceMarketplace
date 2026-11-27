-- Transactional outbox (ADR-013). payload holds the whole envelope as JSON.
CREATE TABLE outbox_events (
    event_id     UUID PRIMARY KEY,
    topic        VARCHAR(60) NOT NULL,
    message_key  VARCHAR(80) NOT NULL,
    event_type   VARCHAR(60) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX outbox_events_unsent ON outbox_events (created_at) WHERE published_at IS NULL;

-- Inbox (ADR-013). auth-service consumes nothing yet, but the shared messaging module maps this table.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    consumer     VARCHAR(60) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One row per notification sent. event_id is unique: an event produces at most one notification,
-- even if the inbox check were ever bypassed (ADR-013, second layer).
CREATE TABLE notification_log (
    id                UUID PRIMARY KEY,
    event_id          UUID NOT NULL UNIQUE,
    type              VARCHAR(60) NOT NULL,
    recipient_user_id UUID NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    subject           VARCHAR(200) NOT NULL,
    body              VARCHAR(1000) NOT NULL,
    sent_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX notification_log_recipient ON notification_log (recipient_user_id, sent_at);

-- Outbox (ADR-013). notification-service publishes nothing yet, but the shared messaging module maps this table.
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

-- Inbox (ADR-013): an event is applied only if its id is not here yet.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    consumer     VARCHAR(60) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

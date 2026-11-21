-- Payment link, policy version, outbox and inbox (PLAN 7, 10, ADR-013, M4b).

-- The premium payment in payment-service, by id only (one database per service). Set at purchase.
ALTER TABLE policies ADD COLUMN payment_id UUID UNIQUE;
-- Increases with every change to the policy; sent in events so consumers can ignore stale ones (PLAN 10).
ALTER TABLE policies ADD COLUMN policy_version BIGINT NOT NULL DEFAULT 1;

-- The unpaid-policy job looks for old PENDING_PAYMENT policies.
CREATE INDEX policies_status_created ON policies (status, created_at);

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

-- Inbox (ADR-013): an event is applied only if its id is not here yet.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    consumer     VARCHAR(60) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

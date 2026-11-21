-- Payments and the payment ledger (PLAN 7, M4b). One premium payment per policy (reference).
CREATE TABLE payments (
    id               UUID PRIMARY KEY,
    -- Idempotency key of the creation request (policy-service sends the policy id): a retry returns the same payment.
    intent_key       VARCHAR(80) NOT NULL UNIQUE,
    reference_type   VARCHAR(20) NOT NULL CHECK (reference_type IN ('POLICY')),
    reference_id     UUID NOT NULL,
    reference_number VARCHAR(60) NOT NULL,
    insurer_id       UUID NOT NULL,
    holder_user_id   UUID NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    provider_ref     VARCHAR(80),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT payments_one_per_reference UNIQUE (reference_type, reference_id)
);

CREATE INDEX payments_holder ON payments (holder_user_id, created_at);

-- Immutable ledger: one row per payment attempt. The code never updates these rows.
CREATE TABLE payment_transactions (
    id               UUID PRIMARY KEY,
    payment_id       UUID NOT NULL REFERENCES payments (id),
    -- Idempotency key of the pay request: a repeat with the same key returns this attempt's result.
    idempotency_key  VARCHAR(80) NOT NULL UNIQUE,
    outcome          VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    provider_ref     VARCHAR(80),
    failure_reason   VARCHAR(60),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX payment_transactions_payment ON payment_transactions (payment_id, created_at);

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

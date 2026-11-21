-- The policy read model, built from policy.events (PLAN 7, M5a). Claim-service keeps its own copy and never
-- reads policy_db (ADR-001, CLAUDE.md Domain rules).
CREATE TABLE policy_snapshots (
    policy_id            UUID PRIMARY KEY,
    policy_number        VARCHAR(40) NOT NULL,
    insurer_id           UUID NOT NULL,
    -- Null for a policy that was cancelled before it was ever activated (it never had a holder).
    holder_user_id       UUID,
    status               VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    cover_start          DATE,
    cover_end            DATE,
    cancel_effective_date DATE,
    -- policy-service bumps this on every change; a change applies only when it is higher (PLAN 11).
    policy_version       BIGINT NOT NULL,
    cancel_reason        VARCHAR(40),
    -- The PolicyActivated payload as received: the frozen terms and members (ADR-011).
    activation           JSONB,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX policy_snapshots_holder ON policy_snapshots (holder_user_id, cover_start);

-- Transactional outbox (ADR-013). Claim-service sends nothing yet, but common-messaging maps this table
-- at startup, so it must exist (ddl-auto=validate). M5b starts writing to it.
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

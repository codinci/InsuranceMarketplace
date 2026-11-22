-- Review and benefit utilisation (PLAN 7, 9.2, M6).

ALTER TABLE claims
    -- What the evaluator says can be paid, after limits and co-pay (PLAN 9.2). Set when review starts and again at approval.
    ADD COLUMN amount_eligible NUMERIC(19, 2),
    -- What the reviewer approved: never more than amount_eligible.
    ADD COLUMN amount_approved NUMERIC(19, 2),
    ADD COLUMN reviewer_id     UUID,
    ADD COLUMN reviewer_role   VARCHAR(20),
    ADD COLUMN decision_reason VARCHAR(500),
    ADD COLUMN reviewed_at     TIMESTAMP WITH TIME ZONE,
    -- True when a soft flag was raised at submission (PLAN 9.1); the queue shows these first.
    ADD COLUMN flagged         BOOLEAN NOT NULL DEFAULT FALSE;

-- The review queue: one insurer's open claims, flagged first, oldest first.
CREATE INDEX claims_queue ON claims (insurer_id, status, flagged, submitted_at);

-- Running totals against the policy's limits (PLAN 7). member_id is null for SHARED limits and set for PER_MEMBER limits.
-- version is optimistic locking: two approvals that read the same total cannot both write it (PLAN 11).
CREATE TABLE benefit_utilisation (
    id           UUID PRIMARY KEY,
    policy_id    UUID NOT NULL,
    member_id    VARCHAR(40),
    benefit_code VARCHAR(40) NOT NULL,
    policy_year  INTEGER NOT NULL CHECK (policy_year >= 1),
    amount_used  NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (amount_used >= 0),
    count_used   INTEGER NOT NULL DEFAULT 0 CHECK (count_used >= 0),
    version      BIGINT NOT NULL DEFAULT 0
);

-- A null member must still make a unique key, so it is compared as an empty string.
CREATE UNIQUE INDEX benefit_utilisation_key
    ON benefit_utilisation (policy_id, benefit_code, policy_year, COALESCE(member_id, ''));

-- What one approved claim reserved on each limited benefit, so the reservation can be released exactly (PLAN 9.2).
CREATE TABLE claim_reservations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id       UUID NOT NULL REFERENCES claims (id),
    utilisation_id UUID NOT NULL REFERENCES benefit_utilisation (id),
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    counted        INTEGER NOT NULL CHECK (counted IN (0, 1)),
    reserved_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX claim_reservations_claim ON claim_reservations (claim_id);

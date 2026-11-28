-- Customer cancellation, expiry and the claim counter (PLAN 17 M8b, ADR-021).

-- Set when a policy is cancelled. effective date: the first day without cover.
ALTER TABLE policies ADD COLUMN cancel_effective_date DATE;
ALTER TABLE policies ADD COLUMN cancel_reason VARCHAR(20);
-- The refund the plan's rule gave at cancellation; null for an unpaid cancellation.
ALTER TABLE policies ADD COLUMN refund_amount NUMERIC(19, 2);

-- The expiry job looks for ACTIVE policies whose cover has ended.
CREATE INDEX policies_status_end_date ON policies (status, end_date);

-- Claims submitted against a policy, learned from ClaimSubmitted on claim.events, so the
-- noRefundIfClaims rule needs no call to claim-service. The claim id makes a repeat harmless.
CREATE TABLE policy_claims (
    claim_id     UUID PRIMARY KEY,
    policy_id    UUID NOT NULL REFERENCES policies (id),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX policy_claims_policy ON policy_claims (policy_id);

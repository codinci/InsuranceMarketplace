-- Claim payouts (PLAN 10, M7). A payout is a payment whose reference is a claim. The reference type
-- tells a premium (POLICY) from a payout (CLAIM), and the unique (reference_type, reference_id)
-- already allows one payout per claim.
ALTER TABLE payments DROP CONSTRAINT payments_reference_type_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_reference_type_check CHECK (reference_type IN ('POLICY', 'CLAIM'));

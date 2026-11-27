-- Premium refunds (PLAN 17 M8b, ADR-021). A refund is a payment whose reference is the cancelled
-- policy, with its own reference type, so the unique (reference_type, reference_id) allows one
-- refund per policy next to its premium.
ALTER TABLE payments DROP CONSTRAINT payments_reference_type_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_reference_type_check
        CHECK (reference_type IN ('POLICY', 'CLAIM', 'REFUND'));

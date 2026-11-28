-- Expired policies in the read model (PLAN 17 M8b). An expired policy still covers incidents that
-- happened during its cover, so claims for them stay possible.
ALTER TABLE policy_snapshots DROP CONSTRAINT policy_snapshots_status_check;
ALTER TABLE policy_snapshots
    ADD CONSTRAINT policy_snapshots_status_check CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED'));

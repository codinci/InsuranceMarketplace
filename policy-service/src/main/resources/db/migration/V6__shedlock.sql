-- ShedLock (PLAN 11, ADR-022): one row per scheduled job. An instance runs the job only while it
-- holds the row's lock, so the unpaid and expiry jobs never run twice at the same moment.
-- The times are UTC without a zone, as ShedLock's database-time mode writes and compares them.
CREATE TABLE shedlock (
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at  TIMESTAMP NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Policy holders (PLAN 7, M4a). Owned by the policy package; never references catalogue tables (ADR-001).
-- national_id is encrypted by the service (D37, ADR-012): the database only ever sees ciphertext.
CREATE TABLE policy_holders (
    user_id        UUID PRIMARY KEY,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    date_of_birth  DATE NOT NULL,
    sex            VARCHAR(10) NOT NULL CHECK (sex IN ('FEMALE', 'MALE')),
    national_id    VARCHAR(200) NOT NULL,
    phone          VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version   BIGINT NOT NULL DEFAULT 0
);

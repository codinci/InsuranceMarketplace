-- Claims (PLAN 7, M5b). Claim-service owns these tables; it never reads another service's database.

CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claims (
    id                UUID PRIMARY KEY,
    claim_number      VARCHAR(40) NOT NULL UNIQUE,
    insurer_id        UUID NOT NULL,
    policy_id         UUID NOT NULL,
    holder_user_id    UUID NOT NULL,
    member_id         VARCHAR(40) NOT NULL,
    module_code       VARCHAR(40) NOT NULL,
    benefit_code      VARCHAR(40) NOT NULL,
    cause             VARCHAR(20) NOT NULL CHECK (cause IN ('ACCIDENT', 'ILLNESS')),
    surgical          BOOLEAN NOT NULL,
    condition_tags    JSONB NOT NULL,
    incident_date     DATE NOT NULL,
    provider_name     VARCHAR(120) NOT NULL,
    description       VARCHAR(2000) NOT NULL,
    amount_claimed    NUMERIC(19, 2) NOT NULL CHECK (amount_claimed > 0),
    currency          VARCHAR(3) NOT NULL,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED',
                                                              'REJECTED', 'APPROVED', 'PAYOUT_PENDING', 'PAID',
                                                              'PAYOUT_FAILED', 'WITHDRAWN')),
    rejection_reason  VARCHAR(60),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at      TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX claims_holder ON claims (holder_user_id, created_at);

CREATE TABLE claim_documents (
    id           UUID PRIMARY KEY,
    claim_id     UUID NOT NULL REFERENCES claims (id),
    object_key   VARCHAR(80) NOT NULL UNIQUE,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(60) NOT NULL,
    size_bytes   BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    doc_type     VARCHAR(40) NOT NULL,
    uploaded_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX claim_documents_claim ON claim_documents (claim_id);

-- Append-only history of status changes (PLAN 7). The code never updates or deletes these rows.
CREATE TABLE claim_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id    UUID NOT NULL REFERENCES claims (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    actor_id    UUID NOT NULL,
    actor_role  VARCHAR(20) NOT NULL,
    reason      VARCHAR(200),
    changed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX claim_status_history_claim ON claim_status_history (claim_id, changed_at);

-- Append-only record of the automatic checks (PLAN 7).
CREATE TABLE claim_assessments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id    UUID NOT NULL REFERENCES claims (id),
    rule_code   VARCHAR(60) NOT NULL,
    result      VARCHAR(10) NOT NULL CHECK (result IN ('PASS', 'FAIL', 'FLAG')),
    detail      VARCHAR(300) NOT NULL,
    assessed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX claim_assessments_claim ON claim_assessments (claim_id, assessed_at);

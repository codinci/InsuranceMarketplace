-- Quotes and policies (PLAN 7, 8.2, M4a). Owned by the policy package; plans are referenced by id and version
-- only, with no foreign key into catalogue tables (ADR-001). Money is NUMERIC(19,2) (CLAUDE.md).

CREATE TABLE quotes (
    id                UUID PRIMARY KEY,
    holder_user_id    UUID NOT NULL,
    insurer_id        UUID NOT NULL,
    insurer_code      VARCHAR(40) NOT NULL,
    plan_id           UUID NOT NULL,
    plan_code         VARCHAR(40) NOT NULL,
    plan_version      INTEGER NOT NULL,
    cover_type        VARCHAR(20) NOT NULL CHECK (cover_type IN ('INDIVIDUAL', 'FAMILY')),
    start_date        DATE NOT NULL,
    members           JSONB NOT NULL,
    selected_modules  JSONB NOT NULL,
    annual_premium    NUMERIC(19, 2) NOT NULL,
    breakdown         JSONB NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    valid_until       TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'USED')),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX quotes_holder ON quotes (holder_user_id, created_at);

-- Numbers for policies and their members (PLAN 3).
CREATE SEQUENCE policy_number_seq;

CREATE TABLE policies (
    id                 UUID PRIMARY KEY,
    policy_number      VARCHAR(60) NOT NULL UNIQUE,
    holder_user_id     UUID NOT NULL REFERENCES policy_holders (user_id),
    -- One policy per quote: a second purchase of the same quote fails here even if two requests race.
    quote_id           UUID NOT NULL UNIQUE REFERENCES quotes (id),
    insurer_id         UUID NOT NULL,
    insurer_code       VARCHAR(40) NOT NULL,
    plan_id            UUID NOT NULL,
    plan_code          VARCHAR(40) NOT NULL,
    plan_version       INTEGER NOT NULL,
    cover_type         VARCHAR(20) NOT NULL,
    selected_modules   JSONB NOT NULL,
    terms_snapshot     JSONB NOT NULL,
    annual_premium     NUMERIC(19, 2) NOT NULL,
    premium_breakdown  JSONB NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    start_date         DATE NOT NULL,
    end_date           DATE NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    declaration        JSONB NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX policies_holder ON policies (holder_user_id, created_at);

CREATE TABLE policy_members (
    id             UUID PRIMARY KEY,
    policy_id      UUID NOT NULL REFERENCES policies (id),
    position       INTEGER NOT NULL,
    member_number  VARCHAR(60) NOT NULL UNIQUE,
    relationship   VARCHAR(20) NOT NULL CHECK (relationship IN ('PRINCIPAL', 'SPOUSE', 'CHILD')),
    sex            VARCHAR(10) NOT NULL CHECK (sex IN ('FEMALE', 'MALE')),
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    date_of_birth  DATE NOT NULL,
    student        BOOLEAN NOT NULL,
    UNIQUE (policy_id, position)
);

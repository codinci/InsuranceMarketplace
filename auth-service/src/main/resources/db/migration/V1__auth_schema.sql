-- Identity schema for auth_db (PLAN 7, M1). Schema changes only through Flyway (CLAUDE.md).

CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(254) NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    -- Staff belong to exactly one insurer (PLAN 4). Null for customers and platform admins.
    insurer_id          UUID NULL,
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP WITH TIME ZONE NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'ADJUSTER', 'ADMIN', 'SERVICE')),
    PRIMARY KEY (user_id, role)
);

-- Refresh tokens are stored as SHA-256 hashes, never raw (PLAN 12).
-- family_id groups every token issued by one login, so reuse of a rotated token can revoke the whole family.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX users_insurer_idx ON users (insurer_id);

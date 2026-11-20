-- Catalogue schema for policy_db (PLAN 7, M3b, ADR-002, ADR-010). Schema changes only through Flyway.
-- The policy package (M4) gets its own tables and never references these (ADR-001).

CREATE TABLE insurers (
    id          UUID PRIMARY KEY,
    code        VARCHAR(40) NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE products (
    id          UUID PRIMARY KEY,
    insurer_id  UUID NOT NULL REFERENCES insurers (id),
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    line        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (insurer_id, code)
);

-- One row per plan version. insurer_id is copied from the product so every staff query filters on one column.
-- created_by and published_by are user ids from the access token; null means the startup seeder.
CREATE TABLE plans (
    id              UUID PRIMARY KEY,
    insurer_id      UUID NOT NULL REFERENCES insurers (id),
    product_id      UUID NOT NULL REFERENCES products (id),
    code            VARCHAR(40) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    plan_version    INTEGER NOT NULL CHECK (plan_version >= 1),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    sale_from       DATE NOT NULL,
    sale_to         DATE NULL,
    currency        VARCHAR(3) NOT NULL,
    schema_version  INTEGER NOT NULL,
    definition      JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE NULL,
    published_by    UUID NULL,
    retired_at      TIMESTAMP WITH TIME ZONE NULL,
    lock_version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (product_id, code, plan_version)
);

CREATE INDEX plans_insurer_status ON plans (insurer_id, status);

-- At most one published version of a plan at any time (D33, ADR-010).
CREATE UNIQUE INDEX plans_one_published ON plans (product_id, code) WHERE status = 'PUBLISHED';

-- Published and retired versions are immutable (CLAUDE.md, ADR-002, ADR-010). The service refuses such changes
-- first; this trigger is the last line of defence against any other code path or a manual UPDATE.
CREATE FUNCTION plans_guard_immutable() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'plan % is %, it cannot be deleted', OLD.id, OLD.status;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status <> 'DRAFT' AND (
           NEW.definition IS DISTINCT FROM OLD.definition
        OR NEW.code IS DISTINCT FROM OLD.code
        OR NEW.name IS DISTINCT FROM OLD.name
        OR NEW.plan_version IS DISTINCT FROM OLD.plan_version
        OR NEW.product_id IS DISTINCT FROM OLD.product_id
        OR NEW.insurer_id IS DISTINCT FROM OLD.insurer_id
        OR NEW.sale_from IS DISTINCT FROM OLD.sale_from
        OR NEW.sale_to IS DISTINCT FROM OLD.sale_to
        OR NEW.currency IS DISTINCT FROM OLD.currency) THEN
        RAISE EXCEPTION 'plan % is %, its terms are immutable', OLD.id, OLD.status;
    END IF;
    IF OLD.status = 'RETIRED' AND NEW.status <> 'RETIRED' THEN
        RAISE EXCEPTION 'plan % is retired', OLD.id;
    END IF;
    IF OLD.status = 'PUBLISHED' AND NEW.status = 'DRAFT' THEN
        RAISE EXCEPTION 'plan % is published and cannot return to draft', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER plans_immutable
    BEFORE UPDATE OR DELETE ON plans
    FOR EACH ROW EXECUTE FUNCTION plans_guard_immutable();

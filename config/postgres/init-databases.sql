-- One database per service (PLAN 3, CLAUDE.md Domain rules). Each service gets its own schema
-- through Flyway later; services never read another service's database.
CREATE DATABASE auth_db;
CREATE DATABASE policy_db;
CREATE DATABASE claim_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;

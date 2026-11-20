# ADR-012: Encrypting personal data at rest

- **Status:** Accepted (2026-11-21). Implemented in M4a for the national ID.
- **Context:** PLAN 12 requires the national ID to be encrypted at column level, and health, sex and identity data to be treated as personal data. A database dump, a backup or a read-only database account must not reveal national IDs. The service needs the plain value only to show its last digits and, later, to match records.

## Decision

1. **AES-256-GCM in the service (D37).** A JPA `AttributeConverter` encrypts on write and decrypts on read, using the JDK's crypto (no new library). The database never sees the plain value.
2. **A fresh 12-byte random nonce per value**, so two customers with the same ID have different ciphertexts. GCM's tag detects any change to the stored value.
3. **The column name is bound as associated data**, so a value copied into another encrypted column fails to decrypt.
4. **Stored form:** `v1:<keyId>:<base64(nonce || ciphertext and tag)>`. The key id names the key that can read the value, which makes rotation possible: add the new key, re-encrypt, then remove the old one.
5. **The key** is 32 random bytes in `POLICY_FIELD_KEY_BASE64` with an id in `POLICY_FIELD_KEY_ID`, from the environment (`.env`, never committed). A missing or wrong-sized key stops the service at startup.
6. **Masking and logging.** Responses show `***` plus the last three characters. Entities and request DTOs override `toString` so the value cannot reach a log line; request bodies are never logged (CLAUDE.md).

## Consequences

- Searching by national ID is not possible in SQL. If it is needed later (for example duplicate detection), add a keyed hash (HMAC) column next to the ciphertext.
- Losing the key loses the data. Keys belong in a secrets store when the platform is deployed (M11); locally they live in `.env`.
- Only one key is configured today. Rotation needs a small change: a map of key ids to keys, and a re-encryption job.

## Alternatives considered

- **pgcrypto in Postgres.** Rejected: the key would be sent with queries and could appear in database logs.
- **Disk or volume encryption only.** Rejected: it does not protect against dumps, backups or anyone with database access.
- **A third-party crypto library.** Rejected for now: the JDK covers AES-GCM; a library such as Tink becomes worth it with key rotation and several encrypted fields.

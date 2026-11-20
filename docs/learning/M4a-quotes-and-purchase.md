# M4a: Profile, quotes and purchase

## What M4a delivers

- `PUT/GET /me/profile`: the policy holder's details, with the national ID encrypted at rest and masked in responses.
- `POST /quotes`: eligibility and module checks, then a premium computed on the server. A quote is valid for 30 days.
- `POST /policies`: buys a policy from an open quote. It is stored as `PENDING_PAYMENT` with the quoted premium, frozen terms, a one-year term, and policy and member numbers.
- `catalogue.api`: the only door between the `policy` and `catalogue` packages, enforced by ArchUnit.
- `TermsSnapshot` in `common-events`, the contract claim-service will read (ADR-011). ADR-012 covers the encryption.
- A business calendar, so "today" is the Nairobi date, not the UTC date (D38).

## New concepts

**An interface boundary inside one service.** `CatalogueApi` is a Java interface with its own small records and enums. The `policy` package depends on that interface only, and Spring injects the implementation (`CatalogueApiService`). An ArchUnit rule fails the build if `policy` touches any other catalogue class. It caught one leak during M4a: `PageResponse` lived in `catalogue.dto`. The boundary is what makes ADR-001 ("can be extracted later") true.

**`AttributeConverter`.** A class that turns an entity field into a column value and back. `@Convert(converter = NationalIdConverter.class)` on the field makes Hibernate call it on every save and load. Spring Boot lets Hibernate get the converter from Spring, so it can have constructor-injected dependencies (the encryptor).

**AES-GCM in one paragraph.** AES is the cipher, and GCM is the mode. Each value gets a random 12-byte nonce, which must never repeat with the same key. GCM also produces a tag, so any change to the stored bytes makes decryption fail instead of returning garbage. "Associated data" is extra input that is authenticated but not stored. Here it is the column name, so a value cannot be copied into another column.

**Database sequences.** `nextval('policy_number_seq')` hands out increasing numbers that never repeat, even with many service instances. A native query (`@Query(nativeQuery = true)`) reads it.

**Optimistic locking for double submission.** The quote has `@Version`. Two purchases of the same quote both read version 3, and the second save fails because the row is now version 4. A unique key on `policies.quote_id` backs it up in the database.

**Time zones and `LocalDate`.** `Instant` is a moment, so storing it in UTC is right. "Today" is a calendar question, though: at 01:30 in Nairobi it is still yesterday in UTC. Cover dates are business dates, so they use `LocalDate.now(clock.withZone(Africa/Nairobi))`. Tests near midnight found this.

## Why this approach

- **The premium comes from the quote, never the request** (CLAUDE.md). The purchase copies the stored amount, so a tampered client cannot change it.
- **Members are fixed by the quote** (D36). Changing anyone's age or the family size changes the price, so it needs a new quote.
- **The snapshot is an event contract** (ADR-011). Claims are judged against exactly the sold terms, without asking the catalogue.
- **Another customer's quote or policy answers 404.** This is the IDOR rule, tested for every customer endpoint.

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| `NationalIdConverter` | An `encrypted` cast on the model attribute |
| `CatalogueApi` boundary | A contract (interface) bound in a service provider, with a deptrac or arch test |
| `@Version` optimistic lock | A `version` column checked in `update ... where version = ?` |
| `nextval` sequence | A database sequence or an auto-increment counter table |
| Ownership checks with 404 | A policy plus `abort_if(... , 404)`, or a route-model binding scoped to the user |
| `BusinessCalendar` | `now('Africa/Nairobi')->toDateString()` instead of `today()` in UTC |

## Self-check questions (answer before starting M4b)

1. A customer gets a quote on Monday for a family of three, then on Thursday tries to buy it with their daughter's date of birth corrected by one day. What happens and why? What should the customer do?
2. Two browser tabs submit `POST /policies` for the same quote at the same moment. Which two mechanisms stop a second policy, and which response does the losing request get?
3. A database backup leaks. What does the attacker learn about national IDs, and what would change if the same key also leaked? Why does each value look different even for two customers with the same ID?

## Official documentation

- JPA attribute converters (Jakarta Persistence): https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2#a3264
- Java Cryptography Architecture (AES/GCM): https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html
- Spring Data JPA locking: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
- `java.time` (LocalDate, ZoneId): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html

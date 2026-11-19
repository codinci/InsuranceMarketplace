# Parking lot

Ideas that are outside the current milestone (CLAUDE.md workflow rule 8, PLAN 21.4). Nothing here enters scope without a plan edit.

| Found in | Idea or issue | Notes |
|---|---|---|
| M0b | Kafka warns that topics with `.` and `_` can collide in metric names | Our topics use dots only (PLAN 10), so the warning is harmless. Keep it in mind if an underscore topic is ever proposed. |
| M0b | `.gitignore` lists `PLAN.md`, but `PLAN.md` is tracked in Git | Ignoring has no effect on a tracked file. Decide whether to remove the line. |
| M0b | Leftover containers and volumes from earlier local runs can hold ports the project needs | Owner decision on cleanup. Do not remove them as part of the project. |
| M0b | ADR-001 (catalogue in policy-service), ADR-002 (document-first catalogue), ADR-003 (rating strategies) | Deferred to M3a, where these decisions are made and implemented. |
| M0b | ADRs for the section 3 design decisions (monorepo, roles and JWT, outbox and inbox, money, IDs, time, API style) | Each is written in the milestone that first implements it (for example the JWT ADR at M1, the outbox ADR at M4). Not written in advance. |
| M0b | ADR for topic and partition design (PLAN 21.6) | Topics exist since M0b, but the first producer arrives at M4. Write the ADR at M4, before the first event is published, and reopen it then if the M0b topic layout needs to change. |
| M0b | Host ports can be taken by other software already installed on a development machine | Compose uses 15432 for Postgres and `HELLO_HOST_PORT` for hello-service. The gateway port (8080) decision is due at M2. |

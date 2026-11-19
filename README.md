# Mini Insurance Platform

Spring Boot microservices for a multi-insurer medical insurance marketplace. The full plan is in [PLAN.md](PLAN.md). Working rules for coding agents are in [CLAUDE.md](CLAUDE.md).

## Requirements

- Java 21 (Temurin recommended)
- Docker (for Hadolint and, from M0b, the compose stack)
- Git Bash on Windows, or any POSIX shell

## One-time setup

```bash
bash scripts/setup-hooks.sh   # enables the pre-commit gate
```

## Common commands

```bash
./mvnw -B verify                      # build and all checks for every module
bash scripts/verify.sh                # the full gate, as CI runs it
./mvnw -pl hello-service -am verify   # one module and the modules it needs
./mvnw spotless:apply                 # format Java
```

## Status

M0a (build, quality gates, CI, walking skeleton) is in progress. See the status tracker in [PLAN.md](PLAN.md) section 21.7.

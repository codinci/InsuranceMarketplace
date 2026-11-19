#!/usr/bin/env bash
# G1 (PLAN 16.2): formatting check, compile, and unit tests of the modules that changed.
# A change to the parent POM, or no recognisable module, tests everything.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

staged="$(git diff --cached --name-only)"
modules=""
for top in $(printf '%s\n' "$staged" | cut -d/ -f1 | sort -u); do
  if [ -f "$top/pom.xml" ]; then
    modules="${modules:+$modules,}$top"
  fi
done

if printf '%s\n' "$staged" | grep -qx 'pom.xml' || [ -z "$modules" ]; then
  echo "pre-commit: spotless:check, compile and unit tests (all modules)"
  ./mvnw -q -B spotless:check test-compile test
else
  echo "pre-commit: spotless:check, compile and unit tests (${modules})"
  ./mvnw -q -B -pl "$modules" -am spotless:check test-compile test
fi

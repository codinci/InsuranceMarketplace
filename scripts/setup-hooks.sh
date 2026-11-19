#!/usr/bin/env bash
# Enables the repo's git hooks (run once per clone).
set -euo pipefail
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit scripts/verify.sh scripts/hooks/*.sh scripts/setup-hooks.sh
echo "git hooks enabled: core.hooksPath=.githooks"

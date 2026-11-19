#!/usr/bin/env bash
# Claude Code PostToolUse hook (G0, PLAN 16.2). Formats the Java file that was just edited.
# Reads the hook payload (JSON) from stdin. Never blocks the edit: it reports and exits 0.
set -uo pipefail
cd "$(dirname "$0")/../.."

payload="$(cat)"
raw="$(printf '%s' "$payload" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"file_path"[[:space:]]*:[[:space:]]*"(.*)"/\1/')"
# JSON escapes each Windows backslash as two; turn every run of backslashes into a forward slash.
file="$(printf '%s' "$raw" | sed -E 's#\+#/#g')"

case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac

name="$(basename "$file")"
escaped="${name//./\.}"
if ! ./mvnw -q -B spotless:apply -DspotlessFiles=".*${escaped}\$" >/dev/null 2>&1; then
  echo "format-java: spotless:apply failed for $file; run ./mvnw spotless:apply to see why" >&2
fi
exit 0

#!/usr/bin/env bash
# SessionStart hook. Cloud sessions only (CLAUDE_CODE_REMOTE=true); a local session exits at once.
#
# A cloud container starts from a fresh clone: no JDK 25, no Gradle, no dependency cache, and scripts
# whose executable bit a Windows checkout may have lost. This provisions all of it once, before the
# session starts, so the first verify is a build and not a two-minute download. The container is
# cached after the hook, so a resumed session pays nothing.
set -euo pipefail

if [[ "${CLAUDE_CODE_REMOTE:-}" != "true" ]]; then
  exit 0
fi

ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$ROOT"

chmod +x .claude/scripts/*.sh .claude/hooks/*.sh 2>/dev/null || true
mkdir -p ai/logs

# Downloads Temurin 25 and Gradle into ai/toolchain, resolves every dependency and compiles main and
# test sources. Idempotent: a second run is a warm no-op build. Output goes to the log, not the
# session - a failure here must not stop the session starting, it only means the first verify pays.
if bash .claude/scripts/gradle.sh compileTestJava > ai/logs/session-start.log 2>&1; then
  echo "session-start: toolchain and dependencies ready"
else
  echo "session-start: bootstrap failed, see ai/logs/session-start.log - verify.sh will retry it"
fi

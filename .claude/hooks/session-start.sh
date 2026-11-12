#!/usr/bin/env bash
# SessionStart hook. Every session installs the git guard on main; the rest is cloud only
# (CLAUDE_CODE_REMOTE=true), and a local session exits after the guard.
#
# A cloud container starts from a fresh clone: no JDK 25, no Gradle, no dependency cache, and scripts
# whose executable bit a Windows checkout may have lost. This provisions all of it once, before the
# session starts, so the first verify is a build and not a two-minute download. The container is
# cached after the hook, so a resumed session pays nothing.
set -euo pipefail

# The guard that keeps Claude off main (.githooks/reference-transaction). .git/hooks is shared by
# every worktree, so one copy covers them all, whatever branch a worktree was cut from.
GUARD_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
if HOOKS="$(git -C "$GUARD_ROOT" rev-parse --git-common-dir 2>/dev/null)/hooks" && [[ -f "$GUARD_ROOT/.githooks/reference-transaction" ]]; then
  [[ "$HOOKS" = /* || "$HOOKS" =~ ^[A-Za-z]: ]] || HOOKS="$GUARD_ROOT/$HOOKS"
  mkdir -p "$HOOKS" && cp "$GUARD_ROOT/.githooks/reference-transaction" "$HOOKS/reference-transaction" \
    && chmod +x "$HOOKS/reference-transaction" 2>/dev/null || true
fi

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

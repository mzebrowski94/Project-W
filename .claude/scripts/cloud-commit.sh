#!/usr/bin/env bash
# Commits and pushes a cloud session's work to its own claude/* branch. The ONE way Claude writes git
# history; raw `git add`, `commit` and `push` stay denied in .claude/settings.json for every session.
#
#   .claude/scripts/cloud-commit.sh -m "Subject line" [-m "Trailer or body"]... [-- path...]
#
# Without paths it stages everything git does not ignore. It refuses, and changes nothing, when:
#   - this is not a cloud session (CLAUDE_CODE_REMOTE != true) - locally, Mateusz commits;
#   - the branch is not claude/* or ccr-* - never main, never somebody else's branch;
#   - there is no message, or nothing to commit.
# It never amends, rebases or force-pushes. Mateusz merges the branch into main himself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail() {
  echo "cloud-commit: $*" >&2
  exit 1
}

[[ "${CLAUDE_CODE_REMOTE:-}" == "true" ]] || fail "not a cloud session - Mateusz commits locally (CLAUDE.md)."

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[[ "$BRANCH" == claude/* || "$BRANCH" == ccr-* ]] || fail "branch '$BRANCH' is not a claude/* or ccr-* branch - refusing."

MESSAGE_ARGS=()
PATHS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -m)
      [[ $# -ge 2 && -n "$2" ]] || fail "-m needs a message."
      MESSAGE_ARGS+=(-m "$2")
      shift 2
      ;;
    --)
      shift
      PATHS=("$@")
      break
      ;;
    *) fail "unknown argument '$1' - only -m and -- paths are accepted." ;;
  esac
done
[[ ${#MESSAGE_ARGS[@]} -gt 0 ]] || fail "no commit message (-m)."

if [[ ${#PATHS[@]} -gt 0 ]]; then
  git add -- "${PATHS[@]}"
else
  git add -A
fi
git diff --cached --quiet && fail "nothing staged to commit."

git commit "${MESSAGE_ARGS[@]}"

# Network hiccups only: retry with backoff 2s, 4s, 8s, 16s. A rejection is not retried into a force.
for delay in 0 2 4 8 16; do
  sleep "$delay"
  if git push -u origin "$BRANCH"; then
    echo "cloud-commit: pushed $(git rev-parse --short HEAD) to origin/$BRANCH"
    exit 0
  fi
done
fail "push failed after retries; the commit is local only."

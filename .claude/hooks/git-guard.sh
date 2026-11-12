#!/usr/bin/env bash
# Runs git-guard.py with whichever Python this machine has. With none, a command that mentions git is
# refused rather than let through unguarded: the permission rules allow git writes because this runs.
INPUT="$(cat)"
# Most calls never mention git: let them through before paying for a Python start (~300 ms on Windows).
shopt -s nocasematch
[[ "$INPUT" == *git* || "$INPUT" == *claudecode* ]] || exit 0
DIR="$(dirname "${BASH_SOURCE[0]}")"
for py in python3 python; do
  if "$py" -c "import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)" >/dev/null 2>&1; then
    printf '%s' "$INPUT" | "$py" -I "$DIR/git-guard.py"
    exit $?
  fi
done
case "$INPUT" in
  *git*) printf '%s\n' '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"git-guard: no Python 3.8+ found, so git commands cannot be checked"}}' ;;
esac
exit 0

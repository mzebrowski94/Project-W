#!/usr/bin/env bash
# Format, compile, lint, test. Compact result; failures in full.
#
#   .claude/scripts/verify.sh [gradle task ...]      default: build
#
# Spotless rewrites a source file by unlinking it first. That is the ONLY thing in the build that
# needs delete rights on the Windows mount, and the sandbox grants those per session. Without them
# we still compile, lint and test - formatting is reported instead of applied.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GRADLE="$ROOT/.claude/scripts/gradle.sh"
LOG="$ROOT/ai/logs/verify.log"
mkdir -p "$(dirname "$LOG")"

TASKS=("$@")
[[ ${#TASKS[@]} -eq 0 ]] && TASKS=(build)

PROBE="$ROOT/ai/.delete-probe"
CAN_DELETE=1
if [[ -n "${PWT_ASSUME_NO_DELETE:-}" ]]; then
  CAN_DELETE=0
else
  : > "$PROBE" 2>/dev/null
  rm -f "$PROBE" 2>/dev/null || CAN_DELETE=0
fi

NOISE='^WARNING: (A terminally deprecated|sun\.misc\.Unsafe|Please consider reporting)'

if [[ $CAN_DELETE -eq 1 ]]; then
  bash "$GRADLE" spotlessApply "${TASKS[@]}" > "$LOG" 2>&1
  STATUS=$?
else
  # `check` depends on spotlessCheck; exclude it so unformatted code does not fail the whole
  # build when we are not allowed to fix it, then report formatting separately below.
  bash "$GRADLE" "${TASKS[@]}" -x spotlessCheck -x spotlessJavaCheck > "$LOG" 2>&1
  STATUS=$?
fi

if [[ $STATUS -eq 0 ]]; then
  echo "VERIFY: PASS"
  shopt -s nullglob
  for f in "$HOME"/.pwt/out/*/build/test-results/test/TEST-*.xml; do
    sed -n 's/.*name="\([^"]*\)".*tests="\([0-9]*\)".*failures="\([0-9]*\)".*errors="\([0-9]*\)".*/  \1: \2 tests, \3 failures, \4 errors/p' "$f" | head -1
  done
  grep -E "warning: \[" "$LOG" | grep -vE "$NOISE" | sed 's/^/  /' | head -20
  if [[ $CAN_DELETE -eq 0 ]]; then
    if bash "$GRADLE" spotlessCheck >> "$LOG" 2>&1; then
      echo "  formatting: clean (not applied - no delete permission this session)"
    else
      echo "  formatting: NEEDS APPLYING, and cannot be applied without delete permission."
      echo "  Ask Mateusz to approve deletion for this folder, then re-run."
    fi
  fi
else
  echo "VERIFY: FAIL"
  grep -vE "$NOISE" "$LOG" | tail -80
  echo "--- full log: ai/logs/verify.log ---"
fi
exit $STATUS

# Sourced by gradle.sh and verify.sh, with ROOT set to the checkout. Sets PWT_OUT_SUB: the part of
# the build output path, under ~/.pwt/out/, that belongs to this checkout. The main checkout keeps the
# top level; a linked worktree (its .git is a file) gets wt/<folder>/, so agents building in parallel
# never overwrite each other's classes - hit 2026-11-11, when they shared one folder.
PWT_OUT_SUB=""
if [[ -f "$ROOT/.git" ]]; then
  PWT_OUT_SUB="wt/$(basename "$ROOT")/"
fi
export PWT_OUT_SUB

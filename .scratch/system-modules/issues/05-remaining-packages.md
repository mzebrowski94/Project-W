# 05 - The remaining fenced packages

Type: task

Blocked by: 04

## Question

Move every other system into its fenced package, one package per stage, and make the docs say so.

## Read first

`.scratch/system-modules/map.md` (03's split, 04's answer), `docs/adr/0020-*.md`.

## Steps

1. One package per stage, `verify.sh` green after each - a stage Mateusz could stop after. Each stage:
   `python3 .claude/scripts/move-classes.py pl.mzebrows.pwt.sim.system.<pkg> <classes and tests from
   split.txt and 03's answer, section 7>`; compile, and make public what it names; add the package to
   `ArchitectureTest.FENCED` and its ports to `PORTS`; write its `package-info.java` (systems, ports, what
   does not belong, the records it answers to); move the ports' lines out of the flat `package-info.java`.
   `upgrade` last: it moves `sim.upgrade` to `sim.system.upgrade` and changes imports in `ui`, `core`
   and `net`.
2. When `sim.system` holds no class of its own: take `SYSTEM` out of `ArchitectureTest.HOMES` and
   `sim.upgrade` out of `everySystemLivesUnderSystem` (04's transitional allowances); delete the flat
   `package-info.java`; `upgradesAreCalledAndDoNotCall` becomes "no other fenced package" (03's answer,
   section 6). Then:
   - `docs/architecture.md`: a table of fenced packages - what each is, its systems, its ports - and the
     placement rule. It replaces the `system/` paragraph and 0017's exception.
   - The `new-system` skill: where a new system goes (the placement rule), and when it founds a package.
   - `CLAUDE.md`: the *Reuse before you create* list and the *Where to look things up* row for
     `architecture.md`, if their wording no longer holds. One line each in the final message.
   - Every other place 03 listed.

## Done when

`verify.sh` green, no class directly in `sim.system`, `sim.upgrade` gone, the docs above updated.

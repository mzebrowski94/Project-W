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

## Answer

Done 2026-10-03, one commit per package: motion, commander, creature, combat, death, level, appearance,
then upgrade (which emptied the flat package), then the docs. `sim.system` holds no class of its own and
`sim.upgrade` is gone; ten fenced packages are in `ArchitectureTest.FENCED`, six ports in `PORTS`
(`Crews`, `Deaths`, `Grants`, `Guns`, `Throws`, `Modifiers`).

- **What compile asked for, beyond 04:** `Grants`' constructor and `apply` (`CrateSystem` builds its own),
  and `Deaths`/`Deaths.kill`. Motion, creature, level, appearance and upgrade needed nothing.
- **The transitional allowances are gone:** classes straight in `sim.system` and `sim.upgrade` fail the
  build. The upgrade rules point at `sim.system.upgrade`; `upgradesAreCalledAndDoNotCall` now refuses any
  other package under `sim.system` and any scenario.
- **Tests:** `CoopTest` went to the `sim` root, `ArenaBoundsTest` and `TerrainCollisionTest` to
  `sim.collision`; every other one sits with its package (03's answer, section 7).
- **Scripts:** `component-index.py` globs only `system/`; `system-graph.py` reads real subpackages and no
  longer counts an import a javadoc link brought in (`Rouse` -> `Guns`) as a use.
- **Docs:** `docs/architecture.md` has the package table and the placement rule in place of the `system/`
  and `upgrade/` paragraphs; the `new-system` and `unit-tests` skills, `docs/design/README.md`,
  `docs/conventions.md` and one `CLAUDE.md` row follow. ADR 0017's text is left as written - its status
  says it is amended.

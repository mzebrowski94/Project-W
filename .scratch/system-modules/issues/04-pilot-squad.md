# 04 - Pilot: the squad as the first fenced package

Type: task

Blocked by: 01, 03

## Question

Move the squad systems and their helpers into `sim.system.squad` with the fences, and learn what the
moves break before doing the rest.

## Read first

`.scratch/system-modules/map.md` (03's split), `.scratch/system-modules/adr-0020-draft.md`,
`sim/src/test/.../sim/ArchitectureTest.java`, `docs/simulation-order.md`. Skills: `unit-tests`, `verify`.

## Steps

1. The fence rules first, red: every system in a fenced package; a package uses only `query`, the shared
   packages and declared ports; `query` writes nothing (01's analysis). `.because(...)` naming ADR 0020,
   and `allowEmptyShould(false)` - a rule over a renamed package must fail, not pass on nothing. Add
   `allowEmptyShould(false)` to the existing rules naming `..sim.system..` and `..sim.upgrade..` too.
2. Create `sim.system.query` with what 03 put there (`split.txt`); move `Odds` to the `sim` root; move the
   squad classes and their tests (03's answer, section 7); `package-info.java` for both. `TrooperMoveSystem`
   is **not** squad - it goes to `motion` in 05. Measured with `system-graph.py` on a pilot split
   (`query` + `squad` out, the rest still flat): besides the `query` helpers and `Odds` (`roll`,
   `possible`, `spent`), the squad reaches `Crews.board`/`leave`, `Guns.ready`/`pull`/`volley` and
   `Throws.reach`/`release` - public classes with package-private methods, which become public. Re-run the
   script after the move; its *Must be public* list is the check.
3. `verify.sh`. `SystemOrderTest` reads the constructor and should not care - confirm it, and confirm the
   component index still lists every system.
4. Move `adr-0020-draft.md` to `docs/adr/0020-systems-live-in-fenced-packages-by-domain.md`, its row in
   `docs/adr/README.md`, 0017's status line in both places. Glossary: none of these words are game
   domain - skip unless `docs/glossary.md` has a section they belong in.
5. `docs/conventions.md`: a table of the three annotations - `@NotState`, `@ConfigKey`, `@Reads` - what
   each claims and what enforces it.

## Find out, and record under Answer

What broke that 03 did not predict, and whether anything in the split should change before 05.

## Done when

`verify.sh` green, ADR 0020 in `docs/adr/`, the answer in `map.md`.

## Answer

Done 2026-10-03. `query` (seven helpers) and `squad` (three systems, seven helpers) are fenced packages;
`Odds` is in the `sim` root. ADR 0020 is in `docs/adr/`, 0017's status says it is amended, and
`docs/conventions.md` has the annotations table.

**The fences** (`ArchitectureTest`, each `.because` naming ADR 0020 and failing on zero classes):
`nothingUnderSystemLivesOutsideADeclaredPackage`, `everySystemLivesUnderSystem`,
`aFencedPackageUsesOnlyQueryAndPortsOfAnother`, `queryIsPublicAndHoldsNothing`, `queryWritesNothing`. The
fenced packages are `FENCED`, the ports `PORTS`. `queryWritesNothing` uses a new
`ComponentWriters.writtenBy(classes, type)`: ADR 0019's analysis asked of one class rather than credited
to systems. Seen failing on a planted violation (a squad class naming `NavigationSystem`, `RallyPoint`
writing `ControlPoint`). The three older rules naming `..sim.system..`/`..sim.upgrade..` say
`allowEmptyShould(false)` - ArchUnit's default already, now written down.

**Transitional, for 05 to remove:** `HOMES` admits classes straight in `sim.system`, and
`everySystemLivesUnderSystem` admits `sim.upgrade`. `PORTS` holds what crosses today - `Crews`, `Guns`,
`Throws` - and grows as packages leave the flat one. The flat `package-info.java` names those ports.

**What broke that 03 did not predict:**
- *A member, not a class.* `Compass.SLOTS` is a constant `TrooperObjective` and `AvoidanceSystem` read; the
  graph sees classes, so it could not say. Compile is the check for members - constants as well as
  methods.
- *Javadoc links.* `Pace` linked `{@link Aid}` and `TrooperCombatSystem` `{@link Muzzle}`. Moved across
  the line, a link to a package-private class fails Error Prone. `.claude/scripts/move-classes.py` (new,
  used for every move here) now turns such a link into `{@code}` rather than importing it.
- Predicted and as predicted: `Crews` public; `Crews.board`/`leave`, `Guns.ready`/`pull`/`volley`,
  `Throws.reach`/`release`, `Odds.roll`/`possible`/`spent` public; `ReachTest` sets `Crate.openedBy`
  instead of calling `Crates.open`.
- Not broken: `SystemOrderTest` (imports only) and the component index (unchanged - it already globbed
  `system/` recursively). Nothing else in `core`, `ui` or `net` imported a moved class.
- `Odds` left `systemsHoldNoStateAcrossTicks`'s sight with `sim.system`. Its fields are final; nothing to do.

**Should the split change before 05?** No. `system-graph.py --split split.txt` gives the same ports as 03.

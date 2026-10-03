# 01 - Every component names its writers

Type: task

## Question

Make "which systems write this component" something the build checks, before any file moves.

## Read first

`.scratch/system-modules/map.md`, `.scratch/system-modules/adr-0019-draft.md` (the rule this builds),
`docs/components.md`, `.claude/scripts/component-index.py`, `sim/src/test/.../sim/ArchitectureTest.java`
(how its custom `ArchCondition`s are written), G0010 in `docs/design/decisions/`. Skills: `unit-tests`,
`tdd`.

## Settled

- Declared in a *Written by* column of `docs/components.md`; the test fails on any difference, either way.
- A write is a field set from outside the component class, or a call to a changing method (sets a field of
  its own class, or calls one that does - a fixed point over the component's methods).
- Credited to the system whose methods reach the write, following calls through non-component classes in
  `sim`. A write no system reaches is credited to the class that makes it (`Simulation.step` -> `Intent`).
- Not a write: `Spawner`, a component's own `reset()`, `WorldCodec`.
- Per component. Scope: `sim` main classes.

## Steps

1. A failing test on a small case: a system that sets `Velocity.x` and one that calls `Damage.land` are
   both credited; a system that only reads is not; a helper's write is credited to the system calling it.
2. The analysis, as an `ArchCondition` or a helper the test owns.
3. Run it once as a report: actual writers per component. Fill the column from it - the first
   declaration is the measurement, reviewed by Mateusz in the diff.
4. `component-index.py`: keep the *Written by* column when regenerating; glob `system/` recursively and
   include `sim.upgrade` (both needed for 04, and `UpgradeSystem` is missing from the index today).
5. Move `adr-0019-draft.md` to `docs/adr/0019-a-component-names-its-writers.md` with the real test name
   under *Enforced by*; add its row to `docs/adr/README.md`. Point the `new-system` skill's "regenerate the
   index" step at the column.

## Find out, and record under Answer

- How many components have more than one writer, and which - decides the deferred *Why several* note.
- What `scenario/` and `Simulation`'s constructor write while assembling a world. Default: treat as
  assembly like `Spawner` and exclude; list them if that hides something.
- Whether lambda bodies inside a system are credited to it by ArchUnit's call graph.
- Any writer that surprises - a candidate for an `architecture-review`-style deepening, like 01 was.

## Done when

`verify.sh` green, the column filled, ADR 0019 in `docs/adr/`, answers above written here and the gist
added to `map.md` *Decisions so far*.

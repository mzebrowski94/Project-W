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
2. Create `sim.system.query` with what 03 put there; move the squad classes and their tests;
   `package-info.java` for both.
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

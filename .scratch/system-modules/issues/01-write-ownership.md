# 01 - Every component names its writers

Type: task

## Question

Make "which systems write this component" something the build checks, before any file moves.

## Read first

`.scratch/system-modules/map.md`, `docs/adr/0019-a-component-names-its-writers.md` (the rule this builds),
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

## Answer

Done 2026-10-03. `ComponentWriters` (sim test) measures; `ComponentWritersTest` pins it on fixtures in
`sim.writers`; `ArchitectureTest.everyComponentIsWrittenByExactlyItsDeclaredWriters` compares it with the
column, and was seen failing both ways. Two readings taken with Mateusz before building: a changing call
on a collection a component field holds is a write (`UpgradeSystem` and `Picks` edit `Drafts` and
`Upgrades` that way, nothing else does); a call to `reset()` is a write (`DamageSystem` clearing the
tick's blows).

- **Several writers: 49 of 79.** 37 without the two broad ones, `CooldownSystem` (17 components - every
  timer counts down there) and `EvolutionSystem` (24). The most: `Velocity` 16, `Position` 13, `Steering`
  10, `Weapon` 9, `Heat` 8, `Level` 8, `Collider`, `Stunned`, `Vitality` 7. One writer: 20. None after it
  is built: `Commander`, `Dead`, `Enemy`, `Faction`, `Focus`, `Handle`, `Obstacle`, `Post`,
  `RespawnsOnDeath`, `SlicksOnDeath`. So no *Why several* note - a note on most rows is the table nobody
  reads; ADR 0019 rejects it. That also meets the draft's *Revisit when* ("most components turn out to
  have several writers") on a literal reading; the ADR landed as drafted and the question is in map.md's
  fog.
- **`scenario/` and `Simulation`'s constructor.** The constructor writes nothing. Scenarios write only
  while laying out the level - `Level.require`, `Waves.add`/`holdBack`, `ControlPoint.holdAfterwards`,
  `Lair.at`/`unit`/`nest`/`pickup` - and every one of those components has a system writer besides, so
  treating them as assembly hides nothing. `Simulation`'s `spawn` methods (a throwaway `Weapon` for a
  test's projectile) are assembly too. `Simulation.step` stays a writer: `Intent`, `Invulnerable`,
  `Level`, `Presence`.
- **Lambdas: yes.** ArchUnit 1.5 attributes a lambda body's accesses to the method that declares it, in a
  system and in a helper alike (`creditsWritesInsideLambdasToTheSystemTheyRunFor`).
- **Surprises.**
  - *`Spawner` is a writer mid-tick.* Its public API is `spawn...` plus `applyWeapon` (pickup, crate,
    `Grants`), `applyStrain` (`EvolutionSystem`) and `markDead` (`Deaths`). Excluding the whole class, as
    the ticket's *Settled* line reads, hid three systems' writes to a unit's whole kit - `EvolutionSystem`
    alone becomes a writer of 24 components. Taken as the draft ADR words it: *building* is excluded,
    a system asking `Spawner` to change an existing entity is that system's write.
  - *Components used as scratch.* `PerceptionSystem`, `NavigationSystem` and `EnemyRangedSystem` keep a
    `private final Position` as a vector to fill; the bytecode cannot tell, so they are declared as
    writers of `Position`. A deepening candidate: a plain scratch type, or `Vector2`.
  - *`SlickSystem` writes `Body`* - a slick grows its radius in place.
  - *`Velocity` has 16 writers* although G0010's pipeline has behaviours write `Steering` and `Drive`
    turn it into velocity: knock-backs, kicks, boarding, grips and stuns all set it directly. The
    candidate most like `architecture-review` 01.
- **Found on the way.** `:sim:test` did not declare the docs it reads as inputs, so a doc-only edit left
  the task up to date and the check silently unrun (true of `SystemOrderTest` since it was written).
  Fixed in `sim/build.gradle`.

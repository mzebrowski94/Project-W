# 03 - Measure the graph and choose the split

Type: research

Blocked by: 02

## Question

Which fenced packages, holding which classes, leave the fewest public helpers - and what are the ports?

## Read first

`.scratch/system-modules/map.md` (the sketch to test, the rules it must obey),
`.scratch/system-modules/adr-0020-draft.md`, `docs/simulation-order.md`, the *Written by* column ticket 01
added to `docs/components.md`.

## Settled

Coarse (about eight plus `query`), by domain, under `sim.system.<domain>`, `upgrade` among them. A package
uses only `query`, the shared packages and other packages' ports. `query` writes nothing. A system goes to
the package whose components it mostly writes.

## Deliver

1. The use graph in `sim.system` and `sim.upgrade`, code references only (comments and javadoc
   stripped) - a script in `.claude/scripts/` or a throwaway test; say which and why.
2. A split: package -> systems, helpers, ports. Every class that must be public, and why.
3. Where each test in `sim/src/test/.../sim/system/` goes. Tests reaching package-private helpers in two
   packages are the hard case - name them and propose what each becomes.
4. What becomes of the one direct call between systems, `TrooperMoveSystem` -> `NavigationSystem.spreadOut`.
5. Where `AppearanceSystem` and `Pace` go (the sketch has no answer).
6. Every place a path is hard-coded and must change: `ArchitectureTest`, `SystemOrderTest`,
   `component-index.py`, the `unit-tests` and `new-system` skills, `docs/architecture.md`, `CLAUDE.md`,
   the ADRs and glossary that name `system/` or `sim.upgrade`, and `ui`, `core` and `net` importing
   `sim.upgrade`.

## Done when

The split is in `map.md` *Decisions so far* in place of the sketch, approved by Mateusz.

## Answer

Approved by Mateusz 2026-10-03, all four open points as recommended (see *Left for Mateusz*).

### 1. The graph

`.claude/scripts/system-graph.py`, a script rather than a throwaway test, for two reasons. 04 and 05 need to
re-run it against `split.txt` after every move, and a test would have to be kept green while the split is
half done. It reads **source**, comments and strings stripped, not bytecode: javac inlines compile-time
constants, so the bytecode loses references a split has to keep. Outside users count only through an
import, which keeps name clashes out (`java.lang.annotation.Target`, a local `Effect` in `CardText`). With
no arguments it prints every class's uses, users and writes; `--split FILE` tests a split.

### 2. What the measurement changed

- **The placement rule (decision 6) does not place.** Read literally ("the package whose components it
  mostly writes"), 23 of 52 systems would move against the sketch, and to nonsense: `MovementSystem` to
  combat, `BlastSystem` to combat, `AppearanceSystem` to creature (because `EvolutionSystem` writes
  `Appearance`). Shared state - `Velocity` 16 writers, `Position` 13, `Steering` 10, `Weapon`/`Heat` 7 -
  swamps everything else. Leaving out `CooldownSystem` and `EvolutionSystem`, **30 of 52 systems write no
  component that is theirs alone.** Writes are what ADR 0019 checks for ownership; they cannot say where a
  system lives.
- **What actually binds classes together is the package-private helpers.** Proposed rule, in order:
  (1) with the helpers it uses that are not `query`; (2) failing that, with the component it alone
  writes; (3) failing that, the glossary section that names what it does.
- **Tests do not reach helpers.** Every test in `sim.system` drives the public `Simulation`. The only ones
  naming a helper are `OddsTest` and `ReachTest` (plus `DealerTest`, `FoldTest` and `RotationTest`, which
  stay with `upgrade`). Where a test goes is tidiness, not a fence problem. map.md's "~70 tests reach
  package-private helpers" was wrong.

### 3. The split (`../split.txt`)

| Package | Systems | Helpers | Ports |
|---|---|---|---|
| `query` | - | `Drivers`, `Sight`, `Compass`, `Reach`, `Pace`, `Aim`, `RallyPoint` | all (public by definition) |
| `motion` | Drive, Movement, Avoidance, Navigation, Weave, Hop, Dodge, Wander, Stun, Facing, PositionHistory, **TrooperMove** | - | - |
| `squad` | SquadOrder, TrooperMind, TrooperCombat | `TrooperAid`, `Crew`, `Fight`, `Mend`, `Objective`, `Orders`, `Roam` | - |
| `commander` | CommanderControl, Melee, Strike, Drag, Action, Aid, Crate, Turret | `Aid`, `Crates`, `Crews` | `Crews` |
| `creature` | Perception, Grip, EnemyRanged, EnemyLeap, Evolution, Barrage | - | - |
| `combat` | Collision, Damage, Shock, Grenade, ProjectileBounds, Cooldown, Pickup | `Guns`, `Muzzle`, `Throws`, `Grants`, `Bounce` | `Guns`, `Throws`, `Grants` |
| `death` | Casualty, Dying, Respawn, Bloat, Mine, Blast, Slick, Reaper | `Deaths` | `Deaths` |
| `level` | Reinforcement, Wave, Nest, Lair, ControlPoint, Objective | `Rouse` | - |
| `appearance` | Appearance | - | - |
| `upgrade` | Upgrade | as today | `Modifiers` (0017's entry points) |

`Odds` leaves `sim.system` for the `sim` root, beside `Rng` and `Streams`.

**Public, and why.** Newly public: the seven `query` helpers; `Crews` (the commander and a trooper must
crew a gun the same way, G0024, so `TrooperCrew` uses it); `Deaths` (the one death path - `DamageSystem`
uses it, and ADR 0020's draft already names it as a port); `Odds`'s `roll`, `possible` and `spent` (it is
leaving the package its callers are in). Public already, and staying so: `Guns` and `Throws` (everybody
fires, and `Simulation` builds them), `Grants` (`ui` reads `worthTo`), `Modifiers` and the upgrade types
that `SimConfig`, `core`, `ui` and `net` use. Compared with today that is **nine more public classes**,
and no system used from another package.

**Into `query`** - each writes no component: `Aim` (pure maths into a `float[]`), `Pace` (reads terrain and
`Slowed`), `RallyPoint` (reads `ControlPoint`s and returns a `TileRect`), `Compass` (a slot's bearing),
`Reach`, `Drivers`, `Sight`. `Life` is a `query` in all but address; it stays in the root because `Players`
and `ui` ask it.

**Not `query`:** `Odds` writes no component but advances the `NERVE` stream, so it is not stateless.
`Bounce`, `Muzzle` and `Rouse` write; each has one home.

### 4. `TrooperMoveSystem` -> `NavigationSystem.spreadOut`

`TrooperMoveSystem` moves to `motion`. It is the squad's steering behaviour - it writes `Steering` and
`Position`, as `Hop`, `Weave` and `Wander` are the aliens' and live in `motion` - and every helper it uses
is `query` or `motion`. The call becomes an ordinary call inside one package: no port, and no new class.

### 5. `AppearanceSystem` and `Pace`

`Pace` is a `query`. `AppearanceSystem` reads everything and writes only `Appearance`, for the renderer
(G0003); none of the three rules places it. Proposed: a package of its own, a named exception to the
founding rule. Backlog D3 may take it out of the simulation altogether, and a package of its own is what
that move would want.

### 6. Hard-coded paths

- `ArchitectureTest`: `systemsHoldNoStateAcrossTicks` (`..sim.system..` or `..sim.upgrade..` -> the
  second goes), `onlyTheUpgradePackageTouchesTheLedgerAndTheDrafts`, `upgradesAreCalledAndDoNotCall` (its
  `..sim.system..` would catch `upgrade`'s own new home - becomes "no other fenced package"), and
  `UPGRADE = "pl.mzebrows.pwt.sim.upgrade"` (`everyStatIsReadInExactlyOnePlace`).
- `SystemOrderTest`: imports only; it reads the constructor. `ComponentWriters`: no package names.
- `.claude/scripts/component-index.py`: globs `system/` and `upgrade/` recursively - after 05 `upgrade/`
  is inside `system/` and the second glob goes. `system-graph.py`: its two package roots.
- Skills: `new-system` (line 33, the 0017 exception), `unit-tests` (line 10, an example path).
- Docs: `docs/architecture.md` (105, 150, 154), `docs/design/README.md` (9, 16), ADR 0017 (7, 10, 13,
  35-36) and its row in `docs/adr/README.md`. Glossary: none (`sim.upgrade.*` there are config keys).
- **Config keys `sim.upgrade.*` do not change** - they are keys in `assets/sim/`, not packages
  (`G0049`, `ScenarioSessionTest`, `TestWorlds` name them).
- Imports of `sim.upgrade`: 9 files in `core`, 8 in `ui`, 6 in `net`, 17 in `sim` outside the two
  packages. Of `sim.system`: one outside `sim` (`ui`'s `ReadoutWriter` -> `Grants`), plus `Simulation`.

### 7. Where the tests go

Beside the system they name; a test naming none goes with the package whose behaviour it asserts.

- `query`: `ReachTest` (its `Crates.open` becomes setting `Crate.openedBy`).
- `motion`: Avoidance-, Dodge-, Drive-, MovementSystem-, NavigationSystem-, PositionHistorySystem-,
  WanderSystemTest; `FacingTest`, `HopTest`, `StunTest`, `RunnerTest`, `ApproachTest`, `SquadSpreadTest`,
  `TrooperDetourTest`, `SlowTest`.
- `squad`: `SquadOrderTest`, `TrooperCombatTest`, `TrooperCrewTest`, `TrooperAdvanceTest`, `AidTest`,
  `MedkitTest`.
- `commander`: `ActionTest`, `CommanderControlSystemTest`, `CrateTest`, `DragTest`, `MeleeSystemTest`,
  `StrikeCombatTest`, `TurretTest`.
- `creature`: `AggroTest`, `AlienTargetingTest`, `AntTest`, `BarrageTest`, `EnemyLeapSystemTest`,
  `EvolutionSystemTest`, `PerceptionSystemTest`, `SpitterTest`.
- `combat`: `BazookaTest`, `CollisionSystemTest`, `CombatTest`, `DamageSystemTest`, `DiscTest`,
  `GrenadeTest`, `OverheatTest`, `PickupSystemTest`, `PierceTest`, `RabbitTest`, `ShieldTest`, `ShockTest`,
  `SweptShotTest`, `TallyTest`, `VitalityTest`, `VolleyTest`, `WeaponPickupTest`, `WoundCostTest`.
- `death`: `BarrelTest`, `BlastTest`, `BloatTest`, `CasualtyTest`, `DeathThroesTest`, `FireTest`, `MineTest`,
  `RespawnSystemTest`, `SlickTest`.
- `level`: `ControlPointTest`, `DefendTest`, `LairTest`, `NestTest`, `ReinforcementSystemTest`, `ReserveTest`.
- `appearance`: `AppearanceSystemTest`, `BeamTest`.
- `sim` (test root): `OddsTest` (follows `Odds`), `CoopTest` (four commanders, every domain).
- `sim.collision`: `ArenaBoundsTest`, `TerrainCollisionTest` - they test `ConstraintSolver`, which already
  lives there.

No hard case: no test reaches package-private code in two packages.

### Settled with Mateusz

- The rewritten placement rule: helpers, then the component it alone writes, then the glossary section.
  Decision 6 and ADR 0020's draft say so.
- `AppearanceSystem` as a one-system package, a named exception to the founding rule.
- Package names stay. The glossary-word rule applies to packages founded after 05; the founding ten are
  named by ADR 0020 and their `package-info.java`, and the glossary stays game words.
- The rest of the split, as above.

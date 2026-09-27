# Engineering backlog

Structural work we decided to defer, each with the reason it waited and what should bring it back. Not
gameplay: a stand-in for a mechanic belongs in `design/alpha-phase.md` (*Still scaffolding*), and an open
design question in `design/README.md`.

An entry is removed when it is done, or rejected in an ADR. Taking one on is a normal session: agree, plan,
tests first. Origin: the `sim` review of 2026-09-26/27 (`Claude outputs/sim-review.md`, item numbers in
brackets).

## Performance

| Item | Why it waited | Bring it back when |
|---|---|---|
| **One shared neighbour index** [B3, B4]. `SpatialGrid` is built twice per tick: by `ConstraintSolver` for pairs, and by `AvoidanceSystem` for its hazard queries. Everything else that looks for things nearby still scans a family: `DodgeSystem` (dodgers × shots), `TrooperFight`, `TurretSystem`, `MineSystem`, `BlastSystem`, `PickupSystem`, `Rouse`. The fix is one index owned by `Simulation`, rebuilt at the top of the tick, `@NotState`, with ties broken on spawn order. | At 800 entities a step is 4 ms of a 16.7 ms tick. Avoidance is 61% of that; the rest is linear and small (`performance.md`: theoretical at this scale). | The benchmark's 800 crowd passes 8 ms, or one of those systems shows up in a profile. |
| **Performance guard is opt-in** (`PWT_BENCH=1`, about 25 s). Its time and allocation checks do not run on every verify. | Wall time is noisy and needs a warm JIT; 25 s is a lot to add to a 3 s verify. | A regression slips through that the guard would have caught, or CI appears. Only the allocation check could move into the default run. |

## Architecture

| Item | Why it waited | Bring it back when |
|---|---|---|
| **Definitions copied into components** [C4]. `Spawner.applyStrain` copies about 60 values from a `UnitDef` into 32 components per grunt, through positional setters (`Perception.set`, `Leap.set`). Every snapshot and hash carries every tunable of every entity. The alternative: components hold state only and tunables are read through the kind (`config.unit(strain.kind).leap()`), so evolving just changes the kind. Migrate one component at a time. | A large mechanical change. Decided "skip for now" in stage 5. | Snapshot size matters online (drop-in over a slow link), or a positional setter misplaces a value. |
| **Presentation still in the deterministic state** [D3]. Only `Facing` was pulled out. `AppearanceSystem` (about 400 lines) still runs every tick on every peer, and `Appearance`, `Shake` and `Focus` are in the snapshot and the hash, so a cosmetic change is a simulation change. The alternative: the same pure function in `sim`, run by the client after `step`, not a system and not snapshotted. The G0003 contract does not change. | It needs a design call on what the client may compute. The part the rules depended on (`Facing`) is already out. | An online desync is traced to a cosmetic field, or snapshots need to shrink. |
| **ADR 0001 (Ashley): its revisit trigger is out of date** [D2]. It says "tens of thousands of entities". The real cost is working around Ashley: the spawn-order shim (ADR 0013), pooled references (solved by handles, ADR 0016), the fixed-composition rule that forces a superset of about 32 components with `enabled` flags on every alien, and reflection for the codec. | Changing a structural ADR is Mateusz's decision. Nothing forces a migration now. | Suggested new trigger: a second reflection shim into Ashley, or a feature blocked by the composition rule. |
| **`WorldCodec` still writes `Entity` fields** as a place in spawn order, although `HandleTest` ensures no component has one any more. | Dead code costs nothing yet. | Next time the codec is touched: delete the path and narrow `ArchitectureTest.componentFieldsAreWhatASnapshotCanWrite`. |
| **`Simulation`'s surface** [C6]: about 20 pass-through `spawnX` methods and a public `engine()`, so `core` can technically write the world. | Low priority; `UiArchitectureTest` already stops `ui` writing a component. | Give tests and scenarios one `spawner()` accessor, and give `core`/`ui` a read-only view. |
| **Trooper component split** [C3]. The 40 fields of `Trooper` would become orders / sight / walk-and-look / dues. | After the behaviour split every field has a visible owner. The split would cost 3-4 pooled components and about 90 rewritten accesses for little gain (stage 4). | The implicit mode-plus-booleans state produces a bug. |

## Agent workflow

| Item | Why it waited | Bring it back when |
|---|---|---|
| **Files too large to "read narrow"** [E5]: `AlphaWorlds` (test, 1585 lines), `Spawner` 1023, `SimConfig` 980, `AdventureScenario` 803. Target about 400 lines. | Splitting `Spawner` or `SimConfig` is structural; the comment sweep already took 20-27% off both. | A session has to read one of them whole to change a line. |
| **`game.properties` is 79 KB**, mostly design essays, so changing one number means reading about 18k tokens [E7]. Split it by domain (`units`, `weapons`, ...) through `generateShippedConfig`, or move the essays into `docs/design`. | The binder reads one file and the essays are Mateusz's tuning notes. | Next config-shape change, or when tuning sessions start. |
| **Comment sweep in the other modules.** `sim` went from 8,738 to 6,325 comment lines under the rule in `conventions.md`; `map`, `net`, `ui` and `core` have not been swept. | Out of scope for the `sim` review. | Next review of that module (see `Claude outputs/module-review-prompt.md`). |

# `sim` module - critical review and staged plan

## Progress (same session, after your decisions)

Decisions: `game.properties` is the truth · handles on Ashley · presentation leaves the snapshot · start with stages 0 and 1.

| Item | Done | Evidence |
|---|---|---|
| B5 benchmark | `SimulationBenchmarkTest` (`PWT_BENCH=1`) | Failed before the fix (28.9 ms > 16.7 ms tick) |
| B2 perception | `sides` family excludes `Enemy` (an enemy is only ever `ALIEN`, set at spawn) | Perception 23% -> 0% |
| B1/B4 avoidance | `SpatialGrid.near(...)` + per-tick hazard grid in `AvoidanceSystem`, exact superset reach | 800 crowd **28.9 -> 4.07 ms**, 400: 6.60 -> 1.55 ms |
| - | Behaviour unchanged | World hash of all 11 scenarios (1200 ticks) + a 300-grunt crowd **identical** before and after |
| A1 drift | `ShippedConfigTest` (core) + 20 defaults moved to the shipped values (seed excluded on purpose) | Test red with a readable diff, then green; all 1266 tests green |
| A4 grid | nest + turret in `largestRadius()`; `ConstraintSolver` throws on a body wider than the grid | 2 config tests + 1 solver test |
| B3 | Not done, on purpose: at 800 entities the step is 4 ms (Avoidance 61%, Navigation 19%, Collision 11%), within budget | `performance.md`: theoretical at this scale |
| A5 | Moved to stage 3 (handles), where it is fixed properly rather than guarded | - |

### Stage 2 (config and timers)

| Item | Done | Evidence |
|---|---|---|
| C1 one config source | `ConfigBinder` binds every record from `game.properties` by component name. `:sim:generateShippedConfig` compiles the file in, so `SimConfig.defaults()` **is** the file. The literal defaults (~530 lines), the hand loader (~560 lines) and `AiChance`'s own numbers are gone. | The file was proven to bind to exactly the old defaults before deletion. `ShippedConfigTest` fails on any unknown or missing key. A bad key in a player's file is logged and the shipped value is used. |
| C2 / A3 timers | Whole-tick countdowns (**not** the deadline model: that needed the current tick passed through 61 call sites; decided in chat). `Ticks.of` converts once where a duration enters. | **71 shipped durations had run a tick long** (flamer every 6th tick instead of every 5th). `TicksTest` was red and is now green. Determinism fuzz passes on 11 scenarios × 18k ticks. |
| - | Deliberately left as floats | Heat, bloat, casualty bleed-out (the rate changes while dragged), capture progress, hop/weave/strike phases: continuous quantities, not timers. |

### Stage 3 (entity handles)

| Item | Done | Evidence |
|---|---|---|
| D1 handles | Every entity carries a `Handle` (a `long` from `Level.nextHandle`, issued in `Spawner.admit`, never reused). `Handles.resolve` is a binary search over the handle family, which is sorted because families keep spawn order. ADR 0016. | `HandleTest`: handles rise in spawn order; a reaped entity resolves to nobody even when its pooled object is reused. |
| A5 | `Strike.owner` and `Damage.locksOnDeath` are handles. | Regression test: a swing whose owner was reaped no longer follows the grunt that reused his object. |
| Two ad hoc id schemes | The drag counter on `Level` and the turret counter in `Spawner` are gone; drags and crews use handles. | This fixed a latent bug: `Spawner.nextStationId` was not in the snapshot, so a restored world could issue a duplicate turret id. |
| Enforcement | `HandleTest.noComponentHoldsAnEntity`: a component field of type `Entity` fails the build. | Fuzz: 11 scenarios × 18k ticks with restores, green. |

Stage 1 deliverable: verify, propose, plan. **No production code changed.** Baseline `:sim:test` green (864 tests).
Numbers below come from throwaway probes run in this session and removed afterwards.

## What is already good

Better than most indie codebases, and not worth changing: module split with architecture tests, fixed tick,
one seeded `Rng` stream per concern, spawn-order shim + determinism, round-trip and fuzz tests, a reflection
reset test for every component, a snapshot codec derived from fields, and cached mappers/families everywhere.
The findings below are what the next 6 months will run into, not what is broken today.

---

## A. Verified defects (evidence, not opinion)

| # | Finding | Evidence | Severity |
|---|---|---|---|
| A1 | **Tests run a different game from the one that ships.** `SimConfig.defaults()` (all 864 sim tests) and `assets/game.properties` (the game) disagree on **21 values**. `ConfigDefaultsTest` only checks the loader's fallback path, so nothing notices. | Probe diff: `grunt.health 100 -> 150`, `grenade.blastRadius 12 -> 6`, `mine.fuseSeconds 1.1 -> 2.5`, `mine.triggerRadius 3.5 -> 2`, spitter/frog `alwaysAggressive true -> false`, `chances[1] 0.4 -> 0.8` (global and 4 strains), `weapons[4].bloatPerShot 0.017 -> 0.06`, `weapons[7].slicks.fadeFraction 0 -> 0.3`, spitter wander x5, seed. | High |
| A2 | **Projectiles tunnel.** Collision checks overlap at the end of each tick only; nothing is swept. Laser moves 3.17 u/tick, and the overlap window for a barrel is 2.6 u. | 20 shots per lateral offset at a barrel: centre **16/20 hit**, 0.6 u 14/20, 1.0 u 10/20, 1.2 u 6/20. Turret gun (2.5 u/tick) misses glancing hits on a runner the same way. | High (gameplay) |
| A3 | **Timers depend on float rounding.** `CooldownSystem` subtracts a float `dt` until it reaches 0. | float32 run: 0.05 s = 3 ticks (exact), **0.1 s = 7** (ideal 6), 0.2 s = 13 (12), 1.1 s = 67 (66), 0.5 s = 30 (exact). A tuner changes 0.10 to 0.11 and gets the same result. Deterministic, but it lies to whoever tunes it. | Medium |
| A4 | **The grid's cell size can miss contacts.** `SimConfig.largestRadius()` leaves out `nest.radius` (2.4) and `turret.radius` (2.2). Obstacle (3.5) covers both today, so nothing fails yet. Raise a nest above 3.5 and contacts drop silently. The javadoc claims it "cannot be forgotten". | `SimConfig.java:712` | Medium (latent) |
| A5 | **`Strike.owner` holds an `Entity` across ticks**, which is the rule G0004/G0010/G0016 exist to prevent. If the owner is reaped mid-swing and the pooled `Entity` is reused that tick (blasts and slicks spawn in the death window), the arc re-centres on a stranger and the lock hits the wrong entity. | `StrikeSystem.java:39`, `CollisionSystem.java:480` | Low (rare), but it shows the pattern |

## B. Performance

Step time with N grunts, 8 troopers and 1 commander on a 600x300 plain map, 300 ticks after 200 warm-up:

| entities | ms/step | Avoidance | Perception | Navigation | Collision |
|---|---|---|---|---|---|
| 105 | 0.70 | 40% | 20% | 15% | 11% |
| 194 | 2.24 | 54% | 23% | 11% | 5% |
| 368 | 7.28 | 62% | 24% | 7% | 2% |
| 794 | **30.8** | **69%** | **23%** | 3% | 1% |

Doubling the entities makes a step about 3.5x slower, so this is quadratic. The budget at 60 Hz is 16.6 ms,
and lockstep means **every peer** pays it. `maxTicksPerFrame=5` catch-up will spiral from ~500 entities up.
Ashley itself is not the problem.

| # | Cause | Fix | Expected |
|---|---|---|---|
| B1 | `AvoidanceSystem`: each steerer checks **every** `Position+Body+Collider` entity (projectiles, slicks and pickups included). | A shared per-tick spatial index with `query(x, y, r, visitor)`. | Most of the 69% is gone. |
| B2 | `PerceptionSystem` -> `Drivers.nearestLivingTarget(sides, ...)`: `sides` is every `Faction+Health` entity, **every alien included**, only to find the ~10 player-side ones. | Collect player-side huntables once per tick. `NavigationSystem.collectGoals` already does exactly this, so share it. ~20 lines. | 23% -> about 0 |
| B3 | Same pattern at lower weight: `Dodge` (dodgers x shots), `TrooperMind.enemyFor`/`somethingToShootFrom`, `Turret` quarry, `Mine`, `Blast`, `Pickup`, `Rouse`. | Through the index from B1. | Scales linearly |
| B4 | `SpatialGrid` exists but only answers "every pair". It is private to `ConstraintSolver`, so every other system falls back to a linear scan, against the project's own "no O(N^2)" rule. | Make it the shared index (B1), owned by `Simulation`, rebuilt at start of tick and marked `@NotState`. Ties break on spawn index so results do not depend on cell order. | Structural |
| B5 | No regression gate: the "0.8 ms" figure in ADR 0013 is a single hand measurement. | Opt-in `PWT_BENCH=1` crowd benchmark, like `DeterminismFuzzTest`, printing the table above. | Keeps it fixed |

Fine as it is (checked, no action): flow fields (lazy, one BFS per strain per tick, 6.5k tiles), `SpawnOrder`
cost, mapper caching, and allocation in the loops I read.

## C. Maintainability and extensibility

| # | Finding | Proposal |
|---|---|---|
| C1 | **Three sources of truth per tunable**: the record field, a positional literal in `defaults()` (for example `TrooperConfig` takes **31 unnamed floats**), a hand-written line in `SimConfigLoader` (612 lines), and the `.properties` line. That is 4-5 edits in 2 modules per new number, and A1 is the result. | One source: `game.properties`. A small binder in `sim` (pure: text in, `SimConfig` out, no I/O) maps `sim.trooper.radius` -> `TrooperConfig.radius` by record-component name, once at load. Missing or unknown key = fail loudly. Tests load the shipped file; tests that want other numbers use the existing `with...()`. Deletes the `defaults()` literals (about 530 lines) and most of the 612-line loader. |
| C2 | Float countdown timers, centralised in `CooldownSystem` (15 families, about 20 fields). A new timer means editing that system. Some are counted down elsewhere (`Trooper.alertRemaining` in `TrooperMindSystem`). | **Absolute tick deadlines** (`long readyAt`, compared with `level.tick`). An idle timer costs nothing, durations are exact (fixes A3), and `CooldownSystem` goes away. Seconds -> ticks conversion happens once, in the config binder. |
| C3 | `Trooper` has 40 fields: mode plus 6 booleans that form an implicit state machine (`holding`, `sampling`, `settled`, `sentOn`, `hasTarget`, ...). `TrooperMindSystem` is 888 lines of `if (x) continue;` cascade. Every trooper feature edits both. | Keep one system, but split the cascade into ordered `Behaviour` objects (`fight`, `obey`, `aid`, `crew`, `hold`, `secure`, `explore`) of about 100 lines each behind one interface. Split the component by concern (sight / orders / errand). Same determinism, same order, and a task reads one file. |
| C4 | Definitions are copied into components: `applyStrain` copies about 60 def values, through positional setters of 7-9 floats (`Perception.set`, `Leap.set`), into 32 components per grunt. Every snapshot and hash carries every tunable for every entity. | Components hold **state**; tunables are read through the kind (`config.unit(strain.kind).leap()`). Evolution becomes "change the kind". Do it incrementally, one component at a time. Cost: one lookup per access, and troopers/turrets need their own def route. |
| C5 | System order constraints live in comments and a doc. The doc is in sync today (checked, 45 = 45), but nothing checks the *constraints* ("avoidance before dodge", "bounds before death reactions"). | Named phases plus a `SystemOrderTest` that states each constraint as `before(A, B)` data. A reorder that breaks one fails the build, in the project's own "enforced, not remembered" style. |
| C6 | `Simulation` has about 20 pass-through `spawnX` methods and a public `engine()`, so the client can technically write the world. | Low priority: one `spawner()` accessor for scenarios and tests, and a read-only world view for `core`/`ui`. |

## D. Architecture decisions, re-examined

**Keep**, and I would argue for them again: `map`/`sim`/`net`/`ui` split (0003, 0009, 0015), lockstep (0012),
circles only with your own collision (0002, 0007), typed definition tables (0008), spawn order (0013), and a
snapshot derived from fields (0014).

**Change or add:**

1. **Stable entity references (generational ids).** Three G-records and a CLAUDE.md checkbox exist only
   because a pooled `Entity` comes back as something else. So AI stores positions instead of targets (G0010),
   and A5 still slipped through. Give every entity an `int` handle made of index and generation, handed out at
   spawn from a counter on `Level` (snapshotted), plus a per-tick table from handle to `Entity`. Components hold
   handles and `resolve(h)` returns null once the entity is gone. You keep Ashley, the rules become "hold a
   handle, never an `Entity`", and target lock, "who shot me" and owner links become safe features instead of
   design exceptions.
2. **ADR 0001 (Ashley): keep for now, but change its revisit trigger.** Measured: Ashley iteration is not the
   cost; algorithms are. The real cost of Ashley here is that you are fighting it on four fronts: a reflection
   shim for ordering, pooled references (-> 1), the fixed-composition rule forcing a 32-component superset on
   every alien with `enabled` flags (so `Family` filtering does nothing), and reflection for the codec. A small
   in-house store (dense arrays, handles, spawn-ordered) would remove all four, but it means migrating 45
   systems and 67 components. **Not worth it before B and C1-C2 land.** Proposed new trigger: "a second
   reflection shim into Ashley, or a feature blocked by the composition rule".
3. **Presentation out of the deterministic state** (optional, needs a decision). `AppearanceSystem` (422
   lines) runs every tick on every peer, and `Appearance`/`Shake`/`Focus` are in the snapshot and the hash, so
   a cosmetic change is a simulation change and makes snapshots bigger. Alternative: the same pure function in
   `sim`, run by the client after `step`, not a system and not snapshotted. G0003's contract (the renderer asks
   nothing about kinds) stays the same.
4. **Swept projectiles** (fixes A2): a segment test from `PreviousPosition` to `Position` for non-solid movers
   only. Solids stay positional. Plus a `Require` in `SimConfig`: `speed * dt <= 2 * (projectileRadius +
   smallest radius)` for any gun that is not swept.
5. **Levels as Java** (`AdventureScenario` is 808 lines): fine for alpha. When layout, not systems, becomes the
   bottleneck, use the Tiled import ADR 0010 already leaves open. No action now.

## E. LLM workflow efficiency

| # | Friction (seen this session) | Fix |
|---|---|---|
| E1 | `.claude/scripts/*.sh` are committed as `100644`, so `verify.sh` fails with *Permission denied* in a cloud container. | `git update-index --chmod=+x .claude/scripts/*.sh` |
| E2 | Every fresh cloud container downloads JDK 25 and Gradle 9.7.1 from scratch (first build about 2 min). No SessionStart hook. | A SessionStart hook that runs `gradle.sh --version` in the background (the `session-start-hook` skill). |
| E3 | CLAUDE.md is written for the Windows mount: delete permission, `Mateusz commits`, never push. In a cloud session the container is thrown away, so work that is not pushed is lost. This session had to choose between the two rules. | A short "Cloud session" block: push to the `claude/*` branch, never to main, no PR unless asked; skip the delete-permission ask. |
| E4 | **39% of `sim` lines are comments** (8.6k of 22.3k); the whole module is about 1.1 MB, roughly 280k tokens. Much of it is narrative history ("until 2026-09-19", "what it replaced", 18 such passages), which the model pays for on every read and which goes stale. | Javadoc = contract + invariant + G-link. History goes to the G-record and git. Estimated 30-40% fewer tokens per file read, with no loss of rationale. |
| E5 | Files too large for "read narrow": `SimConfig` 1650, `Spawner` 1148, `TrooperMindSystem` 888, `AdventureScenario` 808, test `AlphaWorlds` 1589 lines. | Cap around 400 lines. C1 and C3 remove the two worst. |
| E6 | Touch points per change: new tunable = 5 edits (C1), new timer = `CooldownSystem` (C2), 31 positional floats (C1). These are exactly where a model silently misplaces a value. | C1 and C2 bring it to 2 edits. |
| E7 | `game.properties` is 72 KB, mostly essays: about 18k tokens to change one number. | Split per domain (`units/*.properties`, `weapons.properties`), or move the essays to `docs/design`. |
| E8 | Gates exist for determinism but not for drift or speed. | The drift test (A1) and the benchmark (B5) give the model a mechanical "no", in line with the repo's philosophy. |

---

## Plan: each stage can ship on its own and stop after any step

| Stage | Contents | Size | Risk |
|---|---|---|---|
| **0 - fixes** | A1 test that fails on drift + reconcile the 21 values (**you choose the side**) · A4 add nest/turret + an assert in the spawn path · A5 owner guard | S | Behaviour change = the 21 values |
| **1 - perf** | B5 benchmark first (baseline above) -> B2 perception (~20 lines) -> B1/B4 shared index -> B3 migrate the scans one system at a time, re-measuring each | M | Tie-break order changes results; determinism tests still pass (they compare runs, not goldens) |
| **2 - config** | C1 binder, delete `defaults()` literals and loader mapping, tests on the shipped file · C2 tick deadlines (fixes A3) | M-L | Wide diff, mechanical |
| **3 - references** | D1 handles; remove the "no Entity" rules; A5 fixed properly | M | Touches the codec |
| **4 - troopers** | C3 behaviours + component split · C5 order test | M | Pure refactor, covered by 20+ trooper tests |
| **5 - optional** | D3 presentation pass · D4 swept projectiles · C4 state/def split · E4 comment diet (by file, while touching it) | M each | D3 and C4 need a design call |
| **Workflow (any time, tiny)** | E1 chmod · E2 hook · E3 CLAUDE.md cloud block | XS | none |

## Decisions I need from you

1. **A1 direction:** is `game.properties` the truth (my recommendation: it is what you playtest), or `defaults()`?
2. **D1 handles on Ashley** (recommended), or migrate off Ashley now while it is cheapest?
3. **D3:** take presentation out of the snapshot, yes or no?
4. **E4:** is the comment diet OK? It changes the house style.
5. **Commit rule for cloud sessions** (E3).

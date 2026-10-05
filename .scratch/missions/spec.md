# Missions - spec

Assembled 2026-10-05 from the missions map (`map.md`). It names decisions and doesn't argue them: the reasoning is
in the tickets under `issues/` and in the G-records. Each stage below is one session that ends with `verify.sh`
green and can be the last for a while. Each stage has a start prompt to paste into a fresh session.

## What a mission is

One level, played in one go: a strip west to east of **4 biomes** (50-58 columns, 30 rows, no biome twice, never
the same base side by side) separated by **ridges** crossed by **passes**, then a **finale**. All of it is built
from (tier, mission seed). The finale is the only objective; 2-3 control points a biome lead the way. There are
three **tiers**: easy, medium and hard. A tier moves only the swarm's numbers. Each day brings three **daily
missions**. A mission is named by its code (`HK3X-Q7RA`). Online from the start. A machine remembers a daily as ✓
plus its best time.

## Decisions

| Ticket | Record | Settles |
|---|---|---|
| [01](issues/01-what-a-mission-is.md) | - | shape, seed, tier's scope, winning and losing, where it is played |
| [02](issues/02-how-a-biome-s-ground-is-laid.md) | - | the ground's coats and techniques (prototype `prototype/biome-ground.html`) |
| [03](issues/03-what-a-biome-definition-holds.md) | - | `BiomeGround`, `Strip.lay`, `BiomeDef`, `Roster.count`; the five blocks |
| [13](issues/13-what-lies-between-two-biomes.md) | - | ridge, pass, apron; gatehouses (moved to 12's stage by 11) |
| [06](issues/06-which-biome-a-tile-is-in.md) | G0062 | a biome is a grouping of regions; a nest wakes for its own biome; first brood |
| [07](issues/07-the-tiers-in-numbers.md) | G0063 | four numbers and a ramp; `MissionConfig`; `Whole.percentOf`; melee 220 |
| [04](issues/04-what-a-finale-definition-holds.md) | G0064 | a finale is a set piece; the redoubt and keep blocks; the door point |
| [05](issues/05-the-mission-seed-and-its-code.md) | G0065 | the code, the check, `daily()`, `Randomness.derive`, streams |
| [08](issues/08-menu-pages-and-the-result-screen.md) | G0066 | DAILY MISSIONS page; the result screen for every level |
| [09](issues/09-what-is-remembered-and-where.md) | G0067 | `MissionRecords`, written on the outcome tick by every machine |
| [10](issues/10-what-the-new-biome-skill-says.md) | - | the `new-biome` skill, its tests, porting, the living-skill step |
| [11](issues/11-the-build-in-stages.md) | - | this staging |
| [12](issues/12-rooms-inside-a-biome.md) | - | rooms and gatehouses (**open**, gates stage 5) |
| [14](issues/14-placement-inside-a-biome.md) | - | placement inside a biome (**open**, gates stage 7) |
| map.md *Notes* | G0061 | the held point never becomes the rally point; a march ends on a ring round the post (**to write**, stage 0) |

Glossary words, all settled: **mission**, **mission seed**, **daily mission**, **tier**, **biome**, **finale**,
**region**, **ridge**, **pass**, **apron**, **gatehouse**. Never *sector*, *run*, *map* or *difficulty* for these.

## Keys

Values and comments are in the tickets. Each stage adds the keys it needs, never earlier.

| File | Keys | Ticket | Stage |
|---|---|---|---|
| `assets/sim/level.properties` | `sim.nest.firstBroodSeconds=6`; the `wakeRadius` comment rewritten | 06 | 2 |
| `assets/sim/missions.properties` | `sim.mission.ground.{rows, apron, clearance, ridgeWidth, ridgeRough, passWidth, passesMin/Max, passApart, passJog, snake}` | 03, 13 | 3-4 |
| | `sim.mission.ground.{gatehouseShare, chamberWidth, chamberHeight}` and the room keys, as 12 settles them | 12, 13 | 5 |
| | `sim.mission.rampPercent=10`; `sim.mission.tier.{easy,medium,hard}.*` | 07 | 6 |
| | `sim.mission.biomes=4`; placement keys, as 14 settles them | 03, 14 | 7 |
| | `sim.mission.finale.doorColumns=8` | 04 | 8 |
| `assets/sim/biomes.properties` | `sim.biome.{plain, quarry, brake, anthill, badlands}.*` | 03 | 7 |
| `assets/sim/finales.properties` | `sim.finale.redoubt.*`; `sim.finale.keep.*` | 04 | 8; keep run |
| `assets/sim/weapons.properties` | `sim.melee.damage` 190 -> 220 | 07 | 6 |
| `assets/graphic.properties` | `graphic.ui.resultHoldSeconds=3` | 08 | 1 |

## Stages

| # | Stage | Gated by | Polish |
|---|---|---|---|
| 0 | Rally fix (G0061) | - | - |
| 1 | Result screen (G0066), every level | - | - |
| 2 | Nests wake by biome (G0062) | - | - |
| 3 | Biome ground (`map`) | - | draft |
| 4 | The strip (`map`) | - | draft |
| 5 | Rooms and gatehouses (`map`) | ticket 12 | draft |
| 6 | Tiers and the code | - | - |
| 7 | **First biome stage** | ticket 14 | **`SKILL.md` written** |
| 8 | Finale: the redoubt | 0, 7 | skill |
| 9 | DAILY MISSIONS page | 6 | - |
| 10 | Records | 1, 9 | - |
| 11+ | One `new-biome` run a biome, then the keep | 7, 8 | skill, every run |

Tickets 12 and 14 can be grilled while stages 0-4 are being built. If either runs long, its stage waits; it doesn't
ship on stand-in rules.

**Polish** is the `new-biome` amend step (ticket 10): what was wrong, missing or slow, fixed in the text, with one
dated line - "nothing" included. *Draft* means `new-biome-skill.md`, under its *Amended* heading. *Skill* means
`.claude/skills/new-biome/SKILL.md` and its `CHANGELOG.md`.

**Scaffolding** means rows of `docs/design/alpha-phase.md`, *Still scaffolding*. A stage adds the rows its stand-ins
need and deletes the rows it retires, in the same diff.

---

### 0 - Rally fix (G0061)

- **Ships**:
  - A point that holds afterwards (the finale's) never becomes the rally point.
  - A march ends on a ring round the post rather than its middle.
  - G0061 written, amending G0055.
- **Tests**: the smallest reproduction first, in the rally and march tests nearest `RallyPoint` and
  `TrooperObjective`. G0061's *Enforced by* names them.
- **Scaffolding**: none.

> Build G0061 for Project-W. G0055 makes the last control point taken the rally point. Fix two things: a point that
> *holds afterwards* (the Adventures' redoubt and keep posts) must never become the rally point, and a squad's march
> to a post must end on a ring round it, not its middle. Read `CLAUDE.md`,
> `docs/design/decisions/G0055-the-army-is-a-field-and-a-reserve.md`, `G0027`, `G0029`, `G0064` (*The rally point
> stays G0055's*), `docs/design/invariants/squad-and-objectives.md`,
> `sim/.../system/query/RallyPoint.java`, `system/level/ReinforcementSystem.java`,
> `system/squad/TrooperObjective.java`, and the redoubt and keep in `AdventureScenario` / `AdventureTwoScenario`.
> Settled: both rules. Open: how a point says it holds afterwards (a field on what already marks the post, not a
> branch on kind); the ring's radius and its key. Run `feature`, then `new-system` (tests first; a bug-shaped change,
> so start from the smallest reproducing test). Write G0061 and its invariant row. Then offer the Adventures to
> play.

### 1 - Result screen (G0066)

- **Ships**:
  - The win latched like `Level.failed`, so the first outcome is final.
  - `UI:play/result` (`UiPage.RESULT`) for every level with an objective: MISSION COMPLETE or MISSION FAILED, the
    time `m:ss.t` on a win only, RETRY (default; dimmed for a joined machine) and MENU (the host goes through the
    leave confirm, and MENU returns to the page the level was started from).
  - `graphic.ui.resultHoldSeconds` 3, no skip.
  - The mission rows (points, code, DAILY, NEW BEST, COPY CODE) aren't built here. They arrive in 7, 9 and 10.
- **Tests**:
  - won-then-wiped stays won, and lost stays lost;
  - the time is the outcome's tick;
  - `UiFlow` for RETRY, MENU and the return page;
  - `UiSpecTest` rows.
- ***Enforced by***: G0066.
- **Scaffolding**: retires "A lost level says MISSION FAILED on the goal line…" and "A level's objectives are met and
  nothing happens".

> Build the result screen of G0066 for Project-W, for every level with an objective; missions don't exist yet. Read
> `CLAUDE.md`, `docs/design/decisions/G0066-a-level-ends-on-its-first-outcome.md`, `.scratch/missions/issues/08-…`
> (*The result screen*), `docs/ui/menu_layout_agent.md` (`UI:play/game-over`, `UI:play/result`), the `Level`
> component, `ui/.../page/UiFlow.java`, and the pause and leave pages it reuses. Settled: everything in G0066; the
> mission rows (points x/y, code, DAILY, NEW BEST, COPY CODE) are left for later stages. Open: nothing in the
> design. Run `new-system` for the latch, then the page per `menu_layout_agent.md`. Retire the two scaffolding rows
> in `alpha-phase.md`. Offer an Adventure to play to the end.

### 2 - Nests wake by biome (G0062)

- **Ships**:
  - `TileMap` biome partition (`biomeAtTile`, `biomeAt`, `biomeCount`, `NO_BIOME`) and `Builder.regionInBiome`,
    with `build()` checking that the ids are dense.
  - `NestSystem` wakes on same biome and within `wakeRadius`.
  - `Nest.wake` starts its clock at `firstBroodSeconds`.
  - Both Adventures declare 7 biomes, with each wall in the biome to its east.
- **Tests**: the six G0062 names. The invariant rows in `aliens.md` and `map-and-view.md`.
- ***Enforced by***: G0062, all but the ridge test, which stage 4 adds.
- **Scaffolding**: none. The feel changes on purpose: an Adventure nest wakes when you come through the wall.

> Build G0062 for Project-W: a nest wakes only for somebody in its own biome and within `wakeRadius`, and its first
> brood comes `sim.nest.firstBroodSeconds` (6) after waking. Read `CLAUDE.md`,
> `docs/design/decisions/G0062-a-nest-wakes-for-its-own-biome.md`, `G0031`, `.scratch/missions/issues/06-…`
> (the whole *Answer*: code table, invariant rows, the Adventures' `paintRegions` line), `map/.../TileMap.java`,
> `sim/.../system/level/NestSystem.java`, `docs/design/invariants/aliens.md` and `map-and-view.md`. Settled: all
> of it. Open: nothing. Run `new-system` (tests first). The ridge-tile test waits for `Strip` (stage 4). Offer
> both Adventures to play: nests now wake as you come through a wall.

### 3 - Biome ground (`map`)

- **Ships**:
  - `Randomness.derive(long salt)`, with `Rng.stream` rewritten on it to give exactly today's values.
  - `BiomeGround`, `Patch` (`Rect`, `Blob`) over `PatchGround` (with its binder conversion), `Crags`, `Outcrops`,
    each with `off()`.
  - The four coats laid into one rect, apron and clearance honoured.
- **Tests**:
  - `Rng.stream` values unchanged;
  - retuning one coat leaves every other identical;
  - every coat lands within tolerance of its declared density;
  - nothing narrower than `clearance`, and no pockets, in one rect.
  - `docs/determinism.md` gains the stream rules.
- **Scaffolding**: none (test-only).
- **Polish**: the draft.

> Build the ground of one biome in Project-W's `map` module. Read `CLAUDE.md`, `docs/determinism.md`,
> `.scratch/missions/issues/02-…` and `03-…` (*Ground*, *Code*), `05-…` (*Streams*),
> `.scratch/missions/prototype/biome-ground.html` (its generator module is what lifts into Java),
> `map/.../Randomness.java`, `Scatter.java`, `Maps.java`, and `.scratch/missions/new-biome-skill.md`. Settled: the
> coats, their techniques and fields, density not count, clearance 2, `derive` from current state without
> advancing. Open: tolerances for the density test. Not in this stage: the strip, ridges and passes (stage 4).
> Run `game-patterns` (to record outcrops, crags and blobs), then `new-system`'s test-first shape (`map` has no
> systems). End by amending the draft skill (*Amended*: what was wrong, missing or slow, or "nothing").

### 4 - The strip (`map`)

- **Ships**:
  - `StripRules` and `Strip.lay(rules, order, streams)`, returning the `TileMap` and each biome's `TileRect`.
  - Ridges, bent passes and aprons, notches filled.
  - Each biome's regions given to it, and ridge tiles in the biome whose ground they show.
  - The finale's slot, with its ridge spilling west only.
  - Gatehouses are **not** built here.
- **Tests**:
  - `StripSweepTest`: 64 seeds; `PWT_SWEEP=1` runs 2000 and prints a failing seed's index (codes arrive in 6);
  - the ridge-tile membership test.
- ***Enforced by***: G0062's ridge test.
- **Scaffolding**: none (test-only).
- **Polish**: the draft.

> Build `Strip.lay` in Project-W's `map` module: biomes side by side, a ridge between every two and before the
> finale, 1-2 bent passes through each. Read `CLAUDE.md`, `.scratch/missions/issues/13-…`, `03-…` (*Strip rules*,
> *Code*), `06-…` (*Ridge tiles*), `04-…` (west-only spill), `10-…` (*Tests*, *Sweep size*), the prototype's
> border section, stage 3's `BiomeGround`, and `.scratch/missions/new-biome-skill.md`. Settled: ridge, passes,
> apron, clearance, membership, the sweep's assertions and sizes; gatehouses wait for stage 5. Open: the sweep's
> output format. Biome definitions aren't in `sim` yet, so test with literal `BiomeGround`s. Name G0062's ridge test
> in its *Enforced by*. End by amending the draft skill.

### 5 - Rooms and gatehouses (`map`)

- **Ships**: per ticket 12. Rooms as ground and gatehouses in 12's form, kept open by construction.
- **Tests**: `StripSweepTest` extended so every room's way in is reachable and its inside too, plus 12's own.
- **Scaffolding**: per 12.
- **Polish**: the draft. Section 2 gains rooms.

> Build rooms and gatehouses in Project-W's `map` module as ticket 12 settled them. Read `CLAUDE.md`,
> `.scratch/missions/issues/12-…` (*Answer*), `13-…`, stage 4's `Strip`, `StripSweepTest`, and
> `.scratch/missions/new-biome-skill.md`. Settled: 12. Not here: what stands in a room (placement, ticket 14, stage
> 7). Run `new-system`'s test-first shape. End by amending the draft skill.

### 6 - Tiers and the code

- **Ships**:
  - `TierKind`, `TierDef` and `MissionConfig` (`pressure`, `apply`).
  - `Whole.percentOf(base, percent, percent)`, with the javadoc widened to "a whole quantity".
  - `SwarmConfig.tierPercent`, the nest period folded in, adaptation pace, `sim.melee.damage` 220.
  - `core`'s `MissionCode`: tier plus seed to text and back, and the check. `RoomCode`'s alphabet and forgiving
    reading become one public helper.
  - `LaunchOptions` parses `--mission=<code>` and refuses a bad one.
- **Tests**:
  - `SimConfigTest`'s relational guarantees at every tier;
  - `WholeTest` for the two percents;
  - `MissionConfig` is the one reader;
  - `MissionCode` round-trips, every single wrong character and every swapped pair is refused, and every tier
    letter is in the alphabet and unique;
  - `LaunchOptionsTest`.
- ***Enforced by***: G0063, and G0065's code tests.
- **Scaffolding**: none. Nothing plays a mission yet.

> Build mission tiers and the mission code in Project-W. Read `CLAUDE.md`,
> `docs/design/decisions/G0063-…`, `G0065-…`, `.scratch/missions/issues/07-…` and `05-…` (*The code*, *Where each
> lives*), `sim/.../def/Whole.java`, `SimConfig`, `SimConfigTest`, `net`'s `RoomCode`, and
> `core/.../client/LaunchOptions.java`. Settled: the four numbers and the ramp, one reader, one rounding, melee 220;
> the code's format, alphabet and check. `--mission` is parsed here and wired to a scenario in stage 7. Open:
> nothing. Run `new-kind` for `TierKind`, and `new-system` for the fold.

### 7 - First biome stage

- **Ships**:
  - `BiomeKind`, `BiomeDef`, `Roster` (`Loose`, `Nest`) with `Roster.count`, `Furniture`, `Stock`.
  - `biomes.properties` with plain, quarry, brake, anthill and badlands.
  - The deal: none twice, never the same base side by side.
  - `RngStream.MISSION`.
  - Populate per ticket 14, room contents included.
  - `Scenarios.mission(tier)` and `Scenarios.named`'s mission form, with `--mission` wired to it.
  - The `PWT_MEASURE` runner.
  - The goal line leads through points.
  - **`.claude/skills/new-biome/SKILL.md` and `CHANGELOG.md`** written from the draft, and the draft deleted.
- **Tests**:
  - `MissionSweepTest`: points and crates reachable, plus 14's rules;
  - coverage: every kind dealt at every position;
  - the same ground at three tiers;
  - the build-time budget (median 100 ms, printed);
  - the five's number promises;
  - a mission joins `ScenariosTest` and `SimulationDeterminismTest` cases.
  - `PWT_SWEEP` prints codes.
- ***Enforced by***: G0065, the same-ground test.
- **Scaffolding**:
  - **adds** "A mission has no finale and no objective: the strip ends past the last ridge on bare ground"
    (retired in 8);
  - **adds** "A mission is started only by `--mission=<code>`" (decided in 9);
  - amends the `Scenario.adapting` row, since a mission takes its tier's pace;
  - reviews the hand-laid rows (rock, posts, barrels, spitters, ants): the Adventures stay hand-laid by decision
    (10), so their *Replaced by* names missions as the generated fragments.

> Build the first biome stage of Project-W's missions: five biomes as data, dealt into a strip, populated, playable
> from `--mission=<code>`. Read `CLAUDE.md`, `.scratch/missions/spec.md` (this stage), `.scratch/missions/issues/03-…`
> (the five blocks), `14-…` (placement), `12-…` (room contents), `05-…` (*Streams*), `10-…`,
> `.scratch/missions/new-biome-skill.md`, `.claude/skills/new-kind/SKILL.md`, and stages 3-6's `map` and tier code.
> Settled: the definitions, blocks, deal rules, placement, sweeps and their sizes, the build budget. No finale yet;
> the strip ends past the last ridge. Open: whatever building the five turns up, which goes into the skill. Run
> `feature` (placement is new play), `new-kind` for `BiomeKind`, `new-system` for populate. Finish by writing
> `SKILL.md` and `CHANGELOG.md` from the draft against what this took, deleting the draft, then offer Mateusz a few
> printed codes to play.

### 8 - Finale: the redoubt

- **Ships**:
  - `FinaleKind` and `FinaleDef` (`Hold`, `Mouth`, `WaveLine`, `LairLine`) in `finales.properties`, the redoubt only.
  - `BiomeGround.pieces`; the anchor computed in `map` in one place; the flip in populate.
  - Waves held back until the post is taken, counted through `Roster.count` unramped.
  - The last biome's point in its last `doorColumns`, and its nests outside the watch.
  - The goal line's wording across a mission, decided in `feature`.
  - The field measured.
- **Tests**:
  - `MissionSweepTest`'s finale assertions;
  - a wave naming a missing mouth is refused;
  - the flip mirrors north-south only;
  - a mission case in `SimulationBenchmarkTest` and a hard code on `F1`. Write a "how big the field gets" ticket
    only if frame time breaks the budget.
- ***Enforced by***: G0064.
- **Scaffolding**: retires "a mission has no finale". The redoubt's hand-placed turret, mine and wave rows stand
  for the Adventure.
- **Polish**: the skill's section 4 is written from this.

> Build the finale of Project-W's missions, the redoubt only, as a set piece. Read `CLAUDE.md`,
> `docs/design/decisions/G0064-…` and `G0061-…`, `.scratch/missions/issues/04-…` (the whole *Answer* and the redoubt
> block), `.claude/skills/new-biome/SKILL.md`, `AdventureScenario`'s redoubt, and `Waves`, `WaveSystem`,
> `ObjectiveSystem`. Settled: the shape, the offsets, the flip, the waves, the door point, no rally ground. Open:
> what the goal line says across a mission (run `feature` for it, with the HUD's mission line from ticket 08). After
> it plays, measure the field on hard (`F1`, `SimulationBenchmarkTest`), and write a ticket only if frame time
> breaks the budget. End with the skill's amend step; section 4 is written here.

### 9 - DAILY MISSIONS page

- **Ships**:
  - `MissionCode.daily(epochDay, tier)` with an injected UTC `Clock` in `core`.
  - `UI:main/missions` as the main menu's first entry and default.
  - Rows EASY, MEDIUM and HARD, the selected daily's code, RANDOM as a choice row, and ENTER CODE inline with an
    8-slot `CodePicker` whose first slot holds tiers only.
  - HOST GAME's DAILY MISSIONS.
  - The HUD's `DAILY HARD` and the pause page's code, tier and time.
  - The result screen's mission rows: DAILY and COPY CODE.
  - Decide whether `--mission` stays as a dev tool (it moves to the harness table) or goes.
- **Tests**:
  - a daily is fixed for a date;
  - a code equal to a daily is that daily;
  - `CodePicker` with an alphabet per slot;
  - `UiFlow` for the page, host mode and refusals (never a hint);
  - `UiSpecTest` rows.
- ***Enforced by***: G0065's daily test.
- **Scaffolding**: retires or moves the `--mission` row.

> Build the DAILY MISSIONS page of Project-W. Read `CLAUDE.md`, `docs/design/decisions/G0065-…` and `G0066-…`,
> `.scratch/missions/issues/08-…` (the whole *Answer*), `docs/ui/menu_layout_agent.md` (sections 4 and 5, planned),
> `ui/.../nav/CodePicker.java`, the join page it copies, the proving ground's page, and `core`'s `MissionCode`.
> Settled: all of 08. Open: whether `--mission` stays as a dev tool. Follow `menu_layout_agent.md` for the page;
> `new-system` shape for logic.

### 10 - Records

- **Ships**:
  - `core`'s `MissionRecords` (`best`, `offer`) in its own `Preferences` store, `pl.mzebrows.pwt.missions`.
  - Written on the outcome tick by every machine present; a joiner matches the seed against its day and the days
    either side.
  - ✓ and best on the page; NEW BEST on the result screen.
- **Tests**:
  - only a won daily is written;
  - a better time replaces a worse one and an equal one doesn't;
  - a garbled key is skipped;
  - a joiner files the day before as that day.
- ***Enforced by***: G0067.
- **Scaffolding**: adds the known limit "two copies on one PC, last flush wins".

> Build the mission record of Project-W (G0067). Read `CLAUDE.md`, `docs/design/decisions/G0067-…`,
> `.scratch/missions/issues/09-…`, `core/.../client/LocalMachineId.java` (the `Preferences` pattern), stage 9's page
> and `MissionCode.daily`, and the result screen. Settled: all of 09. Open: nothing. `new-system` shape, tests first.

### 11+ - One `new-biome` run each

The rest of the Adventures' biomes: **dust, growth, mire, ashes, scrub, sand, fen, cut**, one session each, in any
order. Then **the keep**: its run first fixes `.scratch/bugs/issues/01` (the boss faces away), tests first, then
ports the keep with its lair. Every run ends with the skill's amend step.

> Port the Adventure's **<biome>** into Project-W's missions. Run `.claude/skills/new-biome`. Read `CLAUDE.md`, the
> skill and its `CHANGELOG.md`, `.scratch/missions/issues/03-…` (the blocks it copies), and the biome's part of
> `AdventureScenario` / `AdventureTwoScenario`. Settled: the skill's steps. Open: the biome's numbers, measured with
> `PWT_MEASURE`. Hand Mateusz a printed code to play. End with the amend step.

> *(keep)* Port the keep, Adventure 2's finale, into Project-W's missions. First fix `.scratch/bugs/issues/01` (the
> boss faces away from the players), starting from its smallest reproducing test. Then run
> `.claude/skills/new-biome` (section 4, *Finale*) with the keep block in `.scratch/missions/issues/04-…`. Read
> `CLAUDE.md`, the skill, `G0048`, `G0062` (*Revisit when*: six nests' first broods), `G0064`, and
> `AdventureTwoScenario`'s keep and lair. End with the amend step.

## Out of scope

- leaderboards and comparing results between players;
- biome sub-variants (the shape allows them);
- new strains for biomes (each through `new-kind`);
- loose weapons lying on the map;
- seeds that stay the same mission across builds;
- a campaign, carrying anything between missions, distinct commander kits;
- "this mission on another tier" from the result screen (deferred in G0066);
- a chasm between biomes.

# The build in stages

Type: grilling
Status: resolved
Blocked by: 03, 04, 05, 06, 07, 08, 09, 10

## Question

Cut the whole into stages of one session each, any of which the work can stop after (`CLAUDE.md`,
Phase), with the scaffolding each leaves listed for `alpha-phase.md` - and assemble `spec.md`. The
destination of this map.

## Carry into the staging

- **Rooms** (12) - asked for on 2026-10-04 with the wider biomes of amended 01. Moved *ahead* of the first biome
  stage in this grilling (2026-10-05), so the biomes and the `new-biome` skill are built with most things settled.
- **The finale stage** (04) needs G0061 landed first (the held point never becomes the rally point); the keep
  wants the boss facing the players (`.scratch/bugs/issues/01`) fixed with it.

## Answer

Grilled 2026-10-05 (three rounds). The stages, their tests, scaffolding and start prompts are in `../spec.md`.

| # | Stage | Gated by |
|---|---|---|
| 0 | Rally fix (G0061) | - |
| 1 | Result screen (G0066), every level | - |
| 2 | Nests wake by biome (G0062) | - |
| 3 | Biome ground (`map`: coats, `Randomness.derive`) - *polish -> draft* | - |
| 4 | The strip (ridges, passes) - *polish -> draft* | - |
| 5 | Rooms and gatehouses (`map`) - *polish -> draft* | ticket 12 |
| 6 | Tiers and the code (G0063, `MissionCode`, `--mission` parsed) | - |
| 7 | **First biome stage**: five blocks, deal, populate with room contents, mission sweep, `--mission` wired - **`SKILL.md` written** | ticket 14 |
| 8 | Finale: the redoubt, the goal line, the field measured - *polish* | 0, 7 |
| 9 | DAILY MISSIONS page (G0065) | 6 |
| 10 | Records (G0067) | 1, 9 |
| 11+ | One `new-biome` run each: dust, growth, mire, ashes, scrub, sand, fen, cut; then the keep with the boss fix - *polish each* | 7, 8 |

**Why this order**

- **G0061 is stage 0** (Q1): map.md had it done separately, and it never was. Nothing else would schedule it.
- **The result screen early** (Q6): independent of missions, it retires two scaffolding rows the Adventures have
  now and is the screen missions are judged through. It builds what every level shows - outcome, time on a win,
  RETRY, MENU; the mission's rows (points, code) arrive in 7, DAILY and COPY CODE in 9.
- **Ground split in two** (3, 4): coats and the strip each fill a session.
- **Rooms before biomes** (Mateusz): ground in `map` right after the strip; what stands in a room is placement,
  built in 7 (Q1, round 3). **Gatehouses move out of the strip stage** into the rooms stage, built once in the form
  12 settles - amends 13 (Q2, round 3).
- **`MissionCode` in 6** (round 4): pure `core`, the tier letter is its first character, and it takes weight off 7.
  6 parses `--mission`; 7 wires it to `Scenarios.mission`. Whether the option outlives ENTER CODE is decided in 9.
- **Before the finale a mission has no objective** (Q3): the strip ends past the last ridge on bare ground. A stand-in
  objective would be a rule players learn; folding the redoubt into 7 would be two sessions in one.
- **The keep carries the boss fix** (Q7): its `new-biome` run fixes `.scratch/bugs/issues/01` first, tests first.

**Not yet specified, resolved** (Q5)

- **Placement inside a biome** - ticket 14, grilled after 12 and before stage 7; 12 and 14 may merge.
- **What the goal line says** - decided in stage 8's `feature` run.
- **How big the field gets** - measured at the end of stage 8: a hard code on `F1`, and a mission case in
  `SimulationBenchmarkTest`. A ticket only if frame time breaks the budget; the avoidance and mine scans are the
  named suspects.
- **Performance of generation** - stage 7's sweep asserts and prints a budget for building one mission (100 ms
  median). A join waits only if that fails.
- **Testing a seed sweep cheaply** - ticket 10: 64 seeds in `verify.sh`, 2000 under `PWT_SWEEP=1`.

**Gating** (Q3, round 3). 12 and 14 can be grilled while stages 0-4 are built. If either runs long, stage 7 waits
rather than shipping on stand-in rules.

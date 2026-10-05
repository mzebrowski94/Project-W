# What the new-biome skill says

Type: grilling
Status: resolved

## Question

The steps of a `new-biome` skill, alongside `new-kind`: constant, block in `biomes.properties`, which
tests prove the generator places it and keeps its passes open, how to port one of the Adventures' biomes,
and what a reviewer checks. Written as a skill when the first biome stage is built.

## Answer

Grilled 2026-10-05 (Q1-Q9). The text it starts from is `../new-biome-skill.md`, updated with these answers.

**Scope** (Q1). One skill, two families, as `new-kind` has them: **biome** (a dealt kind, `biomes.properties`)
and **finale** (a set piece, `finales.properties` - `pieces`, hold, mouths, waves, lair as offsets from the
anchor, the flip, porting from the Adventures' (197, 15)). The keep is a `new-biome` run. A **tier** is not:
it is a block in `missions.properties` through `new-kind`. A biome carries **counts, never places**; where
things stand is placement's (ticket 14), and the skill says so until the stage that builds placement amends it.

**Its text** (Q2). Stage 7 of the build (11) writes `.claude/skills/new-biome/SKILL.md` from the draft,
rewriting each step against what building the five actually took, and deletes the draft - one home. Stages 3-5
(ground, strip, rooms) amend the draft before it moves.

**Tests** (Q3) - none copied per biome; a biome joins every one of them by being a constant:

| Test | Module | Asserts |
|---|---|---|
| `StripSweepTest` | `map` | every pass connects, no pockets, no passage under `clearance`, every ridge tile in the biome whose ground it shows, the finale's west edge straight; rooms open (stage 5) |
| `MissionSweepTest` | `sim` | every point and crate reachable; every finale layout spot standable and reachable; the last biome's nests outside the watch; a point in its last `approachColumns` |
| coats | `map` | every coat of every biome in the pool lands within tolerance of its declared density - generic, once |
| same ground | `sim` | one seed at three tiers lays the same ground (G0065) |
| coverage | `sim` | over the sweep, every `BiomeKind` is dealt at least once at every position - a biome the deal can never place fails |
| a promise | either | only what the design states in a number ("the quarry is the rockiest biome") |

Seeds only: the tier never moves the map (G0065), so 01's "seeds x 3 tiers" is the same-ground test, not the sweep.

**Sweep size** (Q4) - answers map.md's *Testing a seed sweep cheaply*. `verify.sh` runs **64 fixed seeds** a
sweep; past about 3 s, cut the seeds, never the assertions. `PWT_SWEEP=1` runs **2000**, required at the end of
every `new-biome` run and every stage that touches biomes. A failing seed prints its mission code.

**Porting an Adventure biome** (Q5)

- **Measure in Java**: an opt-in runner, `PWT_MEASURE=1 ... BiomeMeasureTest`, builds the Adventure headless,
  measures one band's rock, thicket and patch density and its outcrop sizes, and prints them beside the sweep's
  p10 / median / p90 for the block. The HTML prototype is for looks only once the Java generator exists.
- **Counts**: the Adventure biome's own, scaled by width only (x1.8). No correction for its place in the
  Adventure's march. A range is about +-20 % round the scaled count. Turrets, mines, armoury caches and
  sleeping extras are dropped.
- **The Adventures stay hand-laid** and untouched - they are the reference.

**Seeing it** (Q6). The `PWT_SWEEP` run prints a few mission codes that deal the new biome first; Mateusz plays one
(`--mission=<code>` until the DAILY MISSIONS page's ENTER CODE exists). No scenario, no harness key, no renders.

**Reviewer** (Q7). Everything that can fail a build does, from stage 7: the pool deals (coverage test, pool at least
`sim.mission.biomes`); `regions` at least 1 (refused at bind); no armoury pickup in `furniture.pickups` (refused by
`BiomeDef`); a constant without a block (refused by `Defs`). Left to the eye: the comment above the block (source,
measured numbers, why); every key its nearest neighbour has, a coat off by leaving it out; no coordinates; ported
counts traceable (x1.8); the glossary row; the `PWT_SWEEP` and `PWT_MEASURE` output in the final message.

**A living skill** (Q8, Q9). Mateusz's standing wish: polished as the work goes, not written once.

- **Where**: `.claude/skills/new-biome/CHANGELOG.md` beside the skill - not loaded with it. One dated line an
  amendment: the run or stage; what was wrong, missing or slow; what changed. Git carries the diff.
- **When**: the last step of every `new-biome` run, and of every stage that touches biomes - ground, strip, rooms,
  the first biome stage, the finale (`spec.md` marks them *polish*). Before the skill exists, the draft is amended
  and the line goes under its *Amended* heading, moving to `CHANGELOG.md` with it.
- **What it asks**: which step was **wrong** (followed, and it failed); what was **missing** (you had to find it
  out); what was **slow** (done by hand where a test, a runner or a key would do it).
- **How far**: a text fix goes into the skill in the run; a fix needing code is done in the run when it is one
  helper, otherwise a `docs/backlog.md` row with its trigger, named in the changelog line.
- **A run that found nothing writes "nothing"**, so the step cannot be skipped silently.

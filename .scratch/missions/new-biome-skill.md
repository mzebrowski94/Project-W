# Sketch: the `new-biome` skill

Status: draft, settled by ticket 10 (2026-10-05). Stages 3-5 of `spec.md` amend it; stage 7 rewrites it against what
building the five took, moves it to `.claude/skills/new-biome/SKILL.md`, moves *Amended* below to
`.claude/skills/new-biome/CHANGELOG.md`, and deletes this file. `STAGE n` marks a step whose tool or test arrives
with that stage.

---

```
---
name: new-biome
description: Add a biome or a finale to Project-W's missions as data - a constant, a block in biomes.properties or
  finales.properties, and the sweeps that prove the generator lays it and keeps its passes open. Use when porting
  one of the Adventures' biomes or finales, or adding a new one, once `feature` has cleared it.
---
```

# Adding a biome or a finale

A **biome** and a **finale** are kinds (G0008): one constant and one block - no class, no scenario, no branch. The
mission generator deals biomes from the pool and draws a finale from its own; `map` lays the ground (`BiomeGround`,
through `Strip.lay`) and `sim` puts the roster, furniture or set piece on it. The steps every kind shares - constant,
block, glossary - are `new-kind`'s; this skill is what a biome or a finale adds to them. A **tier** is not here: it is
a block in `missions.properties` through `new-kind`.

## 1. Fit

For every trait the design gives it, name the field that carries it: ground, roster or furniture (a biome); ground,
hold, waves or lair (a finale). Done when every trait has a field.

- A trait with no field is new behaviour: `new-system` first, on the definition every biome (or finale) has, off at
  zero.
- A strain it needs that does not exist yet: `new-kind` first.
- A turret, a mine or a gun cache in a biome: a finale's, or out of scope (ticket 01).
- **Where** anything stands in a biome: placement's (ticket 14), never the biome's. A biome holds counts.
  `STAGE 7` amends this line once placement is built.

## 2. Biome: ground

`map` lays it in coats, each from its own stream: **base**, then **patches** in order, then **crags**, then
**outcrops** (`Coats`, behind `Strip.lay`). Rock always wins over a patch, and a pass's **apron** keeps base ground
only. How much of a coat is always a **density** - a fraction of the biome - never a count.

What a density does, measured over 2000 seeds (stage 3): a coat is laid *until reached*, so its median lands about
half a piece **over** - +0.1 point for 1-2 tile pebbles, +1.3 for 6-11 thicket blobs - and its worst seed one whole
piece over. A patch is measured on the finished ground, so rock laid over it reads it **low**: neighbour patches at
4% under the badlands' 15% rock land at a 4.2% median but 1.6% on the worst seed. Crags land on their density
(quantile cut) less a few repaired tiles. A coat asked for more than fits stops short without a word - the density
test's floor is what catches it.

**What the strip does round a biome** (stage 4): every ridge's face wanders up to `ridgeRough` (3) columns into
the biome either side - into the finale from the west only, so its west edge is straight - and every pass leaves
an apron of base ground a row wider than the pass, out through the face and `apron` (3) tiles on. A density is a
fraction of the biome's whole rect, laid on what the faces leave: a share of rock measured on the rect counts the
faces too, about ten points of a 54-column biome. Measure a coat on the biome inside its faces.

| Coat | Keys under `sim.biome.<name>.ground.` | Reach for it when |
|---|---|---|
| base | `base`, `fallback` | always |
| patches | `patches=rect\|blob <terrain\|neighbour> density sizeMin sizeMax; ...` | `rect` for small patches of colour, `blob` for ground worth going round (thicket). `neighbour` takes the nearer biome's base, or `fallback` |
| crags | `crags.density`, `crags.smoothing` | irregular masses; more smoothing, fewer and bigger. Leave the block out for none |
| outcrops | `outcrops.density`, `lengthMin/Max`, `thicknessMin/Max`, `arms` | rock bent round a corner - the Adventures' shape. Leave the block out for none |

Plus `widthMin/Max` and `regions`, which are geometry and so ground too. A biome's regions are its own -
every region lies inside one biome, and a tile is in its region's (G0062) - so `regions` is at least 1.
Rooms: `STAGE 5` adds their coat, or says why a biome has none (ticket 12).

**Porting an Adventure biome**: measure it first. `PWT_MEASURE=1 .claude/scripts/gradle.sh :sim:test --tests
'*BiomeMeasureTest' -i` (`STAGE 7`) builds the Adventure headless and prints the band's rock, thicket and patch
density and its outcrop sizes beside the sweep's p10 / median / p90 for your block. Tune until the medians sit
within a point of the measured ones. The HTML prototype is for looks only.

## 3. Biome: roster and furniture

- `roster=loose|nest <strain> min max; ...` - loose is put down unaware, a nest is a hole of that strain. The
  ranges are the biome at the first position on medium; tier and ramp scale every count through `Roster.count`
  (pressure = tier `enemyPercent` x (1 + `rampPercent` x position), G0063), and nothing else does.
- A nest wakes only for somebody **in its own biome** and within `wakeRadius`, and broods
  `firstBroodSeconds` after waking (G0062). A biome never counts on its neighbour having stirred its nests.
- `furniture.pointsMin/Max` (2-3), `barrelsMin/Max`, `rabbitsMin/Max`, `bouldersMin/Max`, and
  `furniture.pickups=pickup <kind> min max; ...`. Never scaled. No armoury pickup - `BiomeDef` refuses one.
- **Porting**: the Adventure biome's own counts, one entry per strain and way, scaled by width only (x1.8 - an
  Adventure biome is 29 columns, a mission's 50-58), no correction for its place in the Adventure's march; a range
  about +-20 % round the scaled count. Drop turrets, mines, armoury caches and sleeping extras.

## 4. Finale

`STAGE 8` writes this section from building the redoubt; the keep is its first run. Settled (ticket 04, G0064):
`FinaleDef(ground, hold, waves, lair)` in `finales.properties`, a set piece - every position a tile offset `dx dy`
from the anchor, flipped north-south by one bit, never east-west. Ground is `BiomeGround` with width fixed, no dealt
coats, rock as `pieces`. Hold: `post`, `watch`, `turrets`, `mines`, `barrels`, `pickups`, `mouths`. Waves
`after strain min max mouth`, counted through `Roster.count` unramped. Lair (a keep): `unit|nest|pickup kind dx dy`
in the order written. **Porting**: offsets from the Adventure's (197, 15); counts become ranges round the port.

## 5. Tests

None is written per biome; a biome or finale joins every one by being a constant.

- **`StripSweepTest`** (`map`): four of the pool dealt per seed into a strip before a bare finale; every ridge
  crossed by `passesMin`-`passesMax` passes, every open tile reached from the first pass, no passage under
  `clearance`, every tile in the biome whose ground it shows and each biome holding its `regions`, the finale's
  west edge straight. Checks live in `StripFaults`, one list of faults each, so `StripTest` asserts one of them
  alone. A failure lists every failing seed and fault together (`seed 1412: ridge 2 has 0 passes in and 0 out`);
  under `PWT_SWEEP=1` a green run prints one line - seeds, ms per strip, columns min/median/max, passes per ridge.
  Rooms `STAGE 5`.
- **`MissionSweepTest`** (`sim`): every point and crate reachable, every finale layout spot standable and
  reachable, the last biome's nests outside the watch, a point in its last `doorColumns`. `STAGE 7` / `8`.
- **`CoatsTest`** (`map`): every coat of every biome, over 64 seeds, lands with its **median within 1.5 points**
  of its density and **no seed under half** of it; rock is the crags' and outcrops' densities summed. Also: no
  passage under `clearance`, no pockets, every pass end reaches every other, the apron base ground, rock a crag
  did not lay never cleared, retuning one coat moving no other's draws. The five blocks and a bare redoubt
  are literal `BiomeGround`s in `TestBiome` (`map` tests), shared by `CoatsTest` and both strip tests, until `sim`
  reads `biomes.properties` (`STAGE 7` swaps them for the table). A new biome's block goes in there by hand until
  then, and into `TestBiome.FIVE`. **Same ground**: one seed at
  three tiers. **Coverage**: every `BiomeKind` dealt at least once at every position.
- `verify.sh` runs 64 seeds a sweep. **Run the big one** before you finish:
  `PWT_SWEEP=1 .claude/scripts/gradle.sh :map:test --tests '*SweepTest' --rerun -i` - 2000 seeds. `--rerun`
  because the variable is no input of the test task: without it a sweep that passed at 64 is up to date and
  silently skipped. `:sim:test` joins the command at `STAGE 7`, with `MissionSweepTest` - a `--tests` filter
  that matches nothing in a module fails the build. A failing seed prints its index until codes exist; then its
  mission code, and the run prints a few codes that deal your biome first (`STAGE 7`).
- **What the design promises in a number** - one test per promise ("the quarry is the rockiest biome"). Never
  connectivity, which the sweeps own; never which biome a tile is in, which is `Strip`'s own test.

## 6. Look at it

Hand Mateusz one of the printed codes: `--mission=<code>` (`STAGE 7`), or ENTER CODE once the DAILY MISSIONS page
exists. He judges whether it reads as the place it was ported from.

## 7. Finish

`verify.sh` green. Glossary row for the word (`docs/glossary.md`, *Levels and objectives*). The final message lists
the block's keys with values and what each does to the feel, and pastes the `PWT_SWEEP` and `PWT_MEASURE` output.

## 8. Amend this skill

Every run ends here, and so does every stage that touches biomes. Ask:

- which step was **wrong** - you followed it and it failed;
- what was **missing** - you had to find it out;
- what was **slow** - done by hand where a test, a runner or a key would do it.

Fix text here, in this run. A fix needing code: in this run if it is one helper, otherwise a `docs/backlog.md` row
with its trigger. Then one dated line in `CHANGELOG.md` beside this file - run or stage, what, what changed - and
**"nothing" when nothing**, so the step is never skipped silently.

## Reviewer checks

The build already refuses a constant without a block, `regions` under 1, an armoury pickup in `furniture.pickups`, a
pool smaller than `sim.mission.biomes`, and a biome the deal never places. By eye:

- a comment above the block: source, measured numbers, why;
- every key its nearest neighbour's block has; a coat off by leaving its block out, nothing else;
- no coordinates in a biome - rules, never places;
- ported counts traceable to the Adventure (x1.8);
- the glossary row;
- the `PWT_SWEEP` and `PWT_MEASURE` output in the final message;
- a `CHANGELOG.md` line.

---

## Amended

Moves to `CHANGELOG.md` with the skill. One dated line each: stage or run - what was wrong, missing or slow - what
changed.

- 2026-10-05, ticket 10 grilling - draft settled: finale family added, sweeps split by module, Java measuring,
  printed codes to play, the amend step.
- 2026-10-05, stage 3 (biome ground) - missing: what a density actually lands at (half a piece over; patches read
  low under heavy rock; a greedy coat stops short silently) and the coats test's tolerance; slow: the
  distributions took a throwaway probe test, which `BiomeMeasureTest` (`STAGE 7`) replaces - added both to *Ground*
  and *Tests*. Nothing wrong.
- 2026-10-05, stage 4 (the strip) - wrong: the big-sweep command named `:sim:test`, which has no sweep yet and
  fails the filter, and lacked `--rerun`, without which Gradle skips the sweep as up to date; missing: what the
  strip does round a biome (faces, aprons, a rock share measured on the rect reading the faces), `StripSweepTest`'s
  checks and output, the five blocks' new home `TestBiome`; slow: checking a strip by eye took a throwaway ASCII
  dump - not kept, since a `new-biome` run looks through a played code. Fixed in *Ground* and *Tests*.

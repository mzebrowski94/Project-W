---
name: new-biome
description: Add a biome or a finale to Project-W's missions as data - a constant, a block in biomes.properties or finales.properties, and the sweeps that prove the generator lays it, populates it and keeps its passes open. Use when porting one of the Adventures' biomes or finales, or adding a new one, once `feature` has cleared it.
---

# Adding a biome or a finale

A **biome** and a **finale** are kinds (G0008): one constant and one block - no class, no scenario, no branch. A
mission deals biomes from the pool (`Mission.deal`) and will draw a finale from its own; `map` lays the ground
(`BiomeGround`, through `Strip.lay`) and `sim` puts the roster and furniture on it by rules (`Placement`, G0068) - or
the set piece, for a finale. The steps every kind shares - constant, block, glossary - are `new-kind`'s; this skill
is what a biome or a finale adds to them. A **tier** is not here: it is a block in `missions.properties` through
`new-kind`.

## 1. Fit

For every trait the design gives it, name the field that carries it: ground, roster or furniture (a biome); ground,
hold, waves or lair (a finale). Done when every trait has a field.

- A trait with no field is new behaviour: `new-system` first, on the definition every biome (or finale) has, off at
  zero.
- A strain it needs that does not exist yet: `new-kind` first.
- A turret, a mine or a gun's cache in a biome: a finale's, or out of scope (ticket 01). `BiomeDef` refuses a cache.
- **Where** anything stands is placement's, never the biome's: ordered passes with strip-wide rules,
  `sim.mission.placement.*` (G0068). A biome holds counts. A biome that genuinely needs a rule of its own gets a
  field on `PlacementRules` or `BiomeDef`, off at zero for every other biome (G0068, *Revisit when*).

## 2. Biome: ground

The block lives in `assets/sim/biomes.properties` under `sim.biome.<name>.ground.`. `map` lays it in coats, each
from its own stream: **houses**, then **patches** in order, then **crags**, then **outcrops** (`Coats`, behind
`Strip.lay`). Rock always wins over a patch; a pass's **apron**, the **start**, and a house's inside, doorways and
doorsteps keep base ground. How much of a coat is a **density** - a fraction of the biome - never a count; **houses
are the one exception**, counted, because a house is a place.

| Coat | Keys | Reach for it when |
|---|---|---|
| base | `base`, `fallback` | always |
| houses | `houses.min`, `houses.max` | a place worth taking - "a quarry with a base in it". 0-2; leave the block out for none. Size and doorways are the strip's (`sim.mission.ground.houses.*`) |
| patches | `patches=rect\|blob <terrain\|neighbour> density sizeMin sizeMax; ...` | `rect` for small patches of colour, `blob` for ground worth going round (thicket). `neighbour` takes the nearer biome's base, or `fallback` |
| crags | `crags.density`, `crags.smoothing` | irregular masses; more smoothing, fewer and bigger. Leave the block out for none |
| outcrops | `outcrops.density`, `lengthMin/Max`, `thicknessMin/Max`, `arms` | rock bent round a corner - the Adventures' shape. Leave the block out for none |

Plus `widthMin/Max` and `regions` (at least 1 - every region lies inside one biome, G0062), which are geometry.

**What a density lands at**: a coat is laid *until reached*, so its median lands about half a piece **over** - +0.1
point for 1-2 tile pebbles, +1.3 for 6-11 thicket blobs, +1.7 for the quarry's 4-9 bars - and its worst seed one
whole piece over. A patch is measured on the finished ground, so rock laid over it reads it low. A coat asked for
more than fits stops short without a word - the density test's floor catches it.

**What the strip does round a biome**: every ridge's face wanders up to `ridgeRough` (3) columns into the biome
either side, and every pass leaves an apron of base ground out through the face and `apron` (3) tiles on. A density
is a fraction of the whole rect, laid on what the faces leave; measure a coat inside the faces. A gatehouse is the
strip's, never a biome's: about one ridge in four, never the one before the finale.

**What houses take**: each 8-12 x 7-10 tiles of `Terrain.WALL`, the clearance off all rock, its inside and a
doorstep two deep outside each doorway kept - about 6% of a 54-column biome a house. A biome asking for more houses
than fit lays what fits, and the strip sweep reports it under its `houses.min`.

**Porting an Adventure biome: measure it.**
`PWT_MEASURE=1 .claude/scripts/gradle.sh :sim:test --tests '*BiomeMeasureTest' --rerun -i` builds each Adventure a
biome was ported from and prints the band's rock, thicket and patch share and its outcrops' count and size beside
the same over 256 missions, p10 / median / p90. **Add your port's row to `BiomeMeasureTest.ports()`** - scenario,
first and last column, base ground - first. Tune until the medians sit within a point of the measured ones. The HTML
prototype is for looks only.

## 3. Biome: roster and furniture

- `roster=loose|nest <strain> min max; ...` - loose is put down unaware in packs, a nest is a hole of that strain.
  The ranges are the biome at the first position on medium; tier and ramp scale every count through `Roster.count`
  (pressure = tier `enemyPercent` x (1 + `rampPercent` x position), G0063), and nothing else does.
- `furniture.pointsMin/Max` (2-3), `barrelsMin/Max`, `rabbitsMin/Max`, `bouldersMin/Max`, and
  `furniture.pickups=pickup <kind> min max; ...`. Never scaled.
- A nest wakes only for somebody **in its own biome** within `wakeRadius` (G0062); a biome never counts on its
  neighbour having stirred its nests.
- **Counts fit the room** (G0070). Placement keeps every hostile `startKeepOff` off the start, nests off points,
  passes and each other, packs apart - so a biome holds only so much, and a seed where a count does not fit is a
  sweep fault, never a quieter biome. A short biome is placed again (up to 32 attempts, the first that fits every
  tier), which lifts a block's worst case towards the median but not past it. The same
  `PWT_MEASURE=1 ... BiomeMeasureTest` prints the **room** each biome leaves - holes and loose creatures, least /
  p1 / p10 / median over 2000 missions, dealt first and later - for one attempt. Stage 7 measured, for the five:
  holes 1-3 at the median dealt first, 4-5 later; loose about 42 first, 75 later; their worst seeds 1 / 2 holes and
  25 / 45 loose. A block must fit on hard: **x1.25 its most if dealt first, x1.625 at the last position** - in
  practice **1-2 holes and at most about 25 loose creatures** a block. Ground that leaves less room (heavy crags,
  houses) holds less: the quarry holds one hole.
- **Where it goes** (G0068): points one a column band; house stock (crates, then medkits) up to `houseStock` a house;
  nests; packs; leftover crates by points; medkits anywhere; **food** - anything a creature gains from - on the
  biome's organic ground while there is room; barrels by holes and packs; boulders `rockMargin` off rock; rabbits.
  A block says none of this; it is why a crate or a medkit line fills the houses first.
- **Porting**: the Adventure biome's own counts, scaled by width (x1.8 - an Adventure biome is 29 columns, a
  mission's 50-58), then **cut to the room** - holes most of all. A range about +-20 % round the count. Drop
  turrets, mines, armoury caches and sleeping extras.

## 4. Finale

`STAGE 8` writes this section from building the redoubt; the keep is its first run. Settled (ticket 04, G0064):
`FinaleDef(ground, hold, waves, lair)` in `finales.properties`, a set piece - every position a tile offset `dx dy`
from the anchor, flipped north-south by one bit, never east-west. Ground is `BiomeGround` with width fixed, no dealt
coats, rock as `pieces`. Hold: `post`, `watch`, `turrets`, `mines`, `barrels`, `pickups`, `mouths`. Waves
`after strain min max mouth`, counted through `Roster.count` unramped. Lair (a keep): `unit|nest|pickup kind dx dy`
in the order written. **Porting**: offsets from the Adventure's (197, 15); counts become ranges round the port.
Until then the strip ends on bare ground, `Mission`'s scaffolding stand-in.

## 5. Tests

None is written per biome; a biome joins every one by being a constant.

- **`MissionSweepTest`** (`core`, beside `MissionCode` so a fault is named by the code that plays it): every seed
  built on **every tier**, held to `StripFaults` (the strip, over the **shipped** pool - a `map` test fixture) and
  `MissionFaults` (G0068: nothing short, points and stock reached from the start, nothing on kept ground, nothing
  hostile near the start or in a house, nests off the passes, points, each other and pickups, a point a band and
  the last in the last `approachColumns`, crates by points). Also the build budget: a mission's whole world, median
  of 16, under 100 ms (stage 7: about 6 ms). Under `PWT_SWEEP=1` a green run prints one summary line - seeds,
  ms/build, creatures per biome on hard - and three medium codes that deal each biome first.
- **`StripSweepTest`**, **`StripTest`**, **`CoatsTest`** (`map`): the generator against `TestBiome`, five literal
  blocks kept as `map`'s own fixtures - representative, not the shipped ones, which `map` cannot read. A new biome
  does not go there; its ground is swept by `MissionSweepTest`.
- **`MissionTest`** (`sim`): the deal (none twice, no base side by side), **coverage** (every kind at every position
  over 512 deals - a biome the deal can never place fails), the **same ground and points** on every tier (G0065),
  more creatures on a harder tier.
- **`BiomeDefTest`** (`sim`): the table binds; a gun's cache, a roster line backwards and a pool smaller than
  `sim.mission.biomes` are refused.
- **`BiomePromisesTest`** (`sim`): one test per promise the design makes in a number - "the brake is the one biome
  overgrown", "crags from mounds to masses". Add yours. Never connectivity or room, which the sweeps own.
- `verify.sh` runs 64 seeds a sweep (three tiers each in the mission sweep). **Run the big one** before you finish:
  `PWT_SWEEP=1 .claude/scripts/gradle.sh :map:test --tests '*SweepTest' --rerun :core:test --tests '*SweepTest' --rerun -i`
  - 2000 seeds. Each test task takes its own `--tests` and `--rerun`: the variable is no input of the task, and
  without `--rerun` a sweep that passed at 64 is up to date and silently skipped.

## 6. Look at it

Hand Mateusz one of the printed codes: `./gradlew lwjgl3:run --args="--mission=<code>"` - or ENTER CODE once the
DAILY MISSIONS page exists. He judges whether it reads as the place it was ported from.

## 7. Finish

`verify.sh` green. Glossary row for the word (`docs/glossary.md`, *Levels and objectives*). The final message lists
the block's keys with values and what each does to the feel, and pastes the `PWT_SWEEP` and `PWT_MEASURE` output -
the room lines included.

## 8. Amend this skill

Every run ends here, and so does every stage that touches biomes. Ask:

- which step was **wrong** - you followed it and it failed;
- what was **missing** - you had to find it out;
- what was **slow** - done by hand where a test, a runner or a key would do it.

Fix text here, in this run. A fix needing code: in this run if it is one helper, otherwise a `docs/backlog.md` row
with its trigger. Then one dated line in `CHANGELOG.md` beside this file - run or stage, what, what changed - and
**"nothing" when nothing**, so the step is never skipped silently.

## Reviewer checks

The build refuses a constant without a block, `regions` under 1, a gun's cache in `furniture.pickups`, a pool
smaller than `sim.mission.biomes`, a biome the deal never places, and a count that does not fit its room on some
seed. By eye:

- a comment above the block: source, measured numbers, why - and for `houses`, why this biome has them;
- every key its nearest neighbour's block has; a coat off by leaving its block out, nothing else;
- no coordinates in a biome - rules, never places;
- ported counts traceable to the Adventure (x1.8), and where cut to the room, by how much;
- the port's row in `BiomeMeasureTest.ports()`;
- the glossary row;
- the `PWT_SWEEP` and `PWT_MEASURE` output in the final message;
- a `CHANGELOG.md` line.

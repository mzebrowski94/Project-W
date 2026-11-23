---
name: new-biome
description: Add a biome or a finale to Project-W's missions as data - a constant, a block in biomes.properties or finales.properties, and the sweeps that prove the generator lays it, populates it and keeps its passes open. Use when porting one of the Adventures' biomes or finales, or adding a new one, once `feature` has cleared it.
---

# Adding a biome or a finale

A **biome** and a **finale** are kinds (G0008): one constant and one block - no class, no scenario, no branch. A
mission deals biomes from the pool (`Mission.deal`) and draws a finale from its own; `map` lays the ground
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
- A mine or a gun's cache in a biome: a finale's, or out of scope (ticket 01). `BiomeDef` refuses a cache. A turret
  is a furniture line, `turretsMin/Max`, each placed by a point (G0082) - zero in every biome but the plain.
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
| a new ground | a `Terrain` constant **appended** (so no other renumbers), `graphic.terrain.<name>`, and `sim.ground.<name>.*` if it slows | ground the pool has no terrain for - the desert's mud (G0095) |
| houses | `houses.min`, `houses.max` | a place worth taking - "a quarry with a base in it". 0-2; leave the block out for none. Size and doorways are the strip's (`sim.mission.ground.houses.*`). Every doorway gets a shut door at populate (G0069): a house's stock is behind three seconds of work, and a hunter that chases a man in will siege it - count that into how busy the biome is |
| patches | `patches=rect\|blob <terrain\|neighbour> density sizeMin sizeMax; ...` | `rect` for small patches of colour, `blob` for ground worth going round (thicket). `neighbour` takes the nearer biome's base, or `fallback` |
| crags | `crags.density`, `crags.smoothing`, `crags.terrain` | irregular masses; more smoothing, fewer and bigger. `terrain` is `rock`, `lava` for pools nothing stands on (the caldera, G0091), or `fissure` for the rift's maze (G0115) - any solid terrain, laid with the crag repairs, which is why a solid ground is never a patch (`PatchGround` refuses it). Leave the block out for none |
| clearings | `clearings.count`, `clearings.radius` | round stretches of base no crag or outcrop is laid in, each reached from the passes - the rift's two (G0115). Laid before every other coat, centred in the outer thirds; with the furniture's `sitesInClearings` each holds a point, and `clearingPackShare` puts that share of the packs in them first. A clearing takes a quarter of a biome off its crags' room, so its density lands higher in the rest - measure it there. Leave the block out for none |
| outcrops | `outcrops.density`, `lengthMin/Max`, `thicknessMin/Max`, `arms`, `ragged` | rock bent round a corner - the Adventures' shape. `ragged` (0-1, no arms with it) knocks corners off and bumps the faces, so a slab reads as broken rock rather than a block from above (the cut, 0.6). Leave the block out for none |

Plus `widthMin/Max` and `regions` (at least 1 - every region lies inside one biome, G0062), which are geometry.

**What a density lands at**: a coat is laid *until reached*, so its median lands about half a piece **over** - +0.1
point for 1-2 tile pebbles, +1.3 for 6-11 thicket blobs, +1.7 for the quarry's 4-9 bars, +2.8 for the fen's 9-14
reed blobs (0.11 landed at 15.3; 0.075 lands at 12.3) - and its worst seed one whole piece over. A `rect` patch line
reads 3-4 points over the Adventure in every biome, neighbour or terrain alike: match the other blocks' 0.02-0.04
rather than chasing it. A patch is measured on the finished ground, so rock laid over it reads it low. A coat asked for
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
first and last column, base ground - first. Tune until the medians sit within a point of the measured ones. The run
takes about six minutes (the room over 2000 missions): start it in the background and write the block's comment
meanwhile. The HTML prototype is for looks only.

## 3. Biome: roster and furniture

- `roster=loose|nest|house <strain> min max; ...` - loose is put down unaware in packs, a nest is a hole of that strain,
  a house line a **house pack** in each of its houses on `housePackChance` (G0112) - a biome that lays houses has
  `house jaguar 1 3`, last, and the range is one house's.
  The ranges are the biome at the first position on medium; tier and ramp scale every count through `Roster.count`
  (pressure = tier `enemyPercent` x (1 + `rampPercent` x position), G0063), and nothing else does.
- `furniture.sitesMin/Max` (2-3 sites: control points, or another kind of site in a point's place, G0110), `barrelsMin/Max`, `rabbitsMin/Max`, `bouldersMin/Max`, `treesMin/Max` and
  `treesByNestMin/Max` (0 for none - the fen alone has trees, G0072), `turretsMin/Max` (0 for none - the plain alone
  has them, G0082), `geysersMin/Max` (0 for none - the cut and the caldera have them, G0089, G0091),
  `spires=min max` on one line (`0 0` for none - the fen alone has them, G0113), `relics=min max` (`0 0` for none, at
  most `sitesMin` - the desert alone has them, each a site in a point's place, G0114), `sitesInClearings` and
  `clearingPackShare` (`false` and `0` for none - the rift alone has them: a point at each clearing's centre and 60 % of its
  packs in its clearings, G0115), and `furniture.pickups=pickup <kind> min max; ...`.
  Never scaled. **Every block carries every furniture key**, zeros included: a new furniture line is a key in all of
  them. A new line that stands something is also a `Placed` record, a case in `MissionScenario.populate`, in
  `Placement.radius` and in `MissionFaults`' radius, a pass in `Placement.Biome.place` and its count drawn **after**
  every other (so a biome with none draws as before) - the desert's stones (G0095) are the pattern. `BiomeDefTest`
  builds a `Furniture` positionally. A furniture thing with a size of its own (a tree's trunk) is drawn from its own stream in its pass, never on
  the block; a count tied to another pass (trees by each nest) is drawn from the biome's stream, nest by nest, so a
  biome placed again keeps its counts (G0070).
- A nest wakes only for somebody **in its own biome** within `wakeRadius` (G0062); a biome never counts on its
  neighbour having stirred its nests.
- **Counts fit the room** (G0070). Placement keeps every hostile `startKeepOff` off the start, nests off points,
  passes and each other, packs apart - so a biome holds only so much, and a seed where a count does not fit is a
  sweep fault, never a quieter biome. A short biome is placed again (up to 32 attempts, the first that fits every
  tier), which lifts a block's worst case towards the median but not past it. The same
  `PWT_MEASURE=1 ... BiomeMeasureTest` prints the **room** each biome leaves - holes and loose creatures, least /
  p1 / p10 / median over 2000 missions, dealt first and later - for one attempt. Stage 7 measured, for the five:
  holes 1-3 at the median dealt first, 4-5 later; loose about 42 first, 75 later; their worst seeds 1 / 2 holes and
  25 / 45 loose. **Every roster line is a pack of its own**, kept `packApart` from the others, so a block's
  number of lines costs room as well as its counts: the cut's seventh line (a lone grunt, beside two 0-1 giants) fell
  short on 9 seeds of 2000 dealt first. Fold a line of one or two into a neighbour, or drop it. A block must fit on hard: **x1.25 its most if dealt first, x1.625 at the last position** - in
  practice **1-2 holes and at most about 25 loose creatures** a block. Ground that leaves less room (heavy crags,
  houses) holds less: the quarry holds one hole.
- **Where it goes** (G0068): sites one a column band - a control point in each band no other kind of site takes
  (G0110) - a mainframe in its house first (G0111), then relics in rooms of open base ground (G0114); house stock (crates,
  then medkits) up to `houseStock` a house;
  nests; packs; turrets by points; medkits anywhere; **food** - anything a creature gains from - on the
  biome's organic ground while there is room; barrels by holes and packs; boulders `rockMargin` off rock; trees as boulders, the first within `treeReach` of each
  nest; rabbits. Geysers (G0089) go after turrets and before medkits, `geyserKeepOff` off points, nests and ways in,
  so what is placed after them keeps off their fire; each one's radius is drawn in its pass, as a trunk is.
  Emplacements - spires (G0113) - go after geysers, as boulders stand, `emplacementKeepOff` off ways in, nests, the
  finale's watch and every site's edge, never on thicket; each one's cycle is dealt in its pass.
  A block says none of this; it is why a crate or a medkit line fills the houses first. A crate line is house stock
  and nothing else (G0110): what no house takes is not laid, so a biome that lays no house has no crate line - the
  field's crates are what a taken site leaves.
- **Porting**: the Adventure biome's own counts, scaled by width (x1.8 - an Adventure biome is 29 columns, a
  mission's 50-58), then **cut to the room** - holes most of all. A range about +-20 % round the count. Drop
  mines, armoury caches and sleeping extras; a turret only as a `turrets` line, by a point.

## 4. Finale

A finale is a **set piece** (G0064): `FinaleKind`, and a block under `sim.finale.<name>.` in
`assets/sim/finales.properties` binding `FinaleDef(ground, hold, layouts, waves, lair)`. Its variety is a few
hand-made **layouts** (G0087) - `layouts.1.`, `layouts.2.`, ... numbered blocks with no gap, each its own `pieces`,
`turrets`, `mines`, `barrels` and `pickups`, laid over the ground's and the hold's, which keep what every layout shares
(the post, the watch and the mouths always) - then one bit that flips it north-south. The stream draws kind, flip,
layout, in that order, equally, whatever the tier. Write each layout symmetric north-south about the anchor's row, or
name its pattern in its comment. Never rules, never a dealt coat - a finale laid by rules is G0064's first DO NOT BUILD.
A finale with no layouts is laid as it is written.

**Every position is an offset `dx dy` from the anchor**, `Strip.anchor`: the finale's middle column rounded west and
its middle row - (197, 15) on the Adventures' ground, so a port subtracts that. `dy` counts rows **north**, and a
rect's `dx dy` is its south-west corner (`TileRect` counts rows from the bottom). The flip is `Offset.flipped` - a
point's `dy` negated, a rect's south edge `-(dy + height - 1)` - and `FinaleDef.flipped()` applies it to all of it; a
new field with a position must go through it, or the flip tests fail.

| Part | Keys | What to know |
|---|---|---|
| ground | `widthMin` = `widthMax`, `regions`, `base`, `fallback`, `pieces=dx dy w h; ...` | A dealt width or any coat - houses, patches, crags, outcrops - is refused. Its rock is its `pieces`, laid by `Coats` before anything; a piece past the finale's rect is refused at lay |
| hold | `post=dx dy`, `watch`, `turrets`, `mines`, `barrels` (`dx dy; ...`), `pickups=pickup <kind> dx dy; ...`, `mouths=name dx dy w h; ...` | Nothing scaled by tier. `watch` is world units, from the post. A gun's cache may lie here, never in a biome. Mouth names are unique |
| layouts | `layouts.<n>.pieces`, `.turrets`, `.mines`, `.barrels`, `.pickups`, as the ground's and hold's lines | Numbered from 1, no gap - a gap ends the list and what lies past it is an unknown key. Each laid over the shared ground and hold by `FinaleDef.laid`; `Mission.Finale.layout` says which was dealt and `def(config)` lays it |
| waves | `waves=afterSeconds strain countMin countMax mouths; ...` | Held back until the post is taken. `nw+se` comes from both on one tick, one wave, each mouth its own count from the range (G0087). A mouth the hold lacks - any of them - is refused, and so are more than `Waves.MOST` (63) waves. Each count through `Roster.count` at `MissionConfig.finale(tier)` - the tier's `enemyPercent`, no ramp |
| lair | `lair=unit\|nest\|pickup <kind> dx dy; ...` | Positions, never counts. Resolved into `Mission.Finale.lair` (`Arrival`s, in the order written) and put on the level's `Lair` over the finale's whole rect: nothing of it on the field until every commander on his feet stands in the finale, and each creature rises facing the nearest of them. Keep it inside the `watch` - the sweep checks - so holding the post means dealing with it. A mission strips `alwaysAggressive` from every strain, so a risen boss starts shooting only once somebody is within his `detectionRange` |

**What a block may use**: interior rows 1-28 with the anchor on 15, so with the flip a position fits `dy` -13..13;
columns `dx` -16..17 of a 34-wide finale. The pass from the ridge before it keeps the first four columns (`dx` -16..-13)
its base ground with its apron: a mouth may lie there, nothing that stands may (`MissionFaults.kept`). The `watch`
reaches west into the ridge and the last biome, whose placement keeps **everything hostile** outside it (G0064,
amended) - a wider watch costs the last biome room, and the sweep says when it has none.

**Where it ends up**: `Mission.build` resolves the set piece into `Placed.Hold`, `Turret`, `Mine`, `Barrel` and
`Pickup` at the position after the last biome, `Mission.finale().waves()` into `Wave`s at their mouths and
`.lair()` into `Arrival`s; `MissionScenario.populate` spawns them, holds the waves back, fills the lair and requires
`HOLD_POINT` alone.

**A new finale re-draws every seed**: the draw is one of `FinaleKind.values()`, so adding a constant changes which
finale every existing mission code ends on - a daily's record among them. Say so in the final message. A new layout
re-draws only which layout a seed of that finale ends on: the layout is drawn last.

**The sweep reaches every layout by name**: `MissionSweepTest` picks, past its spread, the first seeds whose
`Mission.drawn` ends on each finale, layout and flip, and fails if any is missing - a new layout is swept by being
written. `MissionFaults.finale` also refuses two things of the set piece or its lair on one tile. Tests that
name one finale's numbers (four turrets, twenty mines) read them from `mission.finale().def(config)` instead, and
`MissionTest.endsOnEveryFinaleFlippedAndNot` fails a finale the draw never reaches. The goal line leads
through every point, the finale's among them, until the waves are let go, then says the hold.

**Porting from an Adventure**: offsets from (197, 15); each wave's count a range of about +-1 round the Adventure's;
the Adventure's own comments carried into the block's. Drop what neither a wave nor the lair brings (the keep's
sleeping ants, ticket 04). Each finale names its own strains.

**Measure the field**: `PWT_BENCH=1 .claude/scripts/gradle.sh :sim:test --tests '*SimulationBenchmarkTest' --rerun -i`
prints `BENCH mission` (a hard mission as built) and `BENCH finale <kind>` for every finale (the first seed ending on
it, every wave on the field at once and its lair risen) and fails any past one tick - a new finale is measured by
being a constant. Stage 8, the redoubt: 0.33 and 0.58 ms of 16.7. Then `F1` on a hard code from the
sweep's output, for what the client draws.

## 5. Tests

None is written per biome; a biome joins every one by being a constant.

- **`MissionSweepTest`** (`core`, beside `MissionCode` so a fault is named by the code that plays it): every seed
  built on **every tier**, held to `StripFaults` (the strip, over the **shipped** pool - a `map` test fixture) and
  `MissionFaults` (G0068: nothing short, points and stock reached from the start, nothing on kept ground, nothing
  hostile near the start or in a house, nests off the passes, points, each other and pickups, a point a band and
  the last in the last `approachColumns`, no biome crate outside a house; the finale's set piece and every mouth open and reached,
  nothing hostile of the last biome in its watch). Also the build budget: a mission's whole world, median
  of 16, under 100 ms (stage 7: about 6 ms). Under `PWT_SWEEP=1` a run, green or red, prints one summary line - seeds, faults,
  ms/build, creatures per biome on hard - and three seeds that deal each biome first, each as its medium and hard
  code and the finale it ends on.
- **`StripSweepTest`**, **`StripTest`**, **`CoatsTest`** (`map`): the generator against `TestBiome`, five literal
  blocks kept as `map`'s own fixtures - representative, not the shipped ones, which `map` cannot read. A new biome
  does not go there; its ground is swept by `MissionSweepTest`.
- **`MissionTest`** (`sim`): the deal (none twice, no base side by side), **coverage** (every kind at every position
  over 512 deals - a biome the deal can never place fails), the **same ground and points** on every tier (G0065),
  more creatures on a harder tier; the finale flipped north-south only, the same on every tier but for its wave counts,
  and populated with its waves held back and `HOLD_POINT` alone.
- **`BiomeDefTest`** (`sim`): the table binds; a gun's cache, a roster line backwards and a pool smaller than
  `sim.mission.biomes` are refused. **`FinaleDefTest`** the same for finales: the redoubt binds - add a line for
  yours - and the refusals and the flip.
- **`BiomePromisesTest`** (`sim`): one test per promise the design makes in a number - "the brake is the most
  overgrown", "crags from mounds to masses". Add yours. A new biome often breaks an old "the one biome" promise:
  reword it to what still holds, never delete it. Never connectivity or room, which the sweeps own.
- `verify.sh` runs 64 seeds a sweep (three tiers each in the mission sweep). **Run the big one** before you finish:
  `PWT_SWEEP=1 .claude/scripts/gradle.sh :map:test --tests '*SweepTest' --rerun :core:test --tests '*SweepTest' --rerun -i`
  - 2000 seeds. Each test task takes its own `--tests` and `--rerun`: the variable is no input of the task, and
  without `--rerun` a sweep that passed at 64 is up to date and silently skipped. **Read its faults by biome**: a
  fault in a biome your change does not reach - its counts and passes untouched - was there before you (on
  2026-11-13 the integration tip showed 63 in 2000, in the badlands, the anthill and the fen, and one strip run);
  say so, and own only the ones in what you changed.

## 6. Look at it

Hand Mateusz one of the printed codes: `./gradlew lwjgl3:run --args="--mission=<code>"`, or ENTER CODE on the
DAILY MISSIONS page. He judges whether it reads as the place it was ported from.

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
seed - and for a finale, a dealt width or coat, a piece past its rect, a wave from a mouth its hold lacks, a hold
pickup that is not a pickup, anything it stands or its lair raises on shut or unreached ground, and a lair line
outside the watch. By eye:

- a comment above the block: source, measured numbers, why - and for `houses`, why this biome has them;
- every key its nearest neighbour's block has; a coat off by leaving its block out, nothing else;
- no coordinates in a biome - rules, never places;
- ported counts traceable to the Adventure (x1.8), and where cut to the room, by how much;
- the port's row in `BiomeMeasureTest.ports()`;
- the glossary row;
- the `PWT_SWEEP` and `PWT_MEASURE` output in the final message;
- a `CHANGELOG.md` line.

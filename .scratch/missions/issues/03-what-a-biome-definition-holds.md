# What a biome definition holds

Type: grilling
Status: resolved
Blocked by: 02

## Question

The exact shape of a biome kind as data (G0008): every field of its ground, roster and furniture, which
are ranges, their keys in `assets/sim/biomes.properties`, and the one place that reads them and applies
tier and ramp. Includes plain, quarry and brake written out in full as the first three blocks. Says
what is a field and what stays out (sub-variants are out of scope, but the shape must not prevent them).

## Answer

Grilled 2026-10-04 against the ticket 02 prototype (`.scratch/missions/prototype/biome-ground.html`). The first
biome stage ships **plain, quarry, brake, anthill and badlands** + the redoubt: the two crag biomes put crags in
the game at both ends of their range - mounds at 5%, masses at 13%.

**Ground** - laid by `map` in coats, each from its own stream, rock last, and nothing but base ground in a
pass's **apron**:

1. **base** - one terrain.
2. **patches** - layers in order, one line: `shape ground density sizeMin sizeMax`. Shape `rect` (small
   colour patches, what the Adventure paints) or `blob` (grown: Eden growth toward the middle, one smoothing
   pass - thicket). Ground is a terrain or `neighbour`: the base of the biome on the nearer side, or the
   biome's `fallback` when there is none or it is the biome's own.
3. **crags** - irregular rock: white noise smoothed `smoothing` times, thresholded at the density quantile,
   then repaired (every pass reachable again, passages under `clearance` widened, pockets sealed). Off at
   density 0.
4. **outcrops** - a bar `length` x `thickness`, an arm bent off one end for a share `arms` of them, placed by
   dart throwing `clearance` tiles off all other rock. Bars and Ls enclose nothing and, spaced, cannot ring
   anything - a pass is never sealed by construction; a flood fill after each one is the net. Off at density 0.

How much of each is a **density** - a fraction of the biome, laid until reached - never a count: quarry rock
lands at 10-12% p10-p90 on density against 10.5-17% on counts. Width and regions are geometry, so they are
ground too.

**Strip rules** - the same for every biome, `sim.mission.ground.*`: between two biomes a **ridge** crossed by
1-2 **passes** and now and then a **gatehouse** (ticket 13 has the border and its keys), apron 3, **clearance 2** -
no passage under ten world units anywhere, walls and border included (a brute is 5.2 across). Dealing the
order: **4** biomes (`sim.mission.biomes`), **none twice in a mission**, and never **the same base** side by
side - a dead end restarts the deal from where the order stream has got to. The pool must hold at least as
many biomes as a mission deals.

**Roster** - one line, `loose|nest strain min max`. Loose = put down unaware, as both Adventures do; nest = a
hole of that strain. No third mode. Every count, loose and nest, is drawn and scaled in **one place**,
`Roster.count(rng, pressure)`; pressure = tier `enemyPercent` x (1 + `rampPercent` x position), and
`MissionConfig.pressure(tier, position)` is the only reader of those two numbers. The tier's nest period is
01's and stays out of here.

**Furniture** - fixed ranges `points`, `barrels`, `rabbits`, `boulders` (min/max each), plus
`pickups=pickup <kind> min max; ...` keyed by `PickupKind` (crates, medkits, mushrooms, flowers). Never scaled
by tier or ramp (01). No turrets, mines or armoury caches - a finale's, or out of scope.

**Code**

| Where | What |
|---|---|
| `map` | `BiomeGround(widthMin, widthMax, regions, base, fallback, patches, crags, outcrops)`; `Patch` sealed (`Rect`, `Blob`) over a `PatchGround` (a terrain, or neighbour - one `ConfigBinder` conversion); `Crags(density, smoothing)` and `Outcrops(density, lengthMin, lengthMax, thicknessMin, thicknessMax, arms)`, each with `off()`; `StripRules`; and one deep module, `Strip.lay(rules, order, streams)` -> the `TileMap` and each biome's `TileRect`. Everything above sits behind that one call, and its tests cross the same seam. Not `Ground`: `GroundDef` already names what standing on a terrain costs. |
| `sim` | `BiomeKind`; `BiomeDef(BiomeGround ground, List<Roster> roster, Furniture furniture)` as `Defs<BiomeKind, BiomeDef>`, root `sim.biome`, in `assets/sim/biomes.properties` - the table refuses a biome without a block. `Roster` sealed (`Loose`, `Nest`); `Furniture`; `Stock` sealed with one record, `Pickup`. |
| config | Ranges as `xMin`/`xMax`, the house form (`sim.nest.broodMin`). Lists in the binder's existing `;` form; a block left out is off. No new binder code but the `PatchGround` conversion. |

Open for the build: `map` holds no generator, and `Strip` wants a stream per coat per biome - either `sim`
passes a stream factory or `Randomness` grows a way to derive one. Rounding a scaled count: `Whole` if its
rule fits a count, else one rule beside it.

**Out** - a pool weight (every `BiomeKind` is dealt, equally, until a biome needs otherwise); a name on screen
(the goal-line ticket); where anything goes inside a biome ("placement inside a biome"); looks and sound.
Sub-variants stay possible: one is another constant with a copied block, and nothing here is global to a biome.

**The blocks** - ported from the Adventures, then scaled to the wider biome (amended 01): 50-58 columns
against the Adventure's 29, so every roster and furniture range is about 1.8 times the Adventure's count, and
patches are a size bigger so a wide biome is not confetti. Points are 2-3. The Adventures' numbers are also at
their own place in the march (the quarry is fourth), so the roster ranges are recalibrated once 07 sets
`enemyPercent` and `rampPercent`. The Adventure 2 brake's three mines are dropped.

```properties
sim.mission.biomes=4
sim.mission.ground.rows=30
sim.mission.ground.apron=3
sim.mission.ground.clearance=2
# The ridge, its passes and gatehouses: ticket 13.

# The plain. Open, three patches, pebbles of rock (Adventure: 4% patches, 0.6% rock).
sim.biome.plain.ground.widthMin=50
sim.biome.plain.ground.widthMax=58
sim.biome.plain.ground.regions=3
sim.biome.plain.ground.base=plain
sim.biome.plain.ground.fallback=dust
sim.biome.plain.ground.patches=rect neighbour 0.04 4 6
sim.biome.plain.ground.outcrops.density=0.006
sim.biome.plain.ground.outcrops.lengthMin=1
sim.biome.plain.ground.outcrops.lengthMax=2
sim.biome.plain.ground.outcrops.thicknessMin=1
sim.biome.plain.ground.outcrops.thicknessMax=1
sim.biome.plain.ground.outcrops.arms=0
sim.biome.plain.roster=loose grunt 7 11; loose runner 0 4; nest grunt 1 2
sim.biome.plain.furniture.pointsMin=2
sim.biome.plain.furniture.pointsMax=3
sim.biome.plain.furniture.barrelsMin=0
sim.biome.plain.furniture.barrelsMax=0
sim.biome.plain.furniture.rabbitsMin=5
sim.biome.plain.furniture.rabbitsMax=9
sim.biome.plain.furniture.bouldersMin=11
sim.biome.plain.furniture.bouldersMax=13
sim.biome.plain.furniture.pickups=pickup medkit 1 1

# The quarry. Heavy rock bent round corners (Adventure: 3.7% patches, 9.5% rock in six outcrops).
sim.biome.quarry.ground.widthMin=50
sim.biome.quarry.ground.widthMax=58
sim.biome.quarry.ground.regions=3
sim.biome.quarry.ground.base=dust
sim.biome.quarry.ground.fallback=plain
sim.biome.quarry.ground.patches=rect neighbour 0.04 4 6
sim.biome.quarry.ground.outcrops.density=0.10
sim.biome.quarry.ground.outcrops.lengthMin=4
sim.biome.quarry.ground.outcrops.lengthMax=9
sim.biome.quarry.ground.outcrops.thicknessMin=2
sim.biome.quarry.ground.outcrops.thicknessMax=3
sim.biome.quarry.ground.outcrops.arms=0.7
sim.biome.quarry.roster=loose brute 11 14; loose spitter 11 14; loose grunt 7 11; nest brute 2 4; nest spitter 1 2; nest grunt 1 2
sim.biome.quarry.furniture.pointsMin=2
sim.biome.quarry.furniture.pointsMax=3
sim.biome.quarry.furniture.barrelsMin=5
sim.biome.quarry.furniture.barrelsMax=7
sim.biome.quarry.furniture.rabbitsMin=0
sim.biome.quarry.furniture.rabbitsMax=0
sim.biome.quarry.furniture.bouldersMin=11
sim.biome.quarry.furniture.bouldersMax=13
sim.biome.quarry.furniture.pickups=pickup crate 4 5; pickup medkit 1 1

# The brake. Undergrowth over a fifth of it (Adventure 2: 3.7% patches, 18.6% thicket, 1.4% rock).
sim.biome.brake.ground.widthMin=50
sim.biome.brake.ground.widthMax=58
sim.biome.brake.ground.regions=3
sim.biome.brake.ground.base=plain
sim.biome.brake.ground.fallback=organic
sim.biome.brake.ground.patches=rect organic 0.035 4 6; blob thicket 0.18 6 11
sim.biome.brake.ground.outcrops.density=0.015
sim.biome.brake.ground.outcrops.lengthMin=2
sim.biome.brake.ground.outcrops.lengthMax=4
sim.biome.brake.ground.outcrops.thicknessMin=1
sim.biome.brake.ground.outcrops.thicknessMax=2
sim.biome.brake.ground.outcrops.arms=0.3
sim.biome.brake.roster=loose runner 13 16; loose ant 5 7; loose brute 1 2; nest runner 2 4; nest ant 1 2
sim.biome.brake.furniture.pointsMin=2
sim.biome.brake.furniture.pointsMax=3
sim.biome.brake.furniture.barrelsMin=2
sim.biome.brake.furniture.barrelsMax=4
sim.biome.brake.furniture.rabbitsMin=0
sim.biome.brake.furniture.rabbitsMax=0
sim.biome.brake.furniture.bouldersMin=7
sim.biome.brake.furniture.bouldersMax=9
sim.biome.brake.furniture.pickups=pickup crate 2 2; pickup medkit 1 1

# The anthill, its rock as low crags: mounds round the holes (Adventure 2: 3.7% patches, 2.8% rock).
sim.biome.anthill.ground.widthMin=50
sim.biome.anthill.ground.widthMax=58
sim.biome.anthill.ground.regions=3
sim.biome.anthill.ground.base=organic
sim.biome.anthill.ground.fallback=dust
sim.biome.anthill.ground.patches=rect neighbour 0.04 4 6
sim.biome.anthill.ground.crags.density=0.05
sim.biome.anthill.ground.crags.smoothing=4
sim.biome.anthill.roster=loose ant 11 14; loose grunt 5 9; nest ant 2 4
sim.biome.anthill.furniture.pointsMin=2
sim.biome.anthill.furniture.pointsMax=3
sim.biome.anthill.furniture.barrelsMin=0
sim.biome.anthill.furniture.barrelsMax=0
sim.biome.anthill.furniture.rabbitsMin=0
sim.biome.anthill.furniture.rabbitsMax=2
sim.biome.anthill.furniture.bouldersMin=7
sim.biome.anthill.furniture.bouldersMax=9
sim.biome.anthill.furniture.pickups=pickup mushroom 9 13; pickup flower 2 4; pickup medkit 1 1

# The badlands, new: heavy crags on burnt ground, masses to fight round and behind.
sim.biome.badlands.ground.widthMin=50
sim.biome.badlands.ground.widthMax=58
sim.biome.badlands.ground.regions=3
sim.biome.badlands.ground.base=ash
sim.biome.badlands.ground.fallback=dust
sim.biome.badlands.ground.patches=rect neighbour 0.04 4 6
sim.biome.badlands.ground.crags.density=0.13
sim.biome.badlands.ground.crags.smoothing=4
sim.biome.badlands.ground.outcrops.density=0.02
sim.biome.badlands.ground.outcrops.lengthMin=2
sim.biome.badlands.ground.outcrops.lengthMax=4
sim.biome.badlands.ground.outcrops.thicknessMin=1
sim.biome.badlands.ground.outcrops.thicknessMax=2
sim.biome.badlands.ground.outcrops.arms=0.3
sim.biome.badlands.roster=loose spitter 7 11; loose grunt 9 13; loose brute 2 4; nest spitter 2 2; nest grunt 1 2
sim.biome.badlands.furniture.pointsMin=2
sim.biome.badlands.furniture.pointsMax=3
sim.biome.badlands.furniture.barrelsMin=4
sim.biome.badlands.furniture.barrelsMax=5
sim.biome.badlands.furniture.rabbitsMin=0
sim.biome.badlands.furniture.rabbitsMax=0
sim.biome.badlands.furniture.bouldersMin=4
sim.biome.badlands.furniture.bouldersMax=5
sim.biome.badlands.furniture.pickups=pickup crate 2 4; pickup medkit 1 1
```

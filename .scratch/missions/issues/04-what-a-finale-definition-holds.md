# What a finale definition holds

Type: grilling
Status: resolved

## Question

A finale as a kind: what the redoubt (point that holds afterwards, turrets, mines, held-back waves from
its mouths) and the keep (lair, boss, nests, cache, regroup) each need as data; how tier scales a wave
schedule; its watch; and the rally ground used when no earlier point was taken (Q28: the generator
always puts a point by the finale's gap, or the finale names a rally ground).

## Answer

Grilled 2026-10-04 (G0064). **A finale is a set piece**: the same layout every time, flipped north-south by the
seed, ported from the Adventures' last biomes.

**One shape, parts off.** `FinaleDef(BiomeGround ground, Hold hold, List<WaveLine> waves, List<LairLine> lair)`.
The **redoubt** is a definition with no lair; the **keep** is the same with one - as Adventure 2 has it. Both are
won by `HOLD_POINT` alone; the keep's lair sits inside the watch, so holding it means killing the boss. No new
objective.

**Laid out as data.** Every position is a tile offset `dx dy` from the finale's **anchor** - column
`x + (width - 1) / 2`, row `rows / 2` of the finale's `TileRect` (197, 15 in both Adventures). One method in `map`
computes the anchor and `sim` calls it. The **flip** is one bit from the finale's stream: it negates every `dy`
(a rect's top becomes `-(dy + h - 1)`) and turns nothing east-west, so west stays the way in.

**Ground.** `BiomeGround` reused: crags and outcrops off, no patches, width fixed at 34 (min = max).
`BiomeGround` gains **`pieces`** - fixed rock rects `dx dy w h` from the anchor, empty for every dealt biome (zero
means off). `sim` mirrors them before `Strip.lay` sees them; `map` never learns about a flip. The ridge before the
finale **spills only westward**, so the finale's west edge is straight and nothing dealt lands on a turret or a
mouth. The seed sweep asserts every layout spot is standable and reachable.

**Hold** - the point that holds afterwards and what stands round it: `post dx dy`, `watch` (a radius in world
units), `turrets`, `mines`, `barrels` (lists of `dx dy`), `pickups` (`pickup kind dx dy`), and `mouths`
(`name dx dy w h`) - where the waves come onto the field. None of it is scaled by tier (01, G0063).

**Waves** - one line each, `afterSeconds strain countMin countMax mouth`, a mouth by name (the definition refuses
one it does not have). The count goes through `Roster.count(rng, MissionConfig.pressure(tier, FINALE))` - the
tier's `enemyPercent`, **no ramp** - and resolves into today's `Wave` when the level is populated. Timing is never
tiered. The schedule is **held back** until the point is taken. Each finale names its own strains; strains drawn
from the march stay out.

**Lair** (keep only) - its ground is the finale's whole `TileRect`, derived, not data. Contents one line each,
`unit|nest|pickup kind dx dy` - a `LairLine`, resolved into `Arrival` (which holds world units) at populate - in
the order written: holes, then pickups, then the boss, as Adventure 2 places them. Positions, not counts:
`enemyPercent` never reaches it; tier does through health (the boss and everything he rises with) and the nest
period. Its six nests take `firstBroodSeconds` as G0062 says.

**Watch** - a radius in the definition, 118 as both Adventures. At 34 columns it covers the far corners and
reaches about seven tiles past the finale's west edge, into the ridge. A placement rule keeps the **last biome's
nests outside the watch**; the sweep checks it.

**Rally** (Q28) - no rally ground in the finale. **One of the last biome's 2-3 points stands in its last
`sim.mission.finale.approachColumns` (8) columns** before the ridge. Taking it moves the rally point to the finale's
approach; skipping it is a visible choice with a cost (G0055 unchanged). In prose "the last biome's last point" - no
new word (*door* is now the thing in a doorway, ticket 14). Assumes **G0061** has landed: the held point never
becomes the rally point.

**Drawn** from its own stream, equal weight, independent of tier (01).

**Code**

| Where | What |
|---|---|
| `map` | `BiomeGround` gains `pieces` (rects from the anchor); the anchor computed in one place; `Strip.lay` spills the last ridge west only. |
| `sim` | `FinaleKind { REDOUBT, KEEP }`; `FinaleDef(ground, Hold, waves, lair)` as `Defs<FinaleKind, FinaleDef>`, root `sim.finale`, in `assets/sim/finales.properties` - the table refuses a finale without a block. `Hold`, `Mouth`, `WaveLine`, `LairLine`; populate resolves lines into `Wave` and `Arrival` and mirrors on the flip. `Waves`, `Lair`, `WaveSystem` and `ObjectiveSystem` untouched. |
| config | The binder's existing `;` lists of space-separated fields; no new conversion expected. `sim.mission.finale.approachColumns=8` beside the tiers. |

**Dropped in the port**: the keep's two ants asleep in its far corners. A finale's creatures arrive by wave or rise
from the lair; a third way would be a `Hold` field for two units.

**Watch in play**: no first-stage biome has frogs (03 ships no mire), so a mission meets its first frog in the
finale's waves. Fine for a strain the Adventures teach; revisit if it reads as unfair.

Separate: the boss stands with his back to the players (`.scratch/bugs/issues/01`). Fix before or with the keep.

**The blocks** - ported from both Adventures, offsets from (197, 15); counts become ranges round the port.

```properties
sim.mission.finale.approachColumns=8

# The redoubt (Adventure 1). A bare square to see across: four guns round the post, mines across the approaches
# from each corner, barrels between the guns, a crate and a flamer behind the post. Waves from all four corners.
sim.finale.redoubt.ground.widthMin=34
sim.finale.redoubt.ground.widthMax=34
sim.finale.redoubt.ground.regions=3
sim.finale.redoubt.ground.base=dust
sim.finale.redoubt.ground.fallback=ash
sim.finale.redoubt.ground.pieces=-9 -3 2 3; 8 1 2 3; -1 -10 3 1; 0 9 3 1
sim.finale.redoubt.hold.post=0 0
sim.finale.redoubt.hold.watch=118
sim.finale.redoubt.hold.turrets=-5 -5; 5 -5; -5 5; 5 5
sim.finale.redoubt.hold.mines=-9 -8; -6 -6; -11 -4; -7 -10; 9 -8; 6 -6; 11 -4; 7 -10; -9 8; -6 6; -11 4; -7 10; 9 8; 6 6; 11 4; 7 10; -4 0; 4 0; 0 -5; 0 5
sim.finale.redoubt.hold.barrels=-5 -3; 5 -3; -5 3; 5 3; 0 -8; 0 8; -12 0; 12 0
sim.finale.redoubt.hold.pickups=pickup crate -3 0; pickup armoury_flamer 3 0
sim.finale.redoubt.hold.mouths=nw -14 -13 4 4; ne 12 -13 4 4; sw -14 8 4 4; se 12 8 4 4
sim.finale.redoubt.waves=6 grunt 9 11 nw; 5 grunt 9 11 se; 4 runner 7 9 ne; 7 spitter 4 6 sw; 4 runner 7 9 sw; 8 brute 4 6 ne; 5 frog 5 7 se; 4 runner 9 11 nw; 8 grunt 11 13 sw; 4 spitter 4 6 nw; 8 brute 5 7 se; 4 runner 11 13 ne

# The keep (Adventure 2). The redoubt on burnt ground with ants in its waves, and at its east end the boss
# grunt rising among a nest of every strain that walks and a cache of every gun, once every player is inside.
sim.finale.keep.ground.widthMin=34
sim.finale.keep.ground.widthMax=34
sim.finale.keep.ground.regions=3
sim.finale.keep.ground.base=ash
sim.finale.keep.ground.fallback=dust
sim.finale.keep.ground.pieces=-9 -3 2 3; 8 1 2 3; -1 -10 3 1; 0 9 3 1
sim.finale.keep.hold.post=0 0
sim.finale.keep.hold.watch=118
sim.finale.keep.hold.turrets=-5 -5; 5 -5; -5 5; 5 5
sim.finale.keep.hold.mines=-9 -8; -6 -6; 9 -8; 6 -6; -9 8; -6 6; 9 8; 6 6; -4 0; 4 0
# No east barrel: the boss rises there, and his first ring would set it off in his own face.
sim.finale.keep.hold.barrels=-5 -3; 5 -3; -5 3; 5 3; -12 0
sim.finale.keep.hold.pickups=pickup crate -3 0; pickup armoury_laser 3 0
sim.finale.keep.hold.mouths=nw -14 -13 4 4; ne 12 -13 4 4; sw -14 8 4 4; se 12 8 4 4
sim.finale.keep.waves=6 grunt 9 11 nw; 5 ant 3 3 se; 4 runner 7 9 ne; 7 spitter 4 6 sw; 5 ant 3 5 nw; 8 brute 3 5 ne; 5 frog 5 7 se; 4 runner 9 11 sw; 6 ant 4 6 ne; 8 grunt 11 13 se; 6 ant 5 7 sw
sim.finale.keep.lair=nest grunt 13 -6; nest spitter 9 -3; nest runner 16 -3; nest frog 10 5; nest ant 16 3; nest brute 13 6; pickup armoury_shotgun 7 -3; pickup armoury_railgun 7 0; pickup armoury_laser 6 3; pickup armoury_flamer 16 0; unit boss_grunt 13 0
```

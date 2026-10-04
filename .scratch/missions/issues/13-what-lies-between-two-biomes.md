# What lies between two biomes

Type: prototype
Status: resolved

## Question

Mateusz, 2026-10-04: the one-tile rock wall between biomes looks odd. Widen it and give the crossing a
transition - mountain passes, or small transition rooms - or find something better.

## Prototype

`prototype/biome-ground.html`, "Border between biomes". Four borders, 200 four-biome missions each: every one
connects start to finale, with no biome failing its own check.

| Border | What | Reads | Cost |
|---|---|---|---|
| wall | one column, 1-2 straight gaps (the Adventures') | a ruled line | - |
| **ridge** | a solid core 3 thick whose edges wander up to 3 tiles into each biome (a smoothed walk per row, one-tile notches filled), crossed by 1-2 passes 4 wide that bend up to 4 rows on the way through | a mountain range; the crossing still feels like a crossing | ~8 columns a mission; 1 slot tile in 200 missions, a notch the fill missed - the build's sweep holds it at zero |
| belt | 10 wide, no wall: the two grounds meet along a wavy line among crags at 45%, two passes carved so it always connects, narrow runs widened, pockets sealed | the most natural | the most porous - pressure from the next biome leaks through wherever the crags leave room |
| gatehouse | a ridge 12 wide whose one pass runs through an 8 x 9 clearing, entered on one row and left on another | works; a heavy block until rooms exist | ~10 more columns where dealt |

Not prototyped: a **chasm or river** with bridges or fords - walk across only there, shoot across anywhere.
`Terrain` declares one thing, whether it can be stood on, and sight follows it, so ground you can see and
shoot over but not cross is a second property and an ADR.

## Answer

- A **ridge** between every two biomes and between the last biome and the finale; the way through it is a
  **pass**. "Gap" was the wall's word. Amends 01 (a rock wall with 1-2 gaps) and 03's strip rules.
- Laid by `Strip.lay`: the ridge, then its passes carved through it and through what spilt into each biome, so
  no later coat can close one; doors on the ridge's edge columns; an **apron** that follows a bent pass out
  through the spill; notches filled; then the whole strip flooded from the start.
- **Gatehouses** ship as a proof of concept with the ridges: a share of ridges (`gatehouseShare` 0.25 - about one
  a mission) is wider and has one pass through a clearing. Improved or expanded in 12; a room on a border is
  probably not the same thing as a room inside a biome.
- The **belt** and the **wall** stay in the prototype only, to compare against.
- The **chasm** is an idea for later, not designed.
- Which biome a ridge tile is in is 06's.
- A mission is now about 255-300 columns.

Keys, `sim.mission.ground.*` (replacing 03's `gapHeight`, `gapsMin/Max`, `gapApart`):

```properties
sim.mission.ground.ridgeWidth=3
sim.mission.ground.ridgeRough=3
sim.mission.ground.passWidth=4
sim.mission.ground.passesMin=1
sim.mission.ground.passesMax=2
sim.mission.ground.passApart=6
sim.mission.ground.passJog=4
sim.mission.ground.snake=8
sim.mission.ground.gatehouseShare=0.25
sim.mission.ground.chamberWidth=8
sim.mission.ground.chamberHeight=9
```

# Sketch: the `new-biome` skill

Status: draft for ticket 10. Becomes `.claude/skills/new-biome/SKILL.md` with the first biome stage. Tickets
03 and 06 are in; 07 is still open. `OPEN (ticket)` marks a step still waiting on one.

---

```
---
name: new-biome
description: Add a biome to Project-W's missions as data - a constant, a block in biomes.properties, and the
  seed sweep that proves the generator lays it and keeps its passes open. Use when porting one of the Adventures'
  biomes or adding a new one, once `feature` has cleared it.
---
```

# Adding a biome

A **biome** is a kind (G0008): one `BiomeKind` constant and one `sim.biome.<name>` block in
`assets/sim/biomes.properties` - no class, no scenario, no branch. The mission generator deals it from the
pool, `map` lays its ground (`BiomeGround`, through `Strip.lay`) and `sim` puts its roster and furniture on that
ground. The steps every kind shares - constant, block, glossary - are `new-kind`'s; this skill is what a biome
adds to them.

## 1. Fit

For every trait the design gives the biome, name the field that carries it: ground, roster or furniture.
Done when every trait has a field.

- A trait with no field is new behaviour: `new-system` first, on the definition every biome has, off at zero.
- A strain it needs that does not exist yet: `new-kind` first.
- A turret, a mine or a gun cache: a finale's or out of scope, never a biome's (ticket 01).

## 2. Ground

`map` lays it in coats, each from its own stream: **base**, then **patches** in order, then **crags**, then
**outcrops**. Rock always wins over a patch, and a pass's **apron** keeps base ground only. How much of a coat
is always a **density** - a fraction of the biome - never a count.

| Coat | Keys under `sim.biome.<name>.ground.` | Reach for it when |
|---|---|---|
| base | `base`, `fallback` | always |
| patches | `patches=rect\|blob <terrain\|neighbour> density sizeMin sizeMax; ...` | `rect` for small patches of colour, `blob` for ground worth going round (thicket). `neighbour` takes the nearer biome's base, or `fallback` |
| crags | `crags.density`, `crags.smoothing` | irregular masses; more smoothing, fewer and bigger. Leave the block out for none |
| outcrops | `outcrops.density`, `lengthMin/Max`, `thicknessMin/Max`, `arms` | rock bent round a corner - the Adventures' shape. Leave the block out for none |

Plus `widthMin/Max` and `regions`, which are geometry and so ground too. A biome's regions are its own -
every region lies inside one biome, and a tile is in its region's (G0062) - so `regions` is at least 1.

- **Porting an Adventure biome**: measure it first - rock, thicket and patch density, and the outcrop sizes.
  `.scratch/missions/prototype/biome-ground.html` transcribes both Adventures and prints the measurements
  beside six generated seeds; add the biome's block there and tune until the two read alike.
- Done when the sweep's median density sits within a point of the measured one for rock and for thicket, and
  a rendered seed reads as the same place. Mateusz judges the second.

## 3. Roster and furniture

- `roster=loose|nest <strain> min max; ...` - loose is put down unaware, a nest is a hole of that strain. The
  ranges are the biome at the first position on a medium tier; tier and ramp scale every count through
  `Roster.count`, and nothing else does. OPEN (07): the numbers that calibrate against.
- A nest wakes only for somebody **in its own biome** and within `wakeRadius`, and broods
  `firstBroodSeconds` after waking (G0062). A nest near a ridge is not woken from the far side, so a biome
  never counts on its neighbour having stirred its nests; the finale is the mission's last biome, and its
  nests wake the same way.
- `furniture.pointsMin/Max`, `barrelsMin/Max`, `rabbitsMin/Max`, `bouldersMin/Max`, and
  `furniture.pickups=pickup <kind> min max; ...`. Never scaled.
- Porting: count the Adventure biome's own, one entry per strain and way, scale by the width (an Adventure
  biome is 29 columns, a mission's 50-58, so about 1.8), and drop what a biome may not hold.

## 4. Tests

- **The seed sweep** - one, generic, never copied per biome: over many seeds x 3 tiers, every pass of every
  biome connects, no pockets, no passage under `clearance`, every point and crate reachable (ticket 01). A
  biome joins it by being a constant. Done when it is green with the biome in the pool.
- **What the design promises in a number** - one test per promise, as `new-kind` says ("the quarry is the
  rockiest biome", "only the brake has thicket over a sixth of it"). Never the ground's connectivity, which
  the sweep owns.
- **Never which biome a tile is in.** That is `Strip`'s own test, once: every tile in one biome, no region
  across two, a ridge tile in the biome whose ground it shows. A biome joins it by being a constant.

## 5. Finish

`verify.sh` green. Glossary row for the biome's word (`docs/glossary.md`, *Levels and objectives*). The final
message lists the block's keys with values and what each does to the feel.

## Reviewer checks

- The block names every key its nearest neighbour does; a coat is off by leaving its block out, nothing else.
- The pool still deals: a mission takes `sim.mission.biomes` of them, none twice and never the same base side
  by side - so the pool needs at least that many biomes, with bases enough to alternate.
- No coordinates anywhere: a biome is rules, never places.
- `regions` at least 1: a biome's regions are its own and never shared with a neighbour.
- No armoury pickup in `furniture.pickups` (ticket 01: loose weapons are out of scope).

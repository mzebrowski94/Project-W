# G0068: A biome is populated by rules in order, never by coordinates

**Status.** amended by G0070: a biome short of room is placed again, and its counts fit the room it has; amended by G0082: turrets by points; amended by G0089: geysers, kept off points, nests and ways in; amended by G0110: a biome counts sites, and crates are only its houses' stock; amended by G0111: a worked site in its house first; amended by G0112: a house may hold a house pack; amended by G0113: spires after the geysers; amended by G0114: a worked site in the open after the housed ones, before the points

**Trigger.** Changing where anything a biome's roster or furniture counts is put down, the order it goes down in,
what a house holds, or what happens when a rule cannot be met.

**Enforced by.** `MissionSweepTest.holdsEveryMissionToTheRules` in `core`, through `MissionFaults` (every point,
crate and house's stock reachable from the start; nothing on an apron or a doorstep; nothing hostile within
`startKeepOff`; nests off the entry pass, the points, each other and every pickup; nothing hostile and no point in a
house; one point a band and the last in the last `approachColumns`; crates by points; a pass short of its count fails
the seed) - 64 seeds on every tier on every verify, 2000 under `PWT_SWEEP=1`. Missions ticket 14 holds the reasoning
and the keys.

**Constraint.**

- **Ordered `Scatter` passes**, one biome at a time from its own stream, most constrained first: the start, points,
  house stock, nests, pack anchors, crates by points, medkits and food, barrels, boulders, rabbits. Each keeps off
  what went down before it by radii in `sim.mission.placement.*`, measured crow-flies, and every pass keeps off
  aprons and doorsteps.
- **A rule that cannot be met is a generator fault.** In play a short pass places what fits; the sweep fails the
  seed. Counts are `Roster.count`'s and the furniture's (03), drawn before placement and never rescaled by it.
- **The start** is the first biome's west edge, middle rows, with nothing hostile within `startKeepOff`.
- **Points** stand one per equal column band, so the goal line leads east; the last biome's last band is its last
  `approachColumns` (G0064).
- **Nests** keep off the entry pass, the points and each other. **Loose creatures** go down in packs round anchors.
- **A house holds the biome's stock** - crates, then medkits, up to `houseStock` - and nothing hostile, no point.
- **Crates** left over stand by points; **barrels** by nests and pack anchors.

**Why in order.** The constraints chain: a nest is kept off points, so points go first; a crate is put by a point and
a barrel by a nest, so both come after. The most constrained goes down while there is most room, so a shortfall
means a rule is too tight, not that the order was unlucky.

**Why crow-flies.** A nest wakes on a straight-line radius (G0062); a keep-off measured along routes would disagree
with the rule it exists to serve, and cost a field a biome.

**Why a house holds stock and nothing hostile.** A house is a place you choose to open (G0069). What is inside is the
reason to open it, and a closed room full of creatures is a trap the player cannot see coming through a shut door.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Scored candidates* - best-candidate sampling against weighted rules. Always places something, so a rule
becomes a preference and "never" cannot be tested.

**No:** *Keep-offs measured along routes* (a Dijkstra map from the entry pass). Disagrees with the wake radius and
builds a field a biome for a measure the player does not walk by.

**No:** *Points scattered freely.* Two can land at the west end and leave the east of the biome with nothing to take.

**No:** *Nests guarding points.* Taking ground would always wake a hole on the spot; a point is kept off nests by more
than its own rouse.

**No:** *A (loose) pack waiting inside a house*, or *a point inside a house.* (A house pack is G0112's.) A house is a cache, and its door is opened
rather than fought through.

**No:** *Silently placing fewer.* A full biome and a broken rule look the same to a caller handed no answer.

**Revisit when** play shows the same rhythm in every biome reads as stale, or a biome genuinely needs a rule of its
own - then it is a field on `BiomeDef`, off at zero for every other (missions ticket 10).

## Amended by G0082

A pass after the crates by points puts a biome's turrets, `furniture.turretsMin/Max`, each within `pointReach` of
a point, a point at a time from a drawn one, off every nest. Only the plain asks for any (1-2). Provisional, for
review.

## Amended by G0089

A pass after the turrets and before the medkits and food puts a biome's geysers, `furniture.geysersMin/Max`, each
`geyserKeepOff` off every point, nest and way into the biome, the start among them, and off everything already
standing by its own radius drawn from the pass's stream; the passes after keep off it, so no barrel stands in its
fire. Only the cut asks for any (3-5). Provisional, for review.

## Amended by G0111

A biome's worked sites - a mainframe - are put down first of all, before its points: on their own chance from the
biome's own stream, in a house of the biome, on a tile beside a wall and clear of its doorways; each takes the column
band it stands in, and the points keep `pointApart` off it, nests `pointKeepOff` and geysers `geyserKeepOff`. The
house's stock is laid round it.

## Amended by G0112

A house may hold a **house pack**: each house of a biome - never a gatehouse - holds a pack of each of its roster's
`house` lines on `sim.mission.placement.housePackChance`, rolled from the biome's own stream, laid inside it straight
after its stock, off its doorways; only in a house wholly `startKeepOff` off the start and outside the finale's watch.
Its creatures wander only inside it until they notice somebody (G0112). Points and loose packs still never stand in a
house: *a pack waiting inside a house* below now means a loose one.

## Amended by G0113

A pass after the geysers and before the medkits and food puts a biome's emplacements - the fen's spires,
`furniture.spires` - as a boulder stands, `emplacementKeepOff` off every way in (the start's distance in a first biome),
every nest and the finale's watch, and off every site's edge, never on a thicket's tile; each is dealt its cycle from
the pass's stream as it goes down. Only the fen asks for any (3-5). Provisional, for review.

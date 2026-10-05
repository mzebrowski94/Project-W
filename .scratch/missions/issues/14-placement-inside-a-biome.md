# Placement inside a biome

Type: grilling
Status: open
Blocked by: 12

## Question

Where everything a biome's roster and furniture count goes, relative to each other, the passes, the ridges and
rooms - as **rules, never coordinates**. Graduated from map.md's *Not yet specified* by 11. Grilled after 12 (a
room's contents are placement rules; the two may merge) and **before stage 7**, the first biome stage, which waits
for it. Run `game-patterns` first (`Scatter` is the house tool), then `feature`.

## Handed down

- **Counts are settled elsewhere**: `Roster.count` for creatures (tier x ramp, 03, G0063); furniture ranges are
  fixed (03). This ticket says *where*, never *how many*.
- **Rules are strip-wide** (`sim.mission.placement.*`) unless a biome genuinely needs its own - then a field on
  `BiomeDef`, off at zero for every biome (10, Q1).
- **From 04 / G0064**: one of the last biome's points stands in its last `sim.mission.finale.doorColumns` (8)
  columns; the last biome's nests stand outside the finale's watch.
- **From 01, 02, 03, 13**: nothing but base ground in a pass's apron, and no scatter there either; clearance 2.
- **From G0062**: a nest wakes only for somebody in its own biome and within `wakeRadius`.
- **Built from `RngStream.MISSION`**, derived per sub-concern and per position (05); build-time only.
- **Checked by `MissionSweepTest`** (10): every point and crate reachable, the two rules from 04.

## Open, roughly in the order they unblock each other

- **Where the squad starts** - the first biome's west edge, and what may stand near it.
- **Points** - 2-3 a biome: spread along the march or free; distance from passes, ridges and each other; in a room
  or never in one.
- **Nests** - distance from the pass you come in by, from points, from each other; how near a ridge (a nest by the
  far side of a ridge is quiet until you cross, G0062).
- **Loose creatures** - put down unaware (03): spread, clumped round something, kept off the entry pass.
- **Crates, medkits, mushrooms, flowers** - near points, in rooms, or anywhere; how far from nests.
- **Barrels, boulders, rabbits** - by rock, by points, anywhere.
- **What a house holds** - 12 settled the house (ground only); what stands in one is here.
- **Doors** - folded in here by 12's grilling; see *Doors* below.
- **The technique** - one `Scatter` pass per kind with exclusion radii, a ranked list of candidate spots, or zones
  per biome; and what happens when a rule cannot be met.

## Doors

Mateusz, 2026-10-05, during 12's grilling - folded into this ticket rather than a ticket of its own. A new
mechanic: `feature` before anything, and a stage may have to grow for it.

> Rooms will feature two doors (placed on whichever wall is appropriate). Doors can be opened by Commanders and
> Troopers (this process takes a few seconds and is visualized by a progress bar - similar to the overheat bar,
> but green). For now, doors cannot be closed once opened. If someone is inside the room, enemies can target and
> attack the doors in a repeating cycle: enemy leap + enemy bouncing off the door + screen shake for nearby
> Commanders - continuing until the door is destroyed. Doors can only be destroyed by explosions and "HEAVY"
> weapons, such as the Bazooka or Flamethrower. For transitions between biomes, Troopers can open the doors
> themselves without assistance.

From 12: every **house** has exactly two **doorways**, 3 wide, in two different walls; a **gatehouse**'s two are 3
wide too, so one door fits every doorway. `Strip.Laid` reports each house and its doorways; a door is an entity
`sim` stands in one. The map's connectivity checks treat a doorway as open ground.

Open:

- Is a door a kind (G0008: a constant, a definition, a block) - scenery with health that only some blows land on?
- **"HEAVY"**: a field on the weapon (off at zero) or on the blow; explosions are already blasts.
- **Opening**: who may (commander, trooper - a trooper on his own only at a gatehouse?), how long, interrupted by
  what, the green bar's place among the bars (pillar 5).
- **Closed**: what the flow field makes of a closed door - a route through it or not, for which side; whether a
  door blocks sight and shots as a wall does.
- **"Someone inside"**: how an enemy knows (perception, the house's rect from `Laid`), and what "inside" is.
- **The attack**: a leap that bounces off is the leap (G0012's) aimed at a door; the shake is a **shake**
  published by the blow, felt by commanders near it.
- **Closed at the start** everywhere, or some houses open; a gatehouse's doors and the march (pillar 1).

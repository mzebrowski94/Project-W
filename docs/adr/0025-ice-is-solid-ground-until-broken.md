# 0025: Ice is solid ground until broken

**Status.** amended by G0116: a hunter's route runs through ice; amended by G0117: a trooper shoots ice in his way

**Trigger.** Building or changing ice; adding anything else that stands across a way until it is broken - a
barricade, rubble, a web; deciding where generation puts such a thing; adding a reader of "is this tile solid" or of
"what door is this" in `sim`.

**Enforced by.** `IceTest` (it holds a man and stops a shot as a shut door does; the squad's shots chip it to nothing
at `sim.ice.health`, and then its tiles are open ground; the swarm's shots take nothing; a grenade's blast breaks it
outright; a leaping alien lands a door's leap on it and breaks it in the end; nobody opens it; it shows how worn it is;
a hunter goes round it when there is a way round); `CoatsTest.icesChokepointsOfTheMazeAndNeverAClearingOrAWayIn`;
`MissionTest.standsIceAcrossEveryWayTheStripIcedAndNoneOnAWayIn`, `.standsAShutDoorInEveryDoorwayOfEveryHouse`;
`MissionSweepTest.holdsEveryMissionToTheRules` through `MissionFaults.ice` and `StripFaults.reach` (with ice counted
open).

**Provisional, for review** - settled in an AFK run (ticket 10, G0115), not with Mateusz.

**Amends ADR 0022.** Its *Revisit when* - "a second thing blocks for a while and wants the same place" - is here: ice is
the second thing `Solid` answers for, and it answers through the same door.

**Constraint.**

- **Ice is a door nobody opens.** An entity with a `Door` - its run's tiles, `openTicks` zero, which is "nobody opens it
  by hand" (`Door.opensByHand`) - and `Health` (`sim.ice.health`), `Damage`, `Shake` and `Look.ICE`, spawned by
  `Spawner.spawnIce` for every run the strip iced. So `Solid.derive`, which takes every shut door, takes ice beside the
  doors with no second family and no second array: the solver's clamp, a shot into it, a blast's line, perception's
  clear line, a shooter's target and the siege (G0069) all treat it as a shut door, because to them it is one. A
  hunter's route is the exception (G0116): it is flooded over `Solid.routes()`, which counts every door with
  `routedThrough` - ice - open, so a hunter breaks ice in its way where it goes round a door (G0075). Broken, it is marked dead and `DoorSystem` derives the place again, as for a
  door.
- **What sets it apart is fields, never a branch on a kind** (two in ticket 10, a third, `routedThrough`, by G0116). `openTicks` zero: `Reach` never offers it to the
  action key, a trooper whose way it shuts waits for nobody at it, and `DoorSystem` counts no work on it.
  `Door.chipped`: every shot from the squad's side lands its blow on it, where a door takes only a heavy gun's
  (`ProjectileBoundsSystem`); the swarm's shots land on neither. A blast breaks either outright, a leap lands
  `sim.door.leapDamage` on either.
- **Never a write into the `TileMap`** (ADR 0022). `map` marks where ice stands (`Ice`, `Strip.Laid.ice`) as it marks
  doorways; the ground under it stays its own, so every connectivity check and pass counts an iced way as open - it can
  always be broken. Placement keeps everything off its tiles.
- **Where.** After every other coat, the chokepoints of the crags' maze: a run across a way, along a row or down a
  column, between two solid tiles, at most the clearance and one long, open ground either side of every tile of it,
  and - shut - its two sides not joined within 16 steps, a bounded flood (a local cut test), so it is a way and not a gap
  beside a lone rock. The runs across one way are one chokepoint, its middle run. A share of them (`ground.ice.share`,
  0.4 in the rift) iced, in an order the biome's stream deals, each the clearance off every other; never in a clearing,
  never within the clearance of a kept tile - a pass's apron, the start, a house - so no way in is iced.
- **Seen** (pillar 5): light blue (`graphic.color.ice`), a little translucent; darker and cracked across each square as
  its health runs out (`Appearance.wear`, `graphic.door.crackWidth`, `.crackDim`). A door leapt at shows its wear the
  same way. Broken, it is gone; no shatter.

**Provisional, for review (AFK run).**

1. **Ice is a `Door`.** Turned down: an `Ice` component and a second family in `Solid.derive` - every reader that
   finds a door by handle (`ProjectileBoundsSystem`, `BlastSystem`, `EnemyLeapSystem`'s siege) would need a second
   loop, a second family and the door's geometry copied, and a trooper's `doorAhead` would have to tell the two apart;
   a door table (`DoorKind`, `DoorDef`) - the right shape once a third such thing comes, a layer for two.
2. **The chokepoint rule**: the narrow crossing and the 16-step cut test above. Turned down: a medial-axis or
   distance-transform pass (a second technique for what the crag repairs already measure in runs); every narrow run
   (ice beside lone rocks, many runs across one way).
3. **Share 0.4** of the chokepoints, the ticket's: about four ice a rift (measured over 32 strips). Turned down: a
   count, which would not follow a maze laid thicker or thinner.
4. **Health 300, every squad shot chipping by its damage**: six sidearm shots, a moment of the squad's fire - ice is a
   delay, not a fight. Turned down: only heavy guns, as a door (the squad would have nothing that opens a rift).
5. **Wear on every door**, not on ice alone: a door leapt at cracking is the same fact told the same way. Turned down:
   a look-only rule for ice (a branch on its kind in the renderer).
6. **No second solidity.** Ice blocks sight and shots as it blocks a man - one `Solid`.

**Scope.** `sim` and `map`, while a world runs and while one is laid. Not a wall the squad builds, not ice that
reforms, not slippery ground.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Writing ice into the `TileMap`*, or a terrain of ice that a break rewrites - ADR 0022's, again: the map is a
function of its seed.

**No:** *Ice as circle obstacles* - ADR 0022's door as circles: a crowd leaning on it squeezes through its seams.

**No:** *An `Ice` component beside `Door`* - a second family every door reader must learn.

**No:** *Ice on a way in* - a pass's apron, the start or a doorstep iced, so a biome is entered through a wall.

**Revisit when** a third thing stands across a way until broken - then a door table (`DoorKind`, `DoorDef`) with its
opening, chipping and look as fields - or ice wants to block a man but not a shot.

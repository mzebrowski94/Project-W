# G0089: A geyser is a second source of fire

**Status.** amended by G0091: 5-7 in the caldera as well (provisional, for review)

**Trigger.** Adding or changing anything placed on the ground that erupts, burns or goes off on a clock of its own;
proposing that a geyser's fire differ from a blast's; reusing geysers in another biome (ticket 16, a lava biome).

**Enforced by.** `GeyserTest.liesDormantAsGroundAManStandsOnUnharmed`, `.eruptsInFireOnNobodysSideOnceItHasFilled`,
`.wearsItsFireMarkWhileItFillsAndNothingWhileItIsQuiet`, `.goesQuietOnceItsFireIsOutAndThenEruptsAgain`,
`.keepsATimeOfItsOwn`, `.neverLiesQuietForLessThanTheFloor`, `.aTrooperGoesRoundAnEruptionOnOpenGround`,
`.aTrooperHoldsShortOfAnEruptionAcrossACorridorAndGetsPastOnceItIsQuiet`;
`BiomePromisesTest.theCutAndTheCalderaAreTheBiomesThatLayGeysersTheCalderaMore`; `MissionTest.putsBetweenItsFewestAndMostGeysersInEveryBiomeEachInItsRange`;
`MissionSweepTest` through `MissionFaults.geysers`; `SystemOrderTest` (before `SlickSystem`).

**Provisional, for review** - settled in an AFK run (playtest ticket 15), not with Mateusz.

**Amends G0035** and **Amends G0068** (a placement pass for geysers). Its scope was "every explosion": fire came only from a blast dying. A geyser is a second source of
the same fire - not a second kind of fire.

**Constraint.**

- **A geyser** is a brown circle on the ground, `sim.geyser.radiusMin/Max` (2-5) **in world units** - the unit every
  neighbouring placement key uses: a boulder's `sim.obstacle.radius`, a trunk's, `sim.mission.placement.*` (a tile
  is five). Drawn per geyser from its placement pass's own stream, as a trunk is.
- **Its cycle**: dormant `sim.geyser.dormantMinSeconds`-`dormantMaxSeconds` (4-9 s), drawn per geyser **per cycle**,
  keyed off the level's seed, the geyser's handle and the cycle (`RngStream.GEYSER`, G0086's keyed shape) - so it
  advances no stream, two geysers keep different time, and a snapshot carries only the cycle count. **Never under
  4 s**: `GeyserConfig` refuses a shorter `dormantMinSeconds` at load. Then filling `sim.geyser.fillingSeconds`
  (1.5 s), then the eruption, burning `sim.geyser.burnSeconds` (3 s); it is dormant again only once that is out.
- **Dormant it is ground**: no collider, so the solver lets anything stand on it and avoidance never steers round it.
  Only the eruption is avoided.
- **The eruption is ordinary fire**: `GeyserConfig.eruption` is `FireConfig.forBlast(radius)` - the fire a blast of the
  geyser's radius leaves, all of `sim.fire.*` (blobs, spread, damage, shove, swell and collapse) - with every circle
  burning `burnSeconds`. On nobody's side, wounding and shoving a man (G0053), steered round by everything (G0046).
- **It arrives by something dying** (G0018): `GeyserSystem` spawns an eruption - a carrier of that fire, born dead -
  and `SlickSystem` lays it in the same death window; the reaper takes the carrier. No new way to lay fire.
- **The warning is visible** (pillar 5): filling, the geyser's charge is how far it has filled, on the `FIRE` look;
  the client draws it as the fire mark at its core, growing and pulsing (`graphic.geyser.pulseHz`). No number.
- **Where**: a placement pass after turrets and before the pickups, barrels and boulders (G0068), each geyser
  `sim.mission.placement.geyserKeepOff` (15) off every point, nest and way into its biome, the start among them, and
  off everything already standing by its own radius; what comes after keeps off it, so no barrel stands in its fire.
- **Counts**: `sim.biome.<b>.furniture.geysersMin/Max` - 3-5 in the cut, 5-7 in the caldera (G0091, provisional, for
  review), zero everywhere else (zero is off).
- **Order**: `GeyserSystem` at 40, after `BloatSystem` and before `MineSystem` - inside the death window, before
  `SlickSystem`, without coming between the mine and the blast or the blast and the slicks (`docs/simulation-order.md`).

**Why a second source and not a new hazard.** Everything a geyser's fire must do - catch everybody, be steered round,
wound and shove, swell and collapse - fire already does, and is tested for. A hazard of its own would be a second
place for those rules to drift (G0035's first DO NOT BUILD).

**Why sized as a blast of its radius.** `forBlast` already keeps its fire inside the circle it is sized by (spread
under one), so a geyser's fire covers the geyser and no more: the brown circle is the danger, drawn before it burns.

**Why its own burn length.** A blast's fire burns 5-10 s once. A geyser goes off again in 4-9 s, so a blast's burn
would outlast its quiet and the corridor would never open: a rhythm to time becomes a wall.

**Scope.** Geysers, wherever a biome counts them. Not lava ground (ticket 16's), not fire that spreads.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `Fire` component or a geyser that lays fire itself.* G0035's rule: the fire arrives through the one slick
reaction, by something dying.

**No:** *A dormant geyser that is avoided or solid.* It is ground a man walks across; going round a quiet geyser would
make the cut's corridors narrower all the time for a danger that is there a third of it.

**No:** *A dormancy drawn from a shared stream, or one draw for every geyser.* A shared stream reshuffles every other
draw when one is retuned; one draw makes a field of geysers one clock.

**No:** *A countdown on the geyser* - a number to read. The fire mark filling and pulsing is the whole warning.

**No:** *A geyser hold of a trooper's own* (ticket 15's open point): troopers wait by the avoidance every fire already
gets, made to read the wall it is against (G0090).

**Revisit when** a biome wants a geyser that erupts in something other than fire (venom, glue - an `eruption` that is
not `forBlast`), or one set off by something standing on it rather than a clock.

# G0091: Lava is ground nothing stands on, and the caldera is laid on it

**Status.** accepted

**Trigger.** Adding or changing a terrain nothing stands on; proposing that lava let shots or sight across, that it
hurt rather than block, or that it be laid as a patch; changing the caldera's ground, furniture or roster; a biome
of its own for another solid terrain.

**Enforced by.** `TerrainCollisionTest.stopsAUnitWalkedIntoLavaAsIntoRock`,
`.stopsAProjectileThatFliesIntoLavaAsIntoRock`; `CoatsTest.laysCragsAsTheTerrainTheyNameAndRepairsThemAsRock`,
`.refusesCragsOfGroundSomethingCouldStandOn`; `BiomePromisesTest.theCalderaIsTheOneBiomeOfLavaPoolsOnAsh`,
`.theCutAndTheCalderaAreTheBiomesThatLayGeysersTheCalderaMore`, `.theCalderaIsTheOneBiomeWithMagmaSpitters`;
`GraphicConfigTest.mapsEveryTerrainToAColour`; `MissionTest` (coverage: the caldera at every position);
`MissionSweepTest` (its passes open, its room, over the shipped pool).

**Provisional, for review** - settled in an AFK run (playtest ticket 16), not with Mateusz.

**Amends G0089**: geysers in a second biome, more of them.

**Constraint.**

- **`Terrain.LAVA`**, impassable. There is **one solidity** in this codebase - `Terrain.passable`, read through
  `Solid` by the solver's clamp, a shot flying into the ground, a blast's line, perception's clear line and a
  shooter's choice of target alike - so lava blocks all of them exactly as rock does. Its own constant only so it is
  drawn as what it is: `graphic.terrain.lava`, a glowing orange-red, rimmed off its own colour as rock is. A look,
  not a number.
- **Laid as crags.** A crag coat names what its masses are: `crags.terrain`, `rock` in the anthill and the badlands,
  `lava` in the caldera, never anything passable (`Crags` refuses it). The crag coat's repairs - every end reached,
  no passage under the clearance, pockets sealed (as the crags' own terrain) - are what keep a pool from closing a
  pass; a patch has none of them, which is why `PatchGround` still refuses solid ground.
- **The caldera**, `BiomeKind.CALDERA`: ash, lava pools at 0.10 density smoothed 5 (fewer and bigger than the
  badlands' rock masses), blobs of neighbour ground as the fen's, no house, no tree, no rock of its own but 0-2
  boulders; barrels kept (4-6), 5-7 geysers - more than the cut's 3-5. Roster: magma spitters 3-5, spitters 2-3,
  grunts 6-8, ants 2-3, a grunt nest. Dealt from the pool as every biome is (G0068, G0070); its ash base is the
  badlands', so the deal never lays the two side by side.
- **How it differs from the cut**, which has geysers too: ash rather than sand, pools to go round rather than slabs
  to go down, twice the geysers, and the magma spitter.
- **The magma spitter is the caldera's candidate signature strain** (ticket 19, open, not settled here; G0092).

**Why solid as rock rather than shots across it.** Fighting across a pool asks for a second solidity - one answer
for moving, another for shooting and seeing - threaded through `Solid`, the solver, `Sight`, the projectile bounds and
the flow field. That is a structural change for one terrain. Deferred, with its trigger, in `docs/backlog.md`.

**Why crags and not a new coat.** Pools are irregular masses that must never close a pass - which is what a crag
is, repairs included. A field on the coat every biome has (off as `rock`) costs one key; a "pools" coat would be a
second way of laying the same shape.

**Scope.** Lava as ground, the caldera's block. Not a lava that hurts on touch, not fire spreading from a pool.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A second solidity layer* - "blocks movement" apart from "blocks shots and sight" - for lava alone. Deferred
(`docs/backlog.md`), not rejected outright: it comes back when a second terrain wants it.

**No:** *Lava as a patch.* A patch is laid without repair and can close a pass; `PatchGround` refuses solid ground.

**No:** *Lava as a hazard you can stand in* - a slick of fire on the ground, or damage per tick. Fire already is the
ground that hurts (G0035); a second one is a second place for its rules to drift. Lava is where you cannot be.

**No:** *A pools coat of its own.* The crag coat with a terrain is the same shape and the same repairs.

**Revisit when** a second terrain wants to block movement and not shots (water, a chasm) - build the second solidity
then, for both; or a level wants lava that spreads or rises.

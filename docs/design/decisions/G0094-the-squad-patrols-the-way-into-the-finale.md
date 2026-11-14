# G0094: The squad patrols the way into the finale

**Status.** accepted

**Trigger.** What troopers do with nothing left to take; troopers walking ahead into a biome; changing where the
squad roams on a mission.

**Enforced by.** `TrooperAdvanceTest.patrolsTheWayIntoGroundHeldAfterwardsWhileItWaitsForACommander`,
`TrooperAdvanceTest.leavesGroundHeldAfterwardsAloneWhileNoCommanderIsInItsBiome`.

**Provisional, for review** - asked for by Mateusz after a playtest; not yet played.

**Constraint.** With every point taken and ground held afterwards (G0061) still waiting for a commander in its biome,
a trooper with nothing else to do patrols `sim.trooper.patchRadius` round the **way in**: the tile outside that biome,
beside it, that the held point's route reaches first. Worked out once, from the route the squad already builds to the
point. He never picks a spot inside that biome, and keeps a body plus his arrive radius out of it, so a man shoved at
the end of his walk is still outside. A level with no such ground, or no way in to it, roams as before.

**Why not inside the finale.** A man there would wake its nests (G0062) and start the last fight before the commander
arrives - the thing G0061 exists to stop.

**Why the way in, not the last point taken.** The last point taken may be anywhere in the biome before; the pass is
where the commander will come through and where the finale's waves come out, so the squad is where he will want it.

**Scope.** Ground held afterwards and the squad's roam. A fight, an order and an errand still outrank it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Roaming the whole map once the points are taken.* The behaviour this replaces.

**No:** *A separate patrol marker placed by the mission.* The way in is already a pure function of the map and the
point's route.

**Revisit when** a mission has a finale with more than one way in that matters, or the squad wants to follow the
commander instead.

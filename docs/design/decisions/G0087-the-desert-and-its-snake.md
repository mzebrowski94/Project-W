# G0087: The desert, and the snake that slithers through it

**Status.** accepted

**Trigger.** Adding a biome with stones or mud; a biome dealt wider than the rest; a creature that passes through bodies and still bites; a creature
in parts that lose their health one at a time; a creature that never stops or never hunts; a shot at a snake's tail.

**Enforced by.** `SnakeTest`; `MissionSweepTest` (the desert's roster and stones fit their room on every tier);
`MissionTest` (it is dealt); `SystemOrderTest` (`SlitherSystem` after the course setters, before `DriveSystem`).

**Provisional, for review**: settled in an AFK run, 2026-11-15, with Mateusz away. **Asked for** by him the same day: a desert with few obstacles - small brown pentagonal stones from a trooper's size
to five times it - and muddy patches that slow as grass does; sludgers and runners; a desert snake of a grunt-sized
head and three smaller parts that moves on through the biome, through units, wounding a man its head passes through,
turning in wide arcs unless space is short, its health in four parts lost from the tail, the head last.

**Constraint.** **Amends G0059** (shock scales by the body's area, from the smallest body to the largest).


- **The desert** is a biome as data (G0008, G0068): sand, mud in blobs, no crags or outcrops or boulders - its rock is
  its **stones**, a furniture line (`stonesMin/Max`, zero in every other biome) each a size drawn from its own stream,
  most of them small (the square of an even draw between `sim.obstacle.stoneMin` and `stoneMax`). A stone stands as a
  boulder does and is drawn as a pentagon turned by where it stands.
- **Mud** is a terrain of its own (`Terrain.MUD`, appended so nothing else renumbers) carrying a thicket's two factors:
  the same decision as undergrowth, drawn as wet ground rather than a hatch.
- **The snake slithers** (`SlitherDef`, on every strain, off at zero parts): it decides its whole course, writing
  its steering last of what steers and committed by `DriveSystem` - and nothing slows it or holds it still. In the open
  it bends one way, the other or straight on, for a time drawn from `RngStream.SLITHER`, at a small turn; where its
  way is shut - rock or a shut door within `lookAhead`, the edge of its biome, or scenery it ran up against - it turns
  hard a quarter round towards whichever side is open. Past `roam` from home it bends back. **It latches nothing as the
  swarm does, but from time to time it picks a man** (asked for 2026-11-15): every `huntMinSeconds`-`huntMaxSeconds`,
  drawn, the nearest man on his feet in its own biome within `huntRange`, and it turns onto him at `huntTurnDegrees` to
  go through him once - until its head has bitten him, he is down or gone, or `huntSeconds` run out. A way that is
  shut still turns it first. **Nothing slows it** (`unslowed`, read by `Pace`): no beam, glue or mud.
- **It passes through every body but scenery** and still touches what it passes through (`Collider.touchesThrough`
  beside G0074's `throughBodies`): its head bites with the harm the slither declares (`WOUND`), **once a pass** - a man
  still inside its head from the last bite is not bitten again however short his grace (bite slots on `Slither`,
  freed once he is clear). A shot along its way hits it.
- **Its health is shared between its parts**, rounded up, so one part's worth lost takes the last part off and the
  head goes last. **Only the head is a body**: the parts behind it are drawn, never hit - the cheap version, for review -
  and drawn so: see-through (`graphic.slither.*`, 40%) with a denser core, the head whole.
- **The desert is crowded** (asked for 2026-11-15): 16-20 runners and 2-3 of their holes, 3-4 snakes, 2-3 sludgers.
  To fit its room on hard (G0070) it is the one biome dealt wider, 62-70 columns against 50-58.
- **The pulse rifle does 12 a bolt** (was 10; asked for a little more damage), and **its stun is by the size of the
  body** (asked for 2026-11-15; **amends G0059**, which scaled it by the radius from the grunt): by area, from
  `stunAfterHits` (2) at `sim.shock.smallestRadius` to `growth` (5) times that at `largestRadius` - 2 bolts at a leech,
  about 3 at a grunt, 10 at the giants. "The pulse rifle stuns before it kills" now holds for whatever it does not kill
  in three bolts anyway: the easy tier's runner, 32 as it arrives, dies on the third, and no stun of two bolts or more
  comes sooner.
- **Nothing holds a snake still** (asked for 2026-11-15): `Stunned.immune`, set with a slither - no stun, recoil or
  clinch lands on it, so it never stops in place; it takes no shock.

**Rejected alternatives.**

- *A part per entity, each a body that passes its damage to the head* - every part shootable, but a second entity
  kind tied by handles and damage forwarded across them; left until a playtest says shooting the tail matters.
- *A snake that hunts as the swarm does* - latching and chasing a man until he is far off; it goes through one man once
  and goes back to its own way, so it is still a thing that moves on through the biome.
- *Stones as rock tiles* - the grid lays a level out and nothing else (G0009), and a pentagon five men wide is not a
  tile.
- *Mire for the mud* - mire costs nothing to cross, and changing it would change the fen.

**Do not build.** a snake a stun, recoil or clinch holds; a snake that chases a man past one go through him, or stops; a snake something slows; a snake pushed by or pushing bodies; a bite every tick it is inside a
man; stones in any biome but the desert without a decision; mud that differs from a thicket's numbers without a
decision.

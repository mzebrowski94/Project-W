# G0072: A tree's foliage hides what is beneath it

**Status.** accepted

**Trigger.** Adding anything drawn over units - cover, roofs, smoke; making a tree see-through; letting the simulation
see foliage; putting trees in another biome.

**Enforced by.** `MissionTest.putsBetweenItsFewestAndMostTreesInEveryBiomeEachTrunkInItsRange`,
`.standsTreesBesideEveryNestOfABiomeThatAsksForThem`, `.spawnsEveryTreeAsAnObstacleTheSizeOfItsTrunk`,
`MissionSweepTest.holdsEveryMissionToTheRules` (`MissionFaults.trees`),
`GraphicConfigTest.hidesMoreThanTheTrunkUnderFoliageThatHalfHidesFurtherOut`. That the foliage is drawn over
everything is caught by reading `WorldRenderer.drawTrees`.

**Settled with Mateusz, 2026-10-06**, adding trees to the fen.

**Constraint.** Pillar 4 is amended. It read:

> **One fight, and you can always see it.** No fog of war, no off-screen management, no second front. ...

and now carries one exception: **a tree's foliage hides what is beneath it.** Trees are ambush cover.

- **A tree is an obstacle**: its trunk stands as a boulder does - immovable, stops walkers, stops shots - laid
  `rockMargin` off rock and everything else (G0042). The trunk's radius is drawn per tree,
  `sim.obstacle.trunkMin`-`trunkMax` (1-2.5).
- **Its foliage is two discs scaled from the trunk**: an inner one, opaque, at `graphic.tree.innerScale` (2.2), and
  an outer one at `outerScale` (3.5), `outerAlpha` (0.5) see-through.
- **The inner disc hides everything beneath it**, whoever is looking: every unit of either side, the commander
  included, shots, the stun disc, rings. The view pulled back (Tab) hides it too. The trunk is drawn over the
  foliage, so what stops a man and his shots is always seen (pillar 5).
- **The foliage is a look, never state.** Nothing in the simulation sees it: perception, a clear shot and aim are
  exactly as they are without a tree, so an alien under a tree still sees you and a trooper still shoots what he
  noticed. Online it costs nothing (pillar 7) - the trunk's radius is on its body; the foliage is the client's.
- **Where**: 5-8 a fen, `furniture.treesMin/Max`; the first `treesByNest` (1-2) within `treeReach` (20, four tiles) of
  each nest, the rest by the usual rules. Zero is none, which is every other biome.

**Scope.** Trees' foliage only. Fog of war, an off-screen front and anything else pillar 4 forbids stay forbidden; a
tree hides what is under it, not what is behind it, and never what is out of shot.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Foliage that turns see-through over the player's own men, or when a commander walks under it.* Mateusz's call:
it hides all units. An ambush you can see into from inside is not cover for anyone.

**No:** *Showing what is beneath when the view is pulled back.* The pull-back is a look at the level, not a scan; a
hidden thing stays hidden.

**No:** *Foliage in the simulation - blocking sight, aim or detection.* A look only. Cover the aliens use against
perception is a different mechanic (G0011, G0015) with its own record.

**No:** *Foliage that stops a shot.* Only the trunk does; the foliage is drawn, not a body.

**Revisit when** play says a fight under trees cannot be read - the answer is fewer or smaller trees, never a marker
over what is hidden.

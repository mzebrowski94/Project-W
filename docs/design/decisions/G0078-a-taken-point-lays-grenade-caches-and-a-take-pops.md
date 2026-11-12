# G0078: A taken point lays grenade caches, and a take by touch pops

**Status.** amended by G0086: a taken point may lay a medkit

**Trigger.** Anything a taken control point lays or hands out; a pickup a point leaves, how many, for whom; showing a
take; proposing a new kind for a refill at a point, one cache per point, or a crate that refills by touch.

**Enforced by.** `ControlPointTest.laysAGrenadeCacheBesideItForEverySeatedSlotWhenItIsTaken`,
`.countsASlotWhoseCommanderIsOnHisWayBack`, `.laysThemOnceAndNeverAgainWhileItIsHeld`; `PopTest` -
`.leavesAPopWearingThePickupsLookWhereAManTookIt`, `.isGoneOnceItsMomentIsOver`, `.publishesHowMuchOfItIsLeft`,
`.leavesNoneWhereAnAlienAteSomething`; `ProvingGroundTest.standsThreeOfTheOthersBetweenHimAndTheMiddle`;
`GraphicConfigTest.readsAPopThatRisesAndIsSeen`.

**Settled with Mateusz, 2026-11-11**, from the playtest list (ticket 05): *small crates should spawn after taking a
control point (ones that refund grenades and resources) + a simple animation of gaining the resources* - and *in the
proving ground, add crates to "others"*, widened in the session to grenade caches as well.

**Amends G0060**, whose list of the proving ground's others gains crates and grenade caches.

**Constraint.**

- **The grenade cache is the thing laid.** No new kind: the `ARMOURY_GRENADES` pickup that is a card in the armoury
  deck also lies on the field, as a weapons cache does. Its grant, its full-check and its look are unchanged (G0077):
  taken by touch it fills a commander to his most; a full man and every trooper walk over it; the aliens are offered
  nothing.
- **A point lays them the tick it is taken**, once: `sim.controlPoint.grenadeCaches` (1) for every **seated** slot -
  standing, down or on his way back, away or not, counted at that tick - just outside the point, in the ring its
  reinforcements lie in, through `Scatter` on a stream of its own (`RngStream.CACHE`). Every point, a finale's that
  holds afterwards too. They lie until taken. Furniture: never scaled by tier or ramp.
- **A take by touch on the player's side pops**: the taken pickup's look, rising off the taker and fading, for
  `sim.pop.seconds` (0.5) - a spawned entity (G0004), counted down with every other clock, published on
  `Appearance.pop`, drawn by `graphic.pop.*`. Commander or trooper, any pickup - a grenade cache, a medkit, a gun, a
  supply drop. Never for the aliens' meals, whose tell is the strain they grow into; never for a card taken from a
  crate, which has the crate bar.
- **The proving ground's others** add `3 CRATES` and `3 GRENADE CACHES`, in the band the others stand in (amends
  G0060's list).

**Why per seated slot.** One per point makes co-op a race for the only refill, and a slot whose commander is down or
coming back is still a player who will want his (pillar 7: keyed by slot, sized from capacity).

**Why not at a capture by itself.** G0030 refused a reward granted by capturing because it would be invisible. This
one is a thing lying on the ground, which the player walks to - and the capture already has its tell, the draft.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A new pickup kind for the refill at a point* (a "mini crate", a resupply). The grenade cache already is one;
a second would be a second way to do what has one (CLAUDE.md, *Reuse before you create*).

**No:** *One cache per point.* In co-op it is a race between players on the same side.

**No:** *A crate that refills by touch*, at a point or anywhere. G0077's refusal, unchanged: a crate offers nothing to
whoever walks onto it (G0056).

**No:** *A pop for the aliens' meals, or a pop when the caches appear.* The first is noise on the other side's tell;
the second shows nothing the shine does not.

**Revisit when** something other than grenades is counted and wants refilling - the cache is the place a second
refill would be asked to join.

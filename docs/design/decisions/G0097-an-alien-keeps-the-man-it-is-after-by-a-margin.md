# G0097: An alien keeps the man it is after until another is nearer by a margin

**Status.** accepted

**Trigger.** Anything that changes who an alien hunts once it is hunting; a creature seen turning back and forth
between two men, or one that will not come off a man; touching `Drivers.nearestLivingTarget` or `Perception.kept`.

**Enforced by.** `AlienTargetingTest.keepsTheManItIsAfterWhenAnotherComesOnlyALittleNearer`,
`.leavesTheManItIsAfterForAnotherNearerByTheSwitchMargin`, `.goesForWhoeverIsNearestAtOnceWithTheSwitchMarginOff`,
`.leavesTheManItKeptTheMomentHeGoesDownHoweverFarOffTheNextIs`, `.isNotHeldToAManItGaveUpOn`,
`.refusesASwitchMarginThatWouldBeALatch`.

**Asked for** by Mateusz, 2026-11-16: the case G0026 said to come back for - a creature between two men, pulled about.
Not yet played.

**Amends G0026** - its *nearest is asked again every tick, not latched*. It is still asked every tick, and still by
`Drivers.nearestLivingTarget` alone; the answer now leans towards the man it gave last time.

**Constraint.** A hunting alien keeps the man it is after until **another is nearer by the switch margin** -
`sim.ai.switchMargin` (0.25), a fraction of the distance to the man it is after: with him 20 off, another must come
inside 15 - **or he goes down**, which releases it at once as it always did (G0023, G0026).

- **One place decides.** `Drivers.nearestLivingTarget` is handed the kept man's handle (`Perception.kept`) and the
  margin, and returns him unless somebody on the list beats him by it. `PerceptionSystem` is still its only caller and
  the only reader of the handle; everything downstream reads the place it publishes, as before.
- **Kept only while it hunts.** A creature that has noticed nobody, or gave a man up past its lose range, keeps nobody:
  the next man it notices is the nearest, with no margin. The handle is handed over only while it is `aggro`.
- **Losing interest is asked of the man it is after.** A strain that loses interest gives up when *he* is past its lose
  range, though another stands inside it not nearer by the margin - him it must notice afresh, at its detection range.
  Most strains are always aggressive and never get there.
- **Zero is off**: the nearest, asked afresh every tick, exactly G0026 as first built. Below one always - at one nobody
  could ever be nearer by it, which is the latch G0026 rejected; the config refuses it.
- **A margin, never a latch.** The handle is an input to a question asked every tick, not an answer held: a handle
  that names nobody on the list - down, gone, a gun unmanned - leans the answer towards nobody. Handles are never
  reused (ADR 0016) and `reset()` clears it, so a pooled `Perception` cannot come back aimed at whoever replaced him -
  the failure G0026's rejected latch was rejected for.
- **Round rock the flow field still leads to the nearest by route.** There is one field per strain, flooded from every
  man at once (G0026), and it knows nothing of who is kept: a creature that cannot see the man it is after walks the
  route to whoever is nearest to walk to. The margin decides whom it faces, leaps at, slings at and shoots, and whom it
  walks at straight once it has a clear path to him. Accepted, and not yet played: a creature may walk a route to one
  man while it faces another.

**Scope.** Aliens only - whatever carries a `Perception`. Not what a trooper shoots at (his **threat**, re-chosen every
tick), not the man a snake picks (G0095), not which men wake a nest, and not food.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A flow field for the kept man.* G0026's rejected field per man, by another door: one grid pass per goal.

**No:** *A margin of one, or a kept man who stays kept once he is down.* Either is the latch G0026 rejected.

**Revisit when** a pack is seen walking round rock to one man while facing and leaping at another for long enough to
read as wrong - then the kept man's tile could weigh the flood, not a second field - or when playing shows 0.25 makes
a pack too hard to peel off a man in trouble.

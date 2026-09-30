# G0026: An alien comes for whoever is nearest, and the commander is on that list

**Status.** accepted

**Trigger.** Anything that changes who an alien fights, or that assumes the answer is the commander.

**Enforced by.** `AlienTargetingTest.comesForWhoeverIsNearestRatherThanForTheCommander`,
`.leavesAManWhoIsDownForWhoeverIsStillOnHisFeet`, `.takesUpWithWhoeverIsLeftWhenTheOneItWasAfterGoesDown`,
`.comesForATurretAsReadilyAsForAMan`, `.shootsTheNearestManRatherThanOnlyTheCommander`.

**Constraint.** An alien hunts the nearest thing on the player's side still on its feet - the commander,
a trooper, or a gun a level put down. `Drivers.nearestLivingTarget` is the only place that decides,
`PerceptionSystem` is the only system that calls it, and everything downstream reads the *place* it
published. Being off your feet takes you off the list, whatever put you there.

- **The target's going down releases what hunts it.** A commander's death still drops every standing
  order and stops what was coming for him, but it no longer empties the field: the men beside him are
  on the list too.
- **Nearest is asked again every tick,** not latched. A latched target is a reference by another name,
  re-resolved the moment it dies anyway (G0010, G0016). A creature between two men can be pulled about;
  the answer, if that reads badly, is hysteresis on the distance rather than a latch.
- **Nearest is by route.** There is one flow field per strain, flooded from every man at once: a
  multi-source flood settles each tile once, as a single-source one does, so it costs the same grid pass
  and leads every unit round rock to whoever is nearest to walk to.

**Scope.** The alien side only. Not what a trooper shoots at - its own arc and range - and not food: the
thing in front of you outranks a meal absolutely, and a grudge outranks nothing at all.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A threat table, or aggro earned by damage dealt.* It needs a per-unit list, decay, and a rule for
every source of damage. Being hurt already latches a **place** (G0016), the arcade-sized version.

**No:** *Latching a target by id or reference until it dies.* A pooled component holding either comes back
aimed at whatever replaced it, which reads as the AI hallucinating.

**No:** *A flow field per man being hunted.* One grid pass per goal per tick is the per-unit cost a flow
field exists to avoid; one field per strain, flooded from every man, is the answer.

**No:** *Letting a pack finish a man who is down.* It would delete the rescue: a squad cannot cross open
ground to somebody a pack is standing on. A casualty is off the list; only something that walks into
him finishes him, which is a different rule.

**Revisit when** a creature is seen visibly dithering between two equally near targets - the one behaviour
this trades for its simplicity. The fix is a margin, not a latch.

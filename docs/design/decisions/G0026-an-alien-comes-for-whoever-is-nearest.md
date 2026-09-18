# G0026: An alien comes for whoever is nearest, and the commander is on that list

**Trigger.** Anything that changes who an alien fights, or that assumes the answer is the commander.

**Enforced by.** `AlienTargetingTest.comesForWhoeverIsNearestRatherThanForTheCommander`,
`.leavesAManWhoIsDownForWhoeverIsStillOnHisFeet`, `.takesUpWithWhoeverIsLeftWhenTheOneItWasAfterGoesDown`,
`.comesForATurretAsReadilyAsForAMan`, `.shootsTheNearestManRatherThanOnlyTheCommander`.

**Constraint.** An alien hunts the nearest thing on the player's side still on its feet - the
commander, a trooper, or a gun a level put down. `Drivers.nearestLivingTarget` is the only place that
decides, `PerceptionSystem` is the only system that calls it, and everything downstream reads the
*place* it published. Being off your feet takes you off the list, whatever put you there.

**Scope.** The alien side only. It says nothing about what a trooper shoots at, which is its own arc
and its own range, and nothing about food - the thing in front of you still outranks a meal
absolutely, and a grudge still outranks nothing at all.

**What it replaced.** Detection latched onto the commander and only the commander, which left a
trooper standing alone in the open perfectly safe and made a squad operating away from him a squad
nothing could threaten. The open question that asked whether an alien should ever aim at a trooper is
answered here, on 2026-09-18.

**The rule it changed.** *His going down releases everything hunting him* is now *the target's* going
down. His death still drops every standing order and still stops anything that was coming for **him** -
but it no longer empties the field, because the men beside him are on the list too.

**Nearest is asked again every tick,** not latched. A latched target is a reference by another name,
and it has to be re-resolved anyway the moment it dies (G0010, G0016). The cost is that a creature
between two men can be pulled about between them; the answer to that, if it ever reads badly, is
hysteresis on the distance rather than a latch.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A threat table, or aggro earned by damage dealt.* The standard answer from another genre, and it
needs a per-unit list, decay, and a rule for every source of damage in the game. Being hurt already
latches a **place** (G0016), which is the arcade-sized version of the same idea.

**No:** *Latching a target by id or reference until it dies.* A pooled component holding either comes
back aimed at whatever replaced it, which reads as the AI hallucinating. This is the third time that
argument has been made in this codebase, and the third time it has won.

**No:** *A flow field per man being hunted.* Routes are one grid pass per goal per tick, which is the
per-unit cost a flow field exists to avoid.

**Amended 2026-09-18.** The half of that which said *the field stays pointed at the commander, and
everything hunting somebody else steers straight* was wrong in practice, and co-op plus a level with
rock in it made it obvious: an alien hunting a trooper across a wall walked into the wall and stayed
there. The answer is not a field per man - that rejection stands - but **one field per strain flooded
from every man at once**. A multi-source flood settles each tile once, exactly as a single-source one
does, so it costs the same grid pass and leads every unit to whoever is nearest *by route*. Which is
what this record said happens all along; it was only ever true by line.

**No:** *Letting a pack finish a man who is down.* It would be consistent, and it would delete the
rescue: a squad cannot cross open ground to somebody a pack is standing on. A casualty is off the
list; only something that walks into him finishes him, which is a different rule and already exists.

**Revisit when** a creature is seen visibly dithering between two equally near targets. That is the
one behaviour this trades for its simplicity, and the fix is a margin, not a latch.

# G0073: The sludger - armoured behind, a trail of glue to its target, and glue that only slows

**Status.** accepted

**Trigger.** Adding a strain armoured anywhere but its head; a pattern that keeps its bearing or stops its shots short; ruined ground that
does something other than wound; making stepping in something a blow; putting the sludger in another biome.

**Enforced by.** `SludgerTest.turnsAShotOffItsBack`, `.takesAShotInTheFace`, `.takesAShotFromTheSide`,
`BarrageTest.keepsALockedLineOnTheBearingItWoundUpOn`, `.followsWhoeverItHuntsSalvoBySalvoWhenItIsNotLocked`,
`.laysAStepFurtherEachSalvoTheLastReachingItsAim`,
`SlickTest.slowsAManStandingInGlueAndCostsHimNothingElse`, `.leavesTheShieldOfAManStandingInGlueUp`,
`SimConfigTest.spitsALockedLineThatWoundsOnTheHitAndLeavesGlueThatOnlySlows`. That the dark segment is the armoured
one is caught by reading `WorldRenderer.drawSegments`.

**Settled with Mateusz, 2026-10-06**, adding the sludger to the fen, the brake and the anthill.

**Constraint.**

- **Armour is an arc round one end, turned from the facing** - `armour.arcDegrees` wide, centred `centreDegrees`
  from the way it faces: zero is the ant's head (G0045), 180 the sludger's back. The same rule otherwise: gunfire
  only, judged by the side the shot arrives from; a fist, a blast and fire land anywhere. The armoured segment is
  drawn dark (pillar 5).
- **The sludger is the ant turned round.** Soft in the face - to hurt it with a gun you stand in its line or square
  on from the side. No leap, no grip. Twice a spitter's health.
- **It spits with a barrage of one pattern** (G0048, G0071): the winding-up tint, then seven single globs down one
  line, standing for both; started only within `reach`, broken off by a declared stun. No roar.
- **A locked pattern keeps the bearing its wind-up ended on** for every salvo (`PatternDef.locked`), so the tint
  tells the line and a sidestep clears it. Every pattern of the boss's and the giants' still follows.
- **A stepped pattern lays its salvos along the way to its aim** (`PatternDef.stepped`): of N, the k-th stops k/N of
  the way to where its target stood when the tint ended, the last reaching him. So the glue is **a trail from the
  sludger's mouth to him** - seven circles of radius 2 overlap down the whole reach. Every other pattern's shots
  fly their whole reach.
- **A glob hurts on the hit and only then** - it wounds a man (G0053), whichever glob of the line reaches him - and
  leaves one circle of **glue** where it stops, under the man it hit too.
- **Glue is a slick with a different declaration** (as fire is, G0035): no damage, no harm, no shove, and a slow
  (0.4 of his pace while he stands in it, 0.3 s after, refreshed, never added). It takes the sludger's side, lasts
  6 s and fades over its last third. Combat's "a slick wounds a man" now reads as venom's and fire's declaration.
- **A touch that only slows is no blow.** It breaks no shield, turns no trooper towards it and provokes nothing; it
  records a slow and nothing else (`ContactDamage.onlySlows`, `Damage.slowBy`). Glue is ground, not an attack.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Glue landing as a harmless blow, as venom's shot does* (G0057). Touching it every tick would shatter a
shield on stepping in and spin a trooper round to look at a puddle.

**No:** *Every glob flying its whole reach.* Tried first: a missed line piled into one puddle at the far end.
Mateusz's call, amended the same day: the glue is a trail from its mouth to its target.

**No:** *One glob laying glue along its flight.* A second way to leave ground behind; the stepped line uses the
death reaction every slick already comes through (G0018).

**No:** *A line that follows him salvo by salvo.* The boss's stream does; the sludger's is read from the tint once.

**No:** *A sludger that leaps or grips.* It is the ant's opposite problem, not a second ant.

**Revisit when** play says the line cannot be read in a crowd, or glue makes a fight a slog - the answers are a
longer tint or a weaker slow, never a marker.

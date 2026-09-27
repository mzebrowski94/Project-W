# G0046: Ground is given room by whoever it can catch

**Trigger.** Changing what a unit steers around, or proposing that a unit stand still on ground that is
hurting it. Narrows G0035's avoidance declaration, which named this as its revisit trigger.

**Enforced by.** `SlickTest.isGivenRoomByAManItCouldCatch`, `.isWalkedThroughByTheSideThatSpatIt`,
`FireTest.stepsAManStandingStillOffGroundThatIsCatchingFire`, `.leansOffBurningGroundAsReadilyAsOffARock`.

**Constraint.** Whether a unit goes round something is a question about the pair for **ground** (a
collider that is not solid), and stays a declaration for **bodies**:

- A solid thing is gone round unless it is on the other side - that is what is being hunted.
- Ground is gone round unless it is **the steerer's own side's** ground. Venom is avoided by the
  player's men and waded through by the frogs that spat it; fire, on nobody's side, is avoided by
  everybody. Every slick is spawned `avoided`; the pair decides.
- **A unit asking to go nowhere steps off ground inside its scan range**, straight away from it at its
  own pace. Bodies never move it. A man holding a point, securing one or standing to shoot no longer
  watches fire swell up round his boots.

**Why.** Playtest 2026-09-27: troopers walked straight into frog venom. They could not see it twice
over - a slick with a side was declared unavoided, and avoidance skipped everything on the other side
anyway. G0035 predicted the fix: the avoidance flag becoming a pair question the way `shunned` is.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Avoidance asking whether something is a slick.* Still refused (G0034, G0035). It asks whether
the thing is solid and whose side it is on - two facts every collider already has.

**No:** *Routing round ruined ground in the flow field.* A slick lasts seconds and a route is a grid
pass; avoidance already leans a unit off it well before contact.

**Revisit when** ground wants to be avoided by a side it cannot hurt (a decoy, a warning), which is a
third declaration rather than a third rule.

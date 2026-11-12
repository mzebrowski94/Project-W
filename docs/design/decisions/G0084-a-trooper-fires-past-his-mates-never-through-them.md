# G0084: A trooper fires past his mates, never through them

**Status.** accepted

**Trigger.** Changing what a trooper does when a mate stands between him and what he is shooting; friendly fire;
a miss chance on a trooper's shot; moving a man aside to get a line; making the commander's own shots wait for his
men; letting a squad fire as one block again.

**Enforced by.** `TrooperFireDisciplineTest` - `.holdsHisFireWithAMateStandingBetweenHimAndHisOnlyTarget`,
`.firesPastAMateStandingJustOffTheLineByAimingAcrossTheTarget`, `.takesAnotherTargetHeHasAClearLineToWhenTheNearerOneIsFouled`,
`.firesOverAMateWhoIsDown`, `.firesAtATargetWithAMateStandingBeyondIt`, `.leavesTheCommandersOwnShotsAlone`;
`TrooperCrewTest.firesFromAGunWithAMateStandingInFrontOfIt`.

**Settled with Mateusz, 2026-11-12**, in the balance round (ticket 18 of the playtest-feedback map): *a medium squad
easily kills everything before it gets close, because of mass shooting* - and *troopers should be able to fire
gently, or along the edges, past their peers.*

**Constraint.**

- **A fouled line** (glossary): a trooper's line to his target that passes closer to a mate on his feet - trooper or
  commander - than the mate's edge plus the shot's. A path clear of his edge is open. A man down, a mate beyond the
  target and a thrown grenade foul nothing; every gun a trooper fires is held to it, the bazooka too.
- **A fouled man, in order**: aims across his target's own width, middle out to `sim.trooper.aimAcross` of its radius,
  and fires on the first line clear of every mate; failing that takes another target he can see down a clear line;
  failing that stays on the best fouled one and **holds his fire** that tick. He never moves for a line - the
  approach offset is what spreads a squad.
- **Fire discipline, not physics**: a shot that does reach a mate still passes through him. No friendly fire.
- **The commander's own shots are never fouled**, and a man on a **turret** is not either - a gun on a mount fires
  where it points (combat). No new look: a man holding his fire shows it by firing nothing (pillar 5).
- What it buys: a clump fires with its front, a squad spread out fires with all of it - the tactic is the spread
  (pillar 1), and a fight closes in without slowing down.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Friendly fire.* A man killed by his own squad's rounds is a death the player did not choose and cannot
read; the cost of a clump is shots not fired, not men lost.

**No:** *Firing anyway with a chance to miss the mate.* Dice the player cannot see (pillar 5), deciding a fight he
can see.

**No:** *Stepping aside for a line.* A second way to move a trooper, fighting the one that already spreads them.

# G0023: The commander goes down like his men, because now somebody comes for him

**Status.** amended by G0053: a revive clears every wound

**Trigger.** Changing what happens when the commander goes down, changing who can revive, or proposing
that the squad do anything with a body on the ground.

**Enforced by.** `CasualtyTest.leavesTheCommanderShotDownOnTheGroundRatherThanDead`,
`.killsTheCommanderOutrightWhenTheBlowIsOneThatKills`, `AidTest.picksTheCommanderUpToo`,
`.putsAFallenComradeBackOnHisFeet`, `.leavesHimWhereHeLiesWhenNobodyIsWilling`,
`.neverBreaksOffAFightToFetchSomebody`, `.holdsWhereItWasToldToRatherThanGoingToFetchSomebody`.

**Constraint.** **Amends G0020**: it reverses its rejection of a casualty commander. Whatever puts a
trooper on the ground puts the commander there, with a bleed-out clock; whatever kills a trooper
outright kills him.

- **A trooper with nothing better to do goes and picks somebody up.** Whether it goes is a roll -
  `AiChance.AID_A_TROOPER`, or the much higher `AID_THE_COMMANDER` - taken once, on the way in. The
  ranking is **fight, order, casualty, objective**: it never turns its back on something shooting at it,
  never breaks a standing order to do it (G0017), and always prefers a body to a square.
- **The work is one piece of code for everybody.** The commander on his key and a trooper on its own
  judgement both go through `Aid`, so a man stands up with the same thing whoever reached him.
- **Him going down releases everything hunting him**, and "down" means bleeding as well as dead
  (`Drivers.livingTarget`). Otherwise whatever put him down would finish him where he lay and the
  rescue would be shown and never reachable.

**Scope.** Who can be a casualty and who comes for one. Not hauling: only the commander drags, and nobody
drags him.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A trooper that always comes.* It turns a rescue into a queue: the player stops deciding whether
to cross the ground, because somebody else always will.

**No:** *Re-rolling every tick of the approach.* Men setting off for a body and thinking better of it
halfway read as a bug. The roll is a decision, taken once.

**No:** *Aid outranking a standing order.* An order a body on the ground could override is not an order
(G0017).

**No:** *Aid outranking a fight.* A squad that turned its back on something shooting at it would lose
both.

**No:** *A separate revive with its own numbers for the squad.* Two copies of "how long it takes, what he
gets back, how long he is protected" drift apart.

**No:** *Letting a trooper haul a body.* Dragging costs a hand and a mode, and a trooper that could do it
would need rules about where it takes him.

**No:** *A commander who bleeds out into a respawn faster or slower than the ordinary one.* The bleed-out
already is the second chance.

**Revisit when** co-op play shows whether a trooper should still go for a commander another player is
closer to (an open question in `docs/design/README.md`), or a man should be hauled by somebody who is
not the commander.

## Amended by G0053

**What he gets back is every wound cleared**, rather than a fraction of his health. Still one revive
path, `Aid`, with one set of numbers, and the same protection afterwards.

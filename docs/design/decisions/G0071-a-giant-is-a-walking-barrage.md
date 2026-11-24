# G0071: A giant is a brute with a walking barrage, and its slam is a ring of shots

**Status.** accepted

**Trigger.** Adding a strain that roars, stands for its attack, or hits everything round it; giving a walking strain
a barrage; proposing that a slam, a stomp or a shockwave be a blast; making a stun interrupt the boss.

**Enforced by.** `BarrageTest.startsAPatternOnlyOnceWhatItHuntsIsWithinReach`,
`.standsStillWhileItWindsUpAndFiresAndWalksWhileItRests`, `.shakesTheGroundWhenItStartsWindingUp`,
`.breaksOffThePatternWhenADeclaredStunLandsAndWindsItUpAgainAfter`, `.keepsWindingUpThroughAStagger`,
`.showsTheCircleItsShotsReachForAMomentAfterASalvoThatSaysSo`, `.finishesAPatternItHasStartedWhenWhatItHuntsGetsAway`, `GiantTest`,
`SimConfigTest.makesEachGiantABruteGrownHalfAsBigAgainAndAtLeastFourTimesAsHard`,
`.slamsACircleWhoseRadiusIsTwiceTheThumpersWidthAndStartsOnlyWithSomebodyInIt`, `.searsOnlyWhereItsConeLands`.

**Grilled with Mateusz, 2026-10-06**, porting the cut and the fen.

**Constraint.**

- **Two giants, the searer and the thumper**: a brute's body half as big again, at least four times its health, slower than
  anything else that walks, no leap, and quicker to give up than a brute (never sooner than a frog). Constants and
  blocks (G0008); they turn up loose in the cut and the fen, 0-1 of each.
- **Each attacks with a barrage of one pattern** - the boss's machinery (G0048), on the definition every strain
  has, with four fields every other strain leaves off: `reach` (it starts a pattern only once what it hunts is that
  near), `holdsStill` (it stands while winding up and firing, and walks through the rest), `roar` (a shake as the
  wind-up starts, felt by every commander in its radius) and `stunCancels`.
- **The wind-up is the roar.** The winding-up tint and the shake tell it; standing still is the opening (pillar 1).
- **A roar is always answered** (amended after play, 2026-10-06): a pattern wound up or firing is finished at the
  place it last aimed, whether or not what it hunted has walked out of its reach or out of its mind. Walking away
  during the roar is not an answer; being out of the way is.
- **A declared stun breaks a giant's pattern off** - shock, a gun's `stunSeconds` - and it roars again from the
  start once the stun is over. A stagger or a lock does not: up close is claw range. The boss keeps G0048's rule -
  nothing interrupts its barrage - by leaving `stunCancels` off.
- **The searer's sear** is the laser's kind of short beam, in a colour none of the player's guns has: one beam every
  0.05 s for a second and a half, each thrown at random inside a 75-degree cone by the gun's own scatter - the
  pattern stays counted, as G0048 asks; where a beam lands is the gun's, as a shotgun's pellets are (amended after
  play: it was nine-beam volleys). Each wounds a man and slows him. No bloat.
- **The thumper's slam is a ring of shots, not a blast**: sixty-four short, fast shots all the way round, out to a
  radius of twice its width (amended after play: it was twice its width across), each wounding a man and stunning him for two seconds, drawn as one white circle for a
  moment after (`PatternDef.burstSeconds`, `Appearance.burst`). Its shots take its side, so the swarm standing in
  it is untouched.
- **Every declared stun is drawn as a pale disc shrinking onto the body** (`Appearance.daze`), on top of the
  darkening - the pulse rifle's and the slam's alike.

**Why a ring of shots, not a blast.** G0018 refuses what a blast-shaped slam needs three times over: spawning one
directly rather than by dying, a blast that spares its own side, and one that stuns rather than being decisive.
A ring of shots is a gun and a pattern - no new mechanic - and catches what matters, the squad. Its price: the
brief's "all units" became the player's units; standing a giant in the swarm stuns nothing of the swarm.

**Why a declared stun, and not a fist.** The pulse rifle exists to hold something still for the squad (G0059); a
giant whose roar a stun did not touch would make the gun a tint. A fist that broke the roar would beat a giant by
one key, which is why the boss ignores both.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

- *The slam as a blast* - G0018, above. A quake that also catches the swarm is a new mechanic with its own word and
  record, not a blast with exceptions.
- *A stagger or a lock breaking a giant's roar* - one key beats it.
- *A stun interrupting the boss's barrage* - G0048; `stunCancels` stays off for it.
- *A giant that leaps* - its threat is the barrage, and a leap on 1000 health at 7.8 across is a wall arriving.

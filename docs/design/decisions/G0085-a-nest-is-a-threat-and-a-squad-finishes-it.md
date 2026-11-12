# G0085: A nest is a threat, and a squad finishes it

**Status.** accepted

**Trigger.** Changing how a trooper weighs a nest against what walks; letting a man drop a target he has started on;
giving any other target a weight or a hold of its own.

**Enforced by.** `TrooperCombatTest` - `.weighsANestAsAThreatAtItsOwnDistance`, `.shootsAGruntComingForSomebodyNearerThanANest`,
`.keepsShootingANestWhenSomethingNearerTurnsUpOutsideHisAllRoundRange`, `.goesBackToTheNestHeKeptOnceAMateStopsFoulingHisLine`,
`.turnsFromANestOnSomethingInsideHisAllRoundRange`,
`.picksANestOutFromFurtherOffThanAnythingElse`.

**Settled with Mateusz, 2026-11-12**, in the balance round (ticket 18 of the playtest-feedback map), kept over
G0080's Do-not-build: *troopers should still notice nests, and be more dedicated to destroying them*, once their sight
came down from 34 to 27.

**Constraint.** **Amends G0080**: its "a threat weight on nests" is withdrawn; "a branch on `Nest`" stands.

- **A nest is weighed as a threat** - at its own distance, as something coming for somebody is, rather than
  `threatBonus` further: `Enemy.threat`, from `sim.nest.threat`. Something coming for somebody nearer than the nest is
  still shot first.
- **A man keeps on a nest he picked out** until it dies, or something he could shoot instead is inside his all-round
  range: `Enemy.kept`, from `sim.nest.kept`, the man holding it by handle (ADR 0016). While it is out of his reach or
  its line is fouled (G0084) he fights what else he can, and goes back to it once he can take it again.
- **Both are fields every target carries**, false for everything that walks - never a question about what the
  target is (G0080, G0008's argument).
- **Still picked out at 54**: `sim.nest.standsOut` 27 past a trooper's sight of 27.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A nest always first.* The commander's grunt at arm's length outranks any hole (pillar 2: the squad covers
him); that is what the all-round range is for.

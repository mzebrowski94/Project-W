# G0080: A trooper notices all round up close, and a nest stands out

**Status.** accepted

**Trigger.** Making a trooper turn on something behind him other than by being hit; widening the **alert**;
making the squad prefer, or ignore, nests; giving a target kind a weight in a trooper's choice.

**Enforced by.** `TrooperCombatTest` - `.turnsOnSomethingThatComesRightUpBehindHim`,
`.stillWillNotSeeThroughRockWhatIsRightBehindHim`, `.takesNoNoticeOfSomethingBehindIt`,
`.picksANestOutFromFurtherOffThanAnythingElse`; `PerceptionSystemTest.noticesWhatIsRightBehindItWithinItsAllRoundRange`;
`PerceptionDefTest.refusesAnAllRoundRangePastWhereItNoticesAnything`.

**Settled on Mateusz's behalf, 2026-11-11**, from the playtest list (ticket 06): *troopers should turn round and
fight if an enemy comes at them from behind (at short range); troopers should attack nests more willingly (maybe
just raise their range).*

**Narrows G0015.** One more test a strain declares: an **all-round range**, `perception.allRoundRange` on the shared
`PerceptionDef`, inside which the arc does not count. Zero means none, and every alien declares zero. It is read in
one place, `Sight.withinNotice`, which both minds call - an alien's and a trooper's.

**Constraint.**

- **The arc keeps ground of its own**: an all-round range is never past the detection range (the definition refuses
  it), and the trooper's is a third of his sight. Past it only the arc counts, so his facing is still worth drawing
  (pillar 5).
- **All round is never through rock**: the clear line is asked after it, as after the arc.
- **A nest stands out by reach alone**: `standsOut` on `Enemy`, which every target carries, zero for everything that
  walks and `sim.nest.standsOut` for a nest. It stretches both the range he picks a target out from and the one he
  holds it to, so the hysteresis holds; it does not touch the **threat** score, so something coming for somebody
  is still shot first.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A wider alert, or an alert started by something close.* The alert is what being hit does (a place, G0016);
starting it from proximity would make it a second, longer all-round range with a timer, and a man would go on
looking round for seconds after the thing behind him was dead.

**No:** *A threat weight on nests.* A nest scored as nearer would pull fire off the grunt walking at the commander
onto a hole that is going nowhere. Reach alone gets the squad to the nest; what is coming for somebody still wins
once it is there.

**No:** *A branch on `Nest` in the trooper's choice.* A target's number is on the component every target has, so the
choice never asks what kind of thing it is (G0008's argument).

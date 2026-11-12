# G0081: A nest left behind breeds slower, and commanders count on the field

**Status.** accepted

**Trigger.** Changing how fast an awake nest breeds with nobody near it, or what the field's most is held
against.

**Enforced by.** `NestTest` (`.breedsFarSlowerOnceEveryManHasLeftIt`, `.keepsItsPaceWhileATrooperIsNearIt`,
`.comesBackToItsPaceTheTickAManIsNearAgain`, `.goesOnWorkingAfterWhoeverWokeItHasGone`,
`.keepsItsPaceAloneWhenThereIsNoLeftBehindRadius`), `ReinforcementSystemTest`
(`.countsACommanderOnTheFieldAgainstItsMost`, `.countsEveryCommanderOnTheFieldWhateverHisSlot`,
`.leavesTheRoomOfACommanderWaitingToComeBack`), `ReserveTest.bringsACommanderBackEvenOntoAFullField`,
`ReadoutWriterTest.countsACommanderOnTheFieldInTheArmy`.

**Constraint.** **Amends G0031, G0055 and G0030.** Provisional, for review: built from playtest feedback with
Mateusz away, without a grilling.

1. **A nest left behind.** An awake nest is left behind while no man - trooper or commander, down or not -
   stands within `sim.nest.leftBehindRadius` (140, the wake radius) of it, biome or none. Its countdown then
   moves once every `sim.nest.leftBehindSlowdown` (5) ticks. The tick a man is that near it is back at its
   pace; what it spent alone is neither made up nor held over. Its progress shows the slower clock (pillar
   5). A zero radius is off. The men are filed in a `SpatialGrid` once a tick and each nest asks it.
2. **Commanders count towards the field's most.** G0055's B is the most men the field holds, troopers and
   commanders together: every commander on the field takes one of its places, whatever his slot, down or
   not - counted from the commanders standing there, never assumed to be one player or four (pillar 7).
   `ReinforcementSystem.onTheField()` is the one definition, read by the crate, the muster and the HUD's
   `ARMY` count (`Simulation.menOnField`). A commander waiting to come back is a ticket, not a man, and takes
   no place. A commander coming back is never kept off a full field: a full field stops troopers, not a
   player, so it can stand one over its most until it loses a man.

**Why slower is not the sleep G0031 rules out.** A sleeping nest sends nothing; a nest left behind still
breeds, a fifth as often - a thirty-second period becomes two and a half minutes, about a creature a minute
per nest. Waiting is still not the counter-play: from inside the radius it works at full pace, and from
outside it it goes on costing, as a trickle that follows the march rather than the pile on the way back.
Waking still latches, and the radius is the wake radius, so what woke it is what keeps it working. Five is
the middle of the four to six asked for; four leaves more of a trickle, six nearer to switched off. Neither
key moves with the tier (G0063 unchanged).

**Why only a commander on the field.** One waiting to come back may wait on an empty reserve; holding a
place for him would starve the field of men who could fight. And a player kept out by his own troopers is
a player who cannot play.

**What it does to a crate (G0030).** It fills one place fewer per commander on the field before banking.
The numbers stay as they were (A 10, B 16): solo that is five places of headroom, four players two, and from
seven players a field that starts full and lives on its reserve.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A nest left behind that stops.* That is G0031's sleep.

**No:** *Left behind by biome rather than by distance.* A man across the ridge from it is still beside it;
the biome is what wakes it, not what keeps it working.

**No:** *Counting a commander waiting to come back.* He is not on the field.

**No:** *A full field holding a commander back.*

**Revisit when** co-op has been played with a full field: if a large game starves, the answer is a headroom
per seat - a number every caller reads through `squadMost` - not leaving commanders out of the count. And
when a long mission has been played: whether five is a trickle or a nuisance.

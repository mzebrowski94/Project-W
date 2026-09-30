# G0028: A player is a slot, and joining is plugging a pad in

**Status.** amended by G0038: seat zero is the keyboard, and the ceiling is eight; amended by G0055: a commander comes back at the rally point

**Trigger.** Anything that adds, removes or identifies a player; anything that assumes there is one
commander; any proposal for a lobby, a character select or a press-start screen.

**Enforced by.** `CoopTest.drivesEachCommanderFromItsOwnSlot`,
`.letsTheHigherSlotsOrderStandWhenTwoCommandersSpeakAtOnce`, `.bringsHimBackIntoTheSlotHeLeft`;
`PlayerSeatsTest.seatsEachNewPadInTheLowestFreeSlot`, `.keepsASeatForAPadThatHasBeenUnplugged`,
`.putsAReconnectedPadBackInTheSeatItLeft`, `.turnsAwayAPadWhenEverySlotIsTaken`;
`ScenarioSessionTest.putsAPlayerWhoHasJustPluggedAPadInOntoTheField`,
`.leavesAPlayerWhoIsAlreadyOnTheFieldAlone`, `.putsEverySeatedPlayerBackWhenTheScenarioRestarts`.

**Constraint.** There are at most `Players.MAX` players and a player *is* a slot number. The slot lives
on the `Commander` component; one `CommanderInput` per slot arrives each tick in a `PlayerInputs`, and
`Simulation.step` is the only place that hands each one to its commander.

- **Joining is plugging a pad in.** A pad the game has not seen before claims the lowest free slot and a
  commander appears for him, at any point in any level - `Simulation.joinCommander` is the one place
  that answers where. He arrives on whoever is still standing: that ground is neither rock nor
  unreachable, it is inside the leash, and it puts him in the fight.
- **Unplugging is not leaving.** A pulled cable, a flat battery and a kicked dongle look identical and
  are almost always followed by the pad coming back, so the seat is kept, the commander stays on the
  field, and the returning pad is put in the seat it left. An empty seat reads as an idle input.
- **A slot is a number and never a reference.** Which commander is in a slot is a scan of a handful of
  entities, asked fresh; a table of entities held across ticks comes back aimed at whatever replaced
  them in the pool (G0010, G0016).
- **A man's orders are his own.** A trooper remembers the slot that gave it its order, so a commander's
  death cancels only his own men's orders, and "follow me" follows the man who said it.
- **Two commanders ordering one trooper on one tick: the higher slot is heard last and wins**, in
  ascending slot order rather than the family's order, which changes whenever somebody respawns.

**Scope.** Who the players are. Not what a pad's buttons do (ADR 0011 and `controls.properties`). The
squad is shared, and orders overwrite one another.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A lobby, a press-start screen or a character select.* Pillar 3 has no loadout screen, and a join
screen is the same idea wearing a hat. Plugging a pad in already states intent, in one second.

**No:** *A `Player` component, or a player entity holding a commander.* A second thing to keep in step
with the commander, and one of the two would eventually be wrong.

**No:** *Removing a commander when his pad disconnects.* It is indistinguishable from a kicked cable, and
one loose plug would delete a player mid-fight.

**No:** *A per-player squad, or troopers owned by a commander.* Orders already overwrite one another,
which is how two players share one squad; ownership would need a roster, and G0017 exists because a
roster is exactly what commanding here is not.

Not rejected, deferred: a pad taking the keyboard's seat - one constant in `PlayerSeats` the day it is
wanted.

**Revisit when** four players have sat down with it: whether sharing a squad reads as co-operation or as
theft, and whether a player who joins mid-fight arriving on top of somebody is a welcome landing or a
shove.

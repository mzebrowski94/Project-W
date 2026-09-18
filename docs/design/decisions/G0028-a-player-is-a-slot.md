# G0028: A player is a slot, and joining is plugging a pad in

**Trigger.** Anything that adds, removes or identifies a player; anything that assumes there is one
commander; any proposal for a lobby, a character select or a press-start screen.

**Enforced by.** `CoopTest.drivesEachCommanderFromItsOwnSlot`,
`.letsTheHigherSlotsOrderStandWhenTwoCommandersSpeakAtOnce`, `.bringsHimBackIntoTheSlotHeLeft`;
`PlayerSeatsTest.seatsEachNewPadInTheLowestFreeSlot`, `.keepsASeatForAPadThatHasBeenUnplugged`,
`.putsAReconnectedPadBackInTheSeatItLeft`, `.turnsAwayAPadWhenEverySlotIsTaken`;
`ScenarioSessionTest.putsAPlayerWhoHasJustPluggedAPadInOntoTheField`,
`.leavesAPlayerWhoIsAlreadyOnTheFieldAlone`, `.putsEverySeatedPlayerBackWhenTheScenarioRestarts`.

**Constraint.** There are at most `Players.MAX` players and a player *is* a slot number. The slot
lives on the `Commander` component; one `CommanderInput` per slot arrives each tick in a
`PlayerInputs`, and `Simulation.step` is the only place that hands each one to its commander. Slot
zero is the keyboard and mouse and no pad ever takes it (1.a, alpha), so four players is one keyboard
and three pads.

**Joining is plugging a pad in.** A pad the game has not seen before claims the lowest free slot and
a commander appears for him, at any point in any level - `Simulation.joinCommander` is the one place
that answers where, and no scenario is asked about it. He arrives on whoever is still standing: ground
somebody is demonstrably on is neither rock nor unreachable and needs no map query to establish, it is
inside the leash, and it puts him in the fight rather than at the edge of the level watching it.

**Unplugging is not leaving.** A pulled cable, a flat battery and a kicked dongle look identical from
here and are almost always followed by the pad coming back, so the seat is kept, the commander stays
standing on the field, and the pad that returns is put in the seat it left - which is what the pad's
id is for. An empty seat reads as an idle input, so his commander stands still rather than holding
whatever he was last pressing.

**A slot is a number and never a reference.** Which commander is in a slot is a scan of at most four
entities, asked fresh. A table of entities held across ticks comes back aimed at whatever replaced
them in the pool (G0010, G0016), and the commander is the likeliest entity in the game to be killed
and recycled.

**Scope.** Who the players are. It says nothing about what a pad's buttons do (ADR 0011 and
`controls.properties`), and nothing about whether the squad is shared - it is, and orders overwrite
one another, which is the rule below.

**The rules it changed.**

- *His going down releases the squad* is now **his own men**. A trooper remembers the slot that gave
  it its order, so a death across the map no longer cancels the formation somebody else is holding,
  and "follow me" follows the man who said it.
- *An order stands on whoever heard it* now has a tie-break: when two commanders shout at one trooper
  on the same tick, **the higher slot is heard last and wins**. Ascending slot order, deliberately,
  rather than whatever order Ashley holds the family in - that order changes every time somebody is
  killed and respawned, and a rule that quiet is a rule nobody can play around.
- *He comes back at the left of the arena* is now **beside whoever is still standing**, falling back
  to the arena edge only when nobody is. The old place is a long walk in single player and, in co-op,
  a dead player watching his friends fight from across the map.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A lobby, a press-start screen or a character select.* Pillar 3 says the squad is the resource
and there is no loadout screen; a join screen is the same idea wearing a hat. Plugging a pad in is
already an unambiguous statement of intent, and it takes one second rather than four menu presses.

**No:** *A `Player` component, or a player entity holding a commander.* A second thing to keep in step
with the commander, and one of the two would eventually be wrong. The slot is one `int` on the
component that already means "this is a player's unit".

**No:** *Pads taking slot zero, or the keyboard moving.* Not rejected, deferred: 1.a fixes player one
to the keyboard for the alpha, and the day a pad may take slot zero is the day `PlayerSeats` stops
reserving it - which is one constant and no other change.

**No:** *Removing a commander when his pad disconnects.* It is indistinguishable from a cable being
kicked, and it would let one loose plug delete a player mid-fight along with whatever he was carrying.

**No:** *A per-player squad, or troopers owned by a commander.* Orders already overwrite one another,
which is the whole of how two players share one squad; ownership would need a roster, and G0017 exists
because a roster is exactly what commanding here is not.

**Revisit when** four players have actually sat down with it. The two things to watch are whether
sharing a squad reads as co-operation or as theft, and whether a player who joins mid-fight arriving
on top of somebody is a welcome landing or a shove.

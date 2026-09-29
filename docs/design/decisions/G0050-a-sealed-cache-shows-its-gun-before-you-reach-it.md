# G0050: A sealed cache shows its gun before you reach it

**Trigger.** Deciding which gun a weapons cache holds at any time other than when the level is laid;
the armoury deck; an upgrade that changes which guns turn up; proposing a mystery crate.

**Enforced by.** `SimConfigTest.revealsASealedCacheBeforeACommanderCanReachIt` - the reveal radius
exceeds the widest cache's reach plus the commander, so no commander can touch a sealed cache before
it has shown its gun. `PickupSystemTest` - a sealed cache cannot be taken; it is revealed on the tick a
commander comes within the radius and not before; the deck deals no card twice before every card has
come up. `UpgradeTest` - Firepower's cards are dealt, and a deck that grows is shuffled afresh. The
catalogue check refuses a deal of a card the deck does not hold.

**Amends** the rationale on `PickupKind` and the glossary's *weapons cache*: "one kind per gun, so
which gun is in a crate is legible before he has walked to it" is still true of every cache anyone
can take. A **sealed** cache is not one yet.

**Constraint.**

- A level may place **sealed** weapons caches. A sealed cache offers nobody anything, so it cannot be
  taken while sealed.
- The first tick a commander stands within its reveal radius, it is dealt a cache kind from the
  level's weighted **armoury deck** and becomes that cache in place - its look and grant change, its
  components stay. From then on it is an ordinary weapons cache, showing its gun to everybody.
- The deck is a `Bag` on its own stream, so a gun does not come up twice before every card has; the
  cards are folded through the upgrade read port, so an upgrade such as Firepower adds cards (ADR
  0017).
- Hand-placed caches keep their gun. In Adventure about half of each level's caches are sealed, and
  the lair's cache of every gun stays as laid.

**Scope.** Weapons caches only. Supply drops, barrels, mines and food keep what they are from the
first tick.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A gun dealt when the cache is taken.* The player would walk over a crate without knowing
whether he wants what is in it - pillar 5, and the "take it or deny it" moment a cache exists for.

**No:** *Re-dealing a cache once it has been revealed.* A gun that changes after he has seen it is a
gun he cannot plan around.

**Revisit when** a level wants a cache revealed by something other than a commander coming near - a
trooper, a switch, an objective.

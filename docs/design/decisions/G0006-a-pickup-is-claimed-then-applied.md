# G0006: A pickup is claimed on contact and applied afterwards

**Trigger.** Adding a pickup or a grant, or anything claimed on contact.

**Enforced by.** `ArchitectureTest.onlyTheReaperRemovesEntities` for the removal half; the rest by reading.

**Constraint.** Touching a pickup grants nothing. Contact resolution records a claim - on the taker
(`Taking.claimed`) and on the pickup (`Pickup.claimed`) - and `PickupSystem`, later in the same tick,
decides what the claim means and spends the pickup. Nothing in the collision path reads a grant,
changes health, swaps a weapon or removes an entity.

Two rules fall out of the claim being a value rather than an action, and both are load-bearing:

- **Exactly one taker per pickup.** The first pair the collision grid visits sets `claimed`; the
  second finds it set and walks on. The grid is a dense array with no hashing, so which unit wins is
  the same on every replay of the same run.
- **A side with nothing on offer leaves it standing.** The commander crossing alien food does not
  consume it. Denial has to be earned some other way, and one item can mean different things to the
  two sides without a "who may take this" flag to keep consistent.

**Scope.** Pickups. It is the same split `ContactDamage` and `Damage` already use for blows, and it
is stated separately only because a pickup looks so much simpler that applying it on the spot seems
obviously fine.

It says nothing about *when* the claim is resolved. That `PickupSystem` runs before `DamageSystem` -
so a grunt shot to a sliver that reaches a flower first gets up whole - is an ordering decision
recorded in `Simulation`'s Javadoc, and is free to change without touching this.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Apply the grant in `onContact`.* It is four lines shorter and reads perfectly well. Rejected because
the collision path does not know what it would need to know: whether the taker is already dying,
whether a grant belongs to the taker or to the level, or what a future grant will need. Every one of
those would arrive as another branch inside a method whose entire value is that it knows only
geometry - which is how `CollisionSystem` would become the place every mechanic goes to be special.

**No:** *A `canTake` flag or a side mask on the pickup, next to its grants.* Rejected because it is the same
fact written twice: a side with `Grant.NOTHING` on offer already cannot take it. Two ways to say one
thing is two ways to disagree.

**No:** *Removing the pickup entity the moment it is claimed.* Rejected because only `ReaperSystem` removes
anything (`ArchitectureTest.onlyTheReaperRemovesEntities`), and because a pickup that vanished
mid-tick would take its own grant with it.

**Revisit when** something must be picked up that is not an entity in the world - an objective
completed by standing somewhere, a timer that pays out. That is a different mechanic wearing this
one's clothes, and forcing it through a claim would be the tell.

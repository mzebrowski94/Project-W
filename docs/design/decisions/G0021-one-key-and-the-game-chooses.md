# G0021: One action key, and the game chooses what it does

**Status.** amended by G0056: opening a crate is on the list the key chooses from

**Trigger.** Adding an action the commander performs on something near him, or proposing any way for
the player to pick between two of them.

**Enforced by.** `ActionTest.offersToPickUpAManWhoIsDownBesideHim`,
`.offersNothingForAManWhoIsDownOutOfReach`, `.stopsOfferingWhenHeWalksOutOfReach`.

**Constraint.** `E` performs whatever the commander could do to whatever is within arm's length,
nearest first. There is no selection step and nothing to choose from: `ActionSystem` resolves the
subject from scratch every tick and publishes the *kind* of action on his `Action` component for the
interface to show. The player learns one key and one distance.

Hauling is the one exception, and it is on `F` rather than in the list. It is not a thing you do to
something and finish - it is a state you enter and leave - so folding it into the same key would mean
`E` sometimes starting a job and sometimes toggling a mode.

**Scope.** Actions the commander takes on a nearby entity. It says nothing about weapons, which have
their own bindings, or about squad orders, whose input is still open (G0017).

**Nothing is named, only kinds.** `Action` carries an `ActionKind`, never an entity - components are
pooled and a reference held across ticks comes back aimed at whatever replaced it (G0010, G0016). Two
consequences worth keeping: walking away from a man cancels the work, and the work itself resets
rather than banking, because "what he is doing" is re-decided from where he is standing.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A radial menu, or any prompt the player picks from.* The obvious answer the moment there are three
actions, and the one the squad-order binding is already waiting for. Rejected here because these
happen inside a firefight with something walking at you: pillar 1 says a mechanic that makes the
player stop and compute belongs in a different game, and a menu over a dying man is exactly that. A
radial for *orders* is a different question - orders are given from cover, between fights.

**No:** *A key per action.* Scales to three and then runs out of hand, and it makes the player learn which
key applies to which thing rather than learning one. The input map is also nearly full.

**No:** *Selecting a target and then acting on it.* Buys precision in a crowd, and costs a second verb, a
selection state, a way to see what is selected, and a way to clear it. Nearest-within-reach is wrong
occasionally; taking a step is the fix, and it costs nothing to learn.

**No:** *Queuing an action, or letting work accumulate across interruptions.* Rejected because standing still
in the open is the entire price of a rescue. A version that banked the seconds would let the player
tap the key on every pass, which is the same as not charging for it.

**Revisit when** a third action exists whose subject genuinely overlaps another's within one reach -
at that point "nearest wins" starts being wrong often rather than rarely, and the answer is probably
a priority per kind rather than a menu.

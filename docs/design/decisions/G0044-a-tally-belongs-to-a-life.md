# G0044: A tally belongs to a life

**Trigger.** Counting anything per player - kills, goals, heals, a score; giving a blow, a shot, a
grenade or a turret a notion of who is behind it; showing a player's numbers.

**Enforced by.** `TallyTest` (the invariant rows in `docs/design/invariants/progression.md`), `ComponentResetTest` for
the component.

**Constraint.**

- Every commander carries a `Tally` from spawn: **kills**, **goals**, **stabilised**. It is reaped with
  him; the respawned commander is a new entity with a new tally. No code resets a tally (plan D1).
- **Credit is a slot, never an entity** (G0016). Whatever lands a blow carries the slot of the player
  behind it, stamped where it is spawned: his shot and his sweep, his grenade, the shots of a turret
  he crews. Anything else carries nobody (`Tally.NOBODY`) - a creature's, a trooper's, a mine's, fire.
- `Damage` gathers a tick's credit beside its amount, keeping the **higher slot** when two players'
  blows land together (the rule orders follow, G0028). The death reads it once, in `DamageSystem`:
  an enemy's death counts on the killer's tally; a body that explodes carries the same credit into
  its blast, so a barrel or a charged corpse counts for whoever set it off.
- **Kills** are the other side's dead only - a nest is on that side, so destroying one is a kill;
  friendly fire counts for nothing, neither way (Q13).
  **Goals** are control points taken while he stood inside them (Q12). **Stabilised** is every man
  he put back on his feet, another player's commander included (Q14). Squad kills credit nobody (Q11).
- Shown, never spent (pillar 3).

**Why.** A per-life count on the man is the only reading of "per life" that needs no reset code and
survives a drop-in peer: the snapshot already carries every component. Crediting by slot rather than
by reference is G0016's rule about pooled entities; stamping it on the blow means the question "whose
kill?" has one answer, asked at one place, however the blow travelled.

**Scope.** What is counted and whose it is. Not how it is shown (the UI plan, 7.2), not any reward.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A score, a currency, or anything that buys with the tally.* Pillar 3.

**No:** *A tally on the level entity, or kept per slot across lives.* Per life was decided (D1); a
running total is a different question and would need reset code this design does not have.

**No:** *Credit by `Entity` reference or by handle to the shooter.* A pooled entity held from the shot
to the death comes back as something else (G0016); a slot does not.

**No:** *Crediting whichever blow the collision grid visited first.* Order-dependent, so it differs
between machines that iterate differently - the higher slot is a rule.

**No:** *Counting a trooper's kill for the commander who ordered him.* The squad's kills are nobody's
(Q11); an order is not a blow.

**Revisit when** a level asks for a score, or a kill needs to mean something other than a death on the
other side - a nest counted apart from a creature, say.

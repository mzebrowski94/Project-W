# G0005: A melee strike is an entity, not an overlap query

**Status.** accepted

**Trigger.** Adding a melee weapon, or anything that hurts by being somewhere.

**Enforced by.** Nothing mechanical - caught by reading.

**Constraint.** A swing of a melee weapon is spawned into the world as an ordinary entity with a
`Position`, a `Body`, a `Collider`, a `Faction` and a `ContactDamage`. It travels its arc over
several ticks and is removed like anything else. No system asks the spatial grid what is in front of
the commander, and nothing writes damage outside the one contact rule in `CollisionSystem`.

This is the melee half of the no-hitscan invariant. That row was written about projectiles, and a
sweep is not a projectile - it does not travel *to* anywhere - so the rule did not obviously reach
it. It does now, and for the same three reasons: collision keeps one path, the simulation stays
replayable, and every threat in the world is a thing the player can see.

**Scope.** Anything that hurts by being somewhere: a swung weapon, and later a flame cone, a shock
wave, a mine's blast radius. It says nothing about effects with no position - a heal, a buff, a
score - which are not contacts and have no business in the collision system either way.

**DO NOT BUILD** - rejected, and likely to be re-proposed. A `MeleeSystem` that queries the grid for everything
inside the arc and writes `Damage` directly. It is fewer lines, allocates nothing extra, and will be
proposed the first time someone notices that a swing entity exists for six ticks and then dies. It
was rejected because it is a second damage path: the moment it exists, "what can hurt what" is
answered in two places that have to be kept agreeing, invulnerability and faction and the
one-stagger-per-tick rule all have to be reimplemented in the second one, and the first bug where a
punch ignores invulnerability will take a day to find. The entity costs one pooled allocation per
swing and inherits all of that correctness for free.

**Revisit when** a weapon needs a hit shape a circle genuinely cannot approximate - a long thin
sweep, a cone with a hard edge - and swept circles start looking like a bad fit. The answer then is a
second `Collider` shape in the solver, which keeps collision in one place; it is not a bypass.

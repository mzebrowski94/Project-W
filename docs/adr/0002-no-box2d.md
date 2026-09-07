# ADR 0002: No Box2D, no box2dlights

**Constraint.** No physics engine. Collision is ours: a uniform-grid spatial hash with circle and
AABB overlap tests, living in `sim`. No dependency may come from JitPack.

**Scope.** The whole simulation. Does not forbid physical-*feeling* movement - knockback,
acceleration and drag are ours to write, and are simple in a top-down arcade game with no stacking,
joints or ragdolls.

**Rejected, and likely to be re-proposed.** Box2D - real physics for free, but native code inside
the simulation, platform-fragile determinism, and slower headless tests. It is also the obvious
suggestion whenever collision code gets fiddly, which is exactly when the determinism cost would
bite hardest.

**Also decisive:** `box2dlights` is only published on JitPack, which this account's egress policy
blocks. Keeping it would have broken the sandbox build outright.

**Revisit when** physical interaction becomes a core mechanic rather than a feel. Collision sits
behind its own types in `sim`, so a swap is contained.

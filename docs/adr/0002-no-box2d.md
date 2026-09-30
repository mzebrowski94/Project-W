# ADR 0002: No Box2D, no box2dlights

**Status.** amended by ADR 0007: every body is a circle, so there is no AABB and no shape interface

**Trigger.** Writing collision, knockback or movement physics; proposing a physics library.

**Enforced by.** No test. A JitPack dependency simply fails to resolve in the sandbox.

**Constraint.** No physics engine. Collision is ours: a uniform-grid spatial hash with circle and
AABB overlap tests, living in `sim`. No dependency may come from JitPack.

**Scope.** The whole simulation. Does not forbid physical-*feeling* movement - knockback,
acceleration and drag are ours to write, and are simple in a top-down arcade game with no stacking,
joints or ragdolls.

**DO NOT BUILD** - rejected, and likely to be re-proposed. Box2D - real physics for free, but native code inside
the simulation, platform-fragile determinism, and slower headless tests. It is also the obvious
suggestion whenever collision code gets fiddly, which is exactly when the determinism cost would
bite hardest.

**Also decisive:** `box2dlights` is only published on JitPack, which this account's egress policy
blocks. Keeping it would have broken the sandbox build outright.

**Revisit when** physical interaction becomes a core mechanic rather than a feel. Collision sits
behind its own types in `sim`, so a swap is contained.

# G0003: The renderer reads a component; it never asks what an entity is

**Trigger.** Writing or changing a renderer; adding a kind of entity that must be drawn. Its temporary
half is superseded by G0043 for looks with a puppet - the commander's is the first. Read that too.

**Enforced by.** Nothing. The record says so: `ArchitectureTest` catches `sim` importing libGDX, not `sim` carrying three floats only a renderer cares about.

**Constraint.** Two parts, one permanent and one temporary.

*Permanent:* how an entity is drawn is decided by reading a component that describes its appearance.
Rendering never branches on an entity's class, its faction, or the presence of a gameplay component.
`sim` holds no visual data and does not know that a renderer exists.

*Temporary:* until real art exists, that appearance resolves to a primitive shape and a colour.
Everything on screen is a shape, and no time is spent on placeholder sprites.

**Scope.** The temporary half expires the day the first atlas lands. The permanent half does not,
and it is the only reason the temporary half is affordable: the swap is one class, not an edit per
entity kind. `WorldRenderer` already documents the intent and already draws every entity the same
colour - this record is what stops the first entity kind from being special-cased.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

1. **No:** *Branching in the renderer* - `if (entity has Enemy) red else blue`. The cheapest possible thing
   the first time there are two kinds of entity, and the reason a sprite pass later means touching
   the renderer for every kind ever added. It will be proposed again each time a single new kind
   makes the component look like overkill for it.
2. **No:** *Real or placeholder sprites now* - art is the slowest way to find out a mechanic is not fun,
   and placeholder art is slow without even being the art.
3. **No:** *Putting colour or shape on a gameplay component* - a `Body` that carries a colour. Convenient,
   and nothing enforces against it: `ArchitectureTest` catches `sim` importing libGDX, not `sim`
   carrying three floats that only a renderer cares about. It has to be caught by reading.

**Revisit when** the first sprite atlas lands - the temporary half only. The permanent half has no
revisit trigger; changing it means changing what the module boundary is for, which is ADR 0003.

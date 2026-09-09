# Gameplay decisions

Closed decisions about what the game *is*, kept separate from `docs/adr/`, which covers technical
and structural choices. Same format, same discipline, different subject.

| # | Decision | Status |
|---|---|---|
| [G0001](G0001-inspired-by-not-remake.md) | Inspired by *Triumph! War 2099*, not a remake of it. | accepted |
| [G0002](G0002-aim-is-independent-of-movement.md) | Aim is independent of movement. | accepted |
| [G0003](G0003-the-renderer-reads-a-component.md) | The renderer reads a component; it never asks what an entity is. | accepted |
| [G0004](G0004-no-event-bus.md) | No event bus - reactions are components, feedback is a spawned entity. | accepted |
| [G0005](G0005-the-strike-is-an-entity.md) | A melee strike is an entity, not an overlap query. | accepted |
| [G0006](G0006-a-pickup-is-claimed-then-applied.md) | A pickup is claimed on contact and applied afterwards; exactly one taker, and a side offered nothing leaves it standing. | accepted |
| [G0007](G0007-level-state-is-an-entity.md) | State that belongs to the level is an entity. Narrows G0004. | accepted |
| [G0008](G0008-a-level-may-be-larger-than-the-view.md) | A level may be larger than the view. Amends pillar 4. | accepted |
| [G0009](G0009-the-grid-builds-the-map-and-nothing-else.md) | The tile grid lays a level out; nothing in the game is quantised to it. | accepted |
| [G0010](G0010-a-mind-is-a-field-not-a-tree.md) | A unit's mind is a field on a component, not a behaviour tree. | accepted |
| [G0011](G0011-detection-is-range-only.md) | Detection is range only; rock does not hide you. | accepted |
| [G0012](G0012-the-dodge-is-deterministic.md) | A dodge is a function of the shot, not a roll. | accepted |
| [G0013](G0013-the-clinch-is-what-a-parry-earns.md) | A melee kill clinches only something already committed to attacking. | accepted; one rejection superseded by G0014 |
| [G0014](G0014-the-clinch-carries.md) | The clinch carries the body in under its own momentum; it never places it. | accepted |

## When to write one

Only when an alternative was actually rejected **and is likely to be proposed again**. A decision
that nobody would argue with is an invariant - put it in `docs/design/README.md` instead. A
description of how something works is neither; that is the code's job.

Keep four things: the **constraint**, its **scope** (especially where it does *not* apply), the
**rejected alternatives** likely to resurface with one reason each, and a **revisit trigger**.

Like ADRs, these are not edited as the game evolves - a changed decision gets a new record
superseding the old one.

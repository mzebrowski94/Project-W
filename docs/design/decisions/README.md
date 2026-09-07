# Gameplay decisions

Closed decisions about what the game *is*, kept separate from `docs/adr/`, which covers technical
and structural choices. Same format, same discipline, different subject.

| # | Decision | Status |
|---|---|---|
| [G0001](G0001-inspired-by-not-remake.md) | Inspired by *Triumph! War 2099*, not a remake of it. | accepted |
| [G0002](G0002-aim-is-independent-of-movement.md) | Aim is independent of movement. | accepted |
| [G0003](G0003-the-renderer-reads-a-component.md) | The renderer reads a component; it never asks what an entity is. | accepted |
| [G0004](G0004-no-event-bus.md) | No event bus - reactions are components, feedback is a spawned entity. | accepted |

## When to write one

Only when an alternative was actually rejected **and is likely to be proposed again**. A decision
that nobody would argue with is an invariant - put it in `docs/design/README.md` instead. A
description of how something works is neither; that is the code's job.

Keep four things: the **constraint**, its **scope** (especially where it does *not* apply), the
**rejected alternatives** likely to resurface with one reason each, and a **revisit trigger**.

Like ADRs, these are not edited as the game evolves - a changed decision gets a new record
superseding the old one.

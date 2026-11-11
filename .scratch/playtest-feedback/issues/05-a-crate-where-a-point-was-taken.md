# A crate where a point was taken

Type: grilling
Status: open
Blocked by: 04
Section: Grenades and crates

## Question

When a **control point** is taken, a small crate appears by it that refills grenades (and whatever else 04
decided is refilled), with a simple animation of the take. What is it - a new **pickup** kind with a
**grant**, or a **crate** variant - and what is it called?

## From Mateusz (translated)

> Small crates should spawn after taking a control point (ones that refund grenades and resources) + a simple
> animation of gaining the resources.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind`. 04's answer. G0006 (claimed on contact), G0030 / G0055 (what a taken point
already earns), G0049 (a draft per point), `Grants`, `docs/glossary.md` *Growth and pickups*. The animation is
a spawned feedback entity (G0004), never an event.

## Open

The name (not *mini crate*, which collides with **crate**); worth anything to the aliens or walked over; one
per point or one per player; what it does when he is already full.

## Done when

Built as data (G0008), glossary row, `verify.sh` green.

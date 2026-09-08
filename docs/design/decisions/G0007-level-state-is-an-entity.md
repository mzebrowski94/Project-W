# G0007: State that belongs to the level is an entity

**Constraint.** A number that belongs to the level rather than to anything standing in it - troopers
in reserve, and whatever comes after - lives on a `Level` component, carried by one entity spawned
with the world and never removed. Systems reach it through a family like any other. There is no
static, no field on `Simulation`, and no message queue.

**Scope.** Level-scope simulation state. This **narrows G0004** rather than contradicting it: G0004
rejected an event bus and named, as the gap that would one day justify one, "something that must react
that is not an entity and cannot sensibly be made one: a global score, an achievement, an audio cue".
The first of those has now arrived, in the form of a pickup that grants troopers instead of changing
whoever took it. The answer is that it *can* sensibly be made an entity, so G0004 stands unchanged and
its revisit trigger is one case smaller.

It does not cover client state - the harness, the overlay and the menu keep their own - nor anything
an entity could own. If a number belongs to a unit, it belongs on the unit.

**Rejected, and likely to be re-proposed.**

*A field on `Simulation`, or a `LevelState` object handed to the systems that need it.* Rejected
because it puts simulation state somewhere no family can see and no snapshot would carry, and because
"just this one field" is how a second one gets added. An entity costs nothing and is already
replayable, inspectable and reapable.

*The pooled per-tick event queue from G0004, now that a global reaction exists.* Rejected for the
reason G0004 gives: once a queue exists, systems start talking through it instead of through
components, and ordering stops being visible in `Simulation`'s constructor. A grant that lands on an
entity needs no queue to find it.

*A singleton component enforced by the framework.* Ashley has no such concept, and adding one would
mean a lookup that can fail. The level entity is spawned in `Simulation`'s constructor, before any
scenario runs, so a system that iterates the family finds exactly one and needs no null case.

**Revisit when** a reaction genuinely has no entity to belong to and none can be invented for it -
G0004's audio cue with no position is still the honest example. A queue may be right for that; it is
not right for this.

# G0004: No event bus - reactions are components

**Trigger.** Making two systems communicate; proposing an event bus, queue, signal or listener.

**Enforced by.** Nothing mechanical - caught by reading. No system holding a reference to another is the thing to check.

**Constraint.** Systems in `sim` communicate through components and family membership. There is no
message queue, no listener registry, no signal, and no system holds a reference to another.

Something that must react to an event reacts to a **component that represents it**. A death is the
worked example: damage resolution adds a `Dead` marker, any number of reaction systems iterate the
families they care about, and one reaper removes the entity last. The marker surviving to the end of
the tick *is* the event - one that can be inspected in a debugger, asserted in a test, and replayed,
because it is state rather than a callback.

Feedback for the player is a **spawned entity**, not a message. An explosion, a corpse, a scorch
mark: the reaction spawns it, the renderer draws it like anything else, a lifetime removes it.

**Scope.** The simulation. The client is free to observe the engine however it likes; it already
does. This says nothing about a future replay or network layer, which serialises state and is
unaffected either way.

**DO NOT BUILD** - rejected, and likely to be re-proposed. A pooled per-tick event queue. It will be proposed the
first time something needs to react to a death from far away in the tick, and it will look like the
obvious tool. Rejected because an event bus is the path of least resistance: once it exists, systems
start talking through it instead of through components, ordering becomes implicit, and the thing that
made the ECS legible - that all state is somewhere you can look at it - quietly stops being true.
Nearly everything this game needs to notify *is* an entity, and for those a component is strictly
better.

**Revisit when** something must react that is not an entity and cannot sensibly be made one: a
global score, an achievement, an audio cue with no position in the world. That is a real gap and a
queue is the right answer to it - but none of those exist yet, and building the mechanism before the
need is how the failure above starts.

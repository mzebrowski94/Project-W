# G0010: A unit's mind is a field on a component, not a behaviour tree

**Constraint.** What a unit is doing is a value on a component it already carries - a boolean, an
enum, a countdown - written by exactly one system. There is no behaviour tree, no state-machine
object, no blackboard, and no per-unit AI instance. A behaviour is a system that reads those fields
and writes a desired velocity; which behaviour applies is decided upstream, once, and everything
downstream reads the answer.

Three things follow, and all three are load-bearing:

- **State is never a component that gets added and removed.** `ArchitectureTest` fails the build if
  anything but `Spawner` changes what an entity is made of, because adding or removing a component
  mid-tick mutates the families being iterated and silently skips entities. The whole "one component
  per state, select with a `Family`" pattern is therefore unavailable here, and `Leap.Phase`,
  `Wander.Phase` and `Perception.aggro` are what it looks like instead.
- **No AI object holds a reference to an entity between ticks.** Components are pooled: a reference
  kept across ticks can outlive what it pointed at and come back aimed at whatever was built from
  the recycled instance. Targets are re-resolved every tick and only the latch survives.
- **Two behaviours never both drive one unit.** They are selected by mutually exclusive conditions
  rather than by priority, so there is no arbitration to get wrong.

**Scope.** Every unit the simulation drives. It says nothing about how many behaviours there may be,
and nothing about the pipeline they write into - deciding is a field, moving is `Steering`, and those
are separate questions.

**Rejected, and likely to be re-proposed.**

*A behaviour tree.* It is the industry answer and it will be proposed the first time a fourth
behaviour arrives. Rejected because a tree earns its keep when there are many behaviours and somebody
other than the programmer is composing them, and it costs a per-unit blackboard, a per-tick traversal
and a second vocabulary to read the AI in. There are two behaviours. When there are eight and they
are being reordered rather than written, that is the trigger below.

*A finite state machine object per unit, or gdx-ai's `StateMachine`.* Same shape, smaller: it still
means an object per unit holding a reference to that unit, allocated at spawn, and a second place
besides the component where a unit's state lives. The enum field is the state machine, minus the
object.

*One component per state, selected with a `Family`.* The natural ECS answer, and the one that reads
best - right up against the enforced rule that only `Spawner` changes what an entity is made of.

**Revisit when** a unit needs behaviour that is not a choice between mutually exclusive drivers - a
sequence it must run to completion, an interruption it must resume from, or a priority ordering
somebody wants to tune without recompiling. That is a real tree-shaped problem and this is a poor
answer to it.

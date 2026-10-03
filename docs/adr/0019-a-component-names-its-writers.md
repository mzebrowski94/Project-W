# 0019: A component names its writers

**Status.** accepted

**Trigger.** Writing a component field, or calling a component method that changes one, from a class
that did not do so before; adding a component; reading or regenerating the *Written by* column of
`docs/components.md`.

**Enforced by.** `ArchitectureTest.everyComponentIsWrittenByExactlyItsDeclaredWriters`, measuring with
`ComponentWriters` (`ComponentWritersTest` pins what it counts).

**Constraint.**

- **The declaration is the doc.** `docs/components.md` has a *Written by* column. The build compares it
  with what the bytecode writes and fails on any difference, either way: an undeclared writer, and a
  declared one that no longer writes. The failure message is the cell to put there.
  `component-index.py` regenerates every other column and keeps this one.
- **A write** is a field set from outside the component class, a call to a **changing method** - one
  that sets a field of its own class, changes a collection one of its fields holds, or calls another
  changing method of it - or a changing call (`add`, `set`, `removeIndex`, `clear`...) on a collection a
  component field holds. Found from the bytecode; nothing is annotated. A call to a component's `reset()`
  is a write like any other.
- **Credited to the system** whose code reaches the write, following calls - through an interface to
  what implements it, and into lambda bodies - through any class in `sim` that is not a component. A
  class nested in a system is that system. A write that no system reaches is credited to the class that
  makes it: `Simulation.step` writes `Intent`, `Picks` writes `Drafts`.
- **Not a write: building a world.** `Spawner`'s `spawn` methods and `Simulation`'s, a scenario laying out
  its level, `WorldCodec` restoring a snapshot, and whatever else `Spawner` does that no system asked of
  it. A system asking `Spawner` to change an entity that already exists - `applyWeapon`, `applyStrain` -
  is that system writing it.
- Per component, not per field. A field is named only where a component is split on purpose.

**Scope.** Every class in `sim.component`, written from `sim`'s main classes. `ui` and `core` write none
already (`UiArchitectureTest`, `ClientArchitectureTest`). It records who writes; whether several writers
are allowed is still G0010's question, answered by reading. *Touched by* stays as the wider index. The
bytecode cannot tell a component kept as a scratch value from one on an entity: a system holding a
`Position` as a vector to fill is declared as writing `Position`. Adding the `Dead` marker is changing
what an entity is made of, not a write, and
`ArchitectureTest.onlyTheSpawnerChangesWhatAnEntityIsMadeOf` guards it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *An annotation on the component naming its writers.* A component would import systems, reversing
the one arrow that keeps data from knowing its users.

**No:** *Writers declared in a table in test code, with the doc generated from it.* The doc the agent reads
before it writes has to be the thing the build checks; a table inside a test is read only after it fails.

**No:** *Ownership per field for every component.* Eighty components times their fields is a table nobody
reads; the component is the unit G0010 speaks of.

**No:** *Credit to the fenced package rather than the system.* G0010's one-writer rule is per system.

**No:** *Reading writes off source text.* *Touched by* already does, and cannot tell a read from a write.

**No:** *A required "why several" note on every component with more than one writer.* Measured when this
landed: 49 of 79 components have several writers - 37 without `CooldownSystem`, which counts down every
timer, and `EvolutionSystem`, whose strain change rewrites a unit's kit. A note on most rows is the table
nobody reads; what guards is the diff, where a new writer is a changed cell.

**No:** *Excluding every call into `Spawner`.* `applyWeapon` and `applyStrain` change an entity in the
middle of a tick, for a pickup, a crate or an evolution; excluding them hid three systems' writes to a
unit's whole kit.

**Revisit when** a component needs ownership per field, or most components turn out to have several
writers - then the column guards nothing a reviewer would read.

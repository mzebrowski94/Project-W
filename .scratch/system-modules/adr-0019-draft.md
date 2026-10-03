# 0019: A component names its writers

**Status.** accepted

**Trigger.** Writing a component field, or calling a component method that changes one, from a class
that did not do so before; adding a component; reading or regenerating the *Written by* column of
`docs/components.md`.

**Enforced by.** `ArchitectureTest.everyComponentIsWrittenByExactlyItsDeclaredWriters` *(name settled
when ticket 01 builds it - this draft lands in `docs/adr/` with that test)*.

**Constraint.**

- **The declaration is the doc.** `docs/components.md` has a *Written by* column. The build compares it
  with what the bytecode writes and fails on any difference, either way: an undeclared writer, and a
  declared one that no longer writes. `component-index.py` regenerates every other column and keeps
  this one.
- **A write** is a field set from outside the component class, or a call to a **changing method** - one
  that sets a field of its own class or calls another changing method of it. Found from the bytecode;
  nothing is annotated.
- **Credited to the system** whose `update` reaches the write, following calls through any class in `sim`
  that is not a component. A write that no system reaches is credited to the class that makes it -
  `Simulation.step` writes `Intent`.
- **Not a write**: `Spawner` building an entity, a component's own `reset()`, `WorldCodec` restoring one.
- Per component, not per field. A field is named only where a component is split on purpose.

**Scope.** Every class in `sim.component`, written from `sim`. `ui` and `core` write none already
(`UiArchitectureTest`, `ClientArchitectureTest`). It records who writes; whether several writers are
allowed is still G0010's question, answered by reading. *Touched by* stays as the wider index.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *An annotation on the component naming its writers.* A component would import systems, reversing
the one arrow that keeps data from knowing its users.

**No:** *Writers declared in a table in test code, with the doc generated from it.* The doc the agent reads
before it writes has to be the thing the build checks; a table inside a test is read only after it fails.

**No:** *Ownership per field for every component.* Eighty components times their fields is a table nobody
reads; the component is the unit G0010 speaks of.

**No:** *Credit to the fenced package rather than the system.* G0010's one-writer rule is per system.

**No:** *Reading writes off source text.* *Touched by* already does, and cannot tell a read from a write.

Not rejected, deferred: a *Why several* note required where a component has more than one writer -
decided once the first measurement says how many there are.

**Revisit when** a component needs ownership per field, or most components turn out to have several
writers - then the column guards nothing a reviewer would read.

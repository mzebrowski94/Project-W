# ADR 0004: Single-player, with a net-ready simulation

**Trigger.** Touching how the simulation advances, randomness or time. Its Scope clause - no networking
code - is superseded by 0012; networking itself is 0012's trigger. Read that too.

**Enforced by.** `ArchitectureTest` for the mechanical bans; `SimulationDeterminismTest` for the rest.

**Constraint.** The simulation advances only through `Simulation.step()`, always by
`SimConfig.tickSeconds()`. State changes only from input. Randomness comes only from the seeded
`Rng`, whose entire state is one `long`. Rendering reads simulation state and never writes it.

**Scope.** No networking code exists and none should be written. The constraints are not for
networking's sake alone - they are what make deterministic replay and headless testing work today,
which is why they pay for themselves before any multiplayer feature exists.

**DO NOT BUILD** - rejected, and likely to be re-proposed. Reading `Gdx.graphics.getDeltaTime()` inside a system,
or pulling a value out of the renderer, because it is momentarily convenient. That is the specific
shortcut this ADR exists to prevent.

**Revisit when** multiplayer moves from "someday" to a decision. Lockstep and rollback both stay
open from here; picking one is a new record, not an edit to this one.

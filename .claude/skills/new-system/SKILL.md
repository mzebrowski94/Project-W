---
name: new-system
description: Add a mechanic to the Project-W simulation as an ECS component plus system, tests first, in the project's conventions. Use when adding gameplay behaviour, a new component, a new Ashley system, or any new logic in the sim module.
---

# Adding a simulation system

Behaviour in Project-W is always a **component** (data) plus a **system** (logic) in the `sim`
module. Never put behaviour on a component, never put simulation logic in `core`.

## Order of work

If this is a gameplay change rather than plumbing, run the `feature` skill first - it checks the
idea against the design before any code exists.

Follow the project's test discipline exactly - it is in `CLAUDE.md` and it is not optional.

### 1. General-level tests first

Before writing any implementation, write one or a few tests that state what the mechanic should do
in broad strokes. Not edge cases. The question they answer is "what is this for".

```java
class DamageSystemTest {
    @Test
    void reducesHealthByTheDamageDealt() { ... }

    @Test
    void removesAnEntityWhoseHealthReachesZero() { ... }
}
```

Run `.claude/scripts/verify.sh :sim:test` and watch them fail for the right reason.

### 2. Implement

**Component** in `sim/.../component/`: copy `Position.java`. The one thing that bites is `reset()` -
it must clear **every** field. A pooled component that forgets one leaks state into the next entity
that reuses it, and that bug is very hard to find.

**System** in `sim/.../system/`: copy `MovementSystem.java`. It already shows the shape - static
final `ComponentMapper`s, `Family` in the constructor, no allocation in `processEntity`. The hot-path
rules are in `docs/conventions.md`; the determinism rules are enforced by `ArchitectureTest`, so you
will hear about a violation rather than having to remember it.

**Register** it in `Simulation`'s constructor. Order matters - see `docs/architecture.md`.

### 3. Fill in the specific cases

Now go back to the tests and add the specifics that surfaced while implementing: boundaries, zero
and negative values, entities missing an optional component, interaction with systems that run
before or after this one.

For each one, ask whether it is a real case or noise. A test that cannot fail for an interesting
reason is worse than no test - it is maintenance with no payoff. Delete those.

### 4. Verify

```
.claude/scripts/verify.sh
```

Green means: compiles, formatted, Error Prone clean, NullAway clean, architecture rules hold, all
tests pass. Anything less is not done.

## Checklist before saying it is finished

- [ ] `reset()` clears every field of the new component
- [ ] `ComponentMapper`s are `static final`; entity views cached, not re-queried per frame
- [ ] nothing allocates in the update path
- [ ] the system is registered, in a deliberate position
- [ ] tests run headless - no `Gdx.app`, no GL context
- [ ] new domain words are in `docs/glossary.md`
- [ ] tunables are in `assets/game.properties`, not literals in the system
- [ ] `verify.sh` is green

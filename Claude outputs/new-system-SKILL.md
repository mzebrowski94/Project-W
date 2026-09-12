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

**System** in `sim/.../system/`: copy `MovementSystem.java` - it is the smallest one and it shows the
whole shape. The file outranks any skeleton written down here, which is why there is not one: static
final `ComponentMapper`s, `Family` in the constructor, no allocation in `processEntity`. The
banned-API table and the per-entity allocation rule are in `docs/conventions.md`; determinism is
enforced by `ArchitectureTest`, so you will hear about a violation rather than having to remember it.

**Register** it in `Simulation`'s constructor. Order matters - see `docs/architecture.md`, which
records what each existing ordering means. Say in your reply why you put it where you put it.

### 3. Fill in the specific cases

Now go back to the tests and add the specifics that surfaced while implementing: boundaries, zero
and negative values, entities missing an optional component, interaction with systems that run
before or after this one.

For each one, ask whether it is a real case or noise. A test that cannot fail for an interesting
reason is worse than no test - it is maintenance with no payoff. Delete those.

### 4. Offer a scenario

We are in the alpha phase, where a system is meant to be run and judged by hand, not only by its
tests. **Ask** whether Mateusz wants a scenario demonstrating it - do not build one unheard, and do
not skip the offer either. `docs/design/alpha-phase.md` has the recipe: a class in `sim/.../scenario/`
and a line in `Scenarios.ALL`, which is the whole registry the menu reads. `Scenarios.world` is the
only place a playable world is assembled; do not assemble one anywhere else.

A system already visible in an existing scenario does not need its own; say so when offering.

### 5. Verify

```
.claude/scripts/verify.sh
```

Green means: compiles, formatted, Error Prone clean, NullAway clean, architecture rules hold, all
tests pass. Anything less is not done. If it is red, follow the failure protocol in `CLAUDE.md` -
read the error, check `docs/workflow.md`'s table, name the cause, one hypothesis at a time.

## Before saying it is finished

`CLAUDE.md`'s pre-flight list covers the project-wide items. These are the ones specific to adding a
system:

- [ ] `reset()` clears every field of the new component
- [ ] `ComponentMapper`s are `static final`; entity views cached in the constructor or
      `addedToEngine`, never re-queried per frame
- [ ] nothing allocates inside the per-entity loop
- [ ] the system is registered at a deliberate position, and you said why
- [ ] tests run headless - no `Gdx.app`, no GL context

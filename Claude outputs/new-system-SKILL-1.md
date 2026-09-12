---
name: new-system
description: Add a mechanic to the Project-W simulation as an ECS component plus system, tests first, in the project's conventions. Use when adding gameplay behaviour, a new component, a new Ashley system, or any new logic in the sim module.
---

# Adding a simulation system

Behaviour is always a **component** (data) plus a **system** (logic) in `sim`. Never behaviour on a
component, never simulation logic in `core`.

Gameplay change rather than plumbing? Run the `feature` skill first - it checks the idea against the
design before any code exists.

Check `CLAUDE.md`'s reuse list before starting. Most mechanics need an existing component, not a new one.

## 1. General-level tests first

Before any implementation, one or a few tests stating what the mechanic should do in broad strokes. Not
edge cases. The question they answer is "what is this for".

```java
class DamageSystemTest {
    @Test
    void reducesHealthByTheDamageDealt() { ... }

    @Test
    void removesAnEntityWhoseHealthReachesZero() { ... }
}
```

`.claude/scripts/verify.sh :sim:test`, and watch them fail for the right reason.

## 2. Implement

**Component** in `sim/.../component/`: copy `Position.java`. The thing that bites is `reset()` - every
field, every time.

**System** in `sim/.../system/`: copy `MovementSystem.java`. It is the smallest one and shows the whole
shape, and the file outranks any skeleton written down here - which is why there is not one. Rules:
`docs/performance.md` for the loop body, `docs/conventions.md` for everything else.

**Register** it in `Simulation`'s constructor. Check `docs/simulation-order.md` for the constraints your
position must not break, and say in your reply why you chose it.

## 3. Fill in the specific cases

Back to the tests with what surfaced while implementing: boundaries, zero and negative values, entities
missing an optional component, interaction with the systems either side of yours.

For each, ask whether it is a real case or noise. A test that cannot fail for an interesting reason is
maintenance with no payoff. Delete those.

## 4. Offer a scenario

Alpha phase: a system is meant to be run and judged by hand, not only by its tests. **Ask** whether
Mateusz wants a scenario - do not build one unheard, do not skip the offer. `docs/design/alpha-phase.md`
has the recipe: a class in `sim/.../scenario/` and a line in `Scenarios.ALL`, the whole registry the
menu reads. `Scenarios.world` is the only place a playable world is assembled; do not assemble one
anywhere else.

A system already visible in an existing scenario does not need its own. Say so when offering.

## 5. Verify

`.claude/scripts/verify.sh`. Green means compiles, formatted, Error Prone clean, NullAway clean,
architecture rules hold, all tests pass. Red -> the failure protocol in `CLAUDE.md`.

`CLAUDE.md`'s pre-flight list is the finishing check. The two items specific to this skill and not on
it: entity views cached in the constructor or `addedToEngine` rather than re-queried per frame, and
tests running headless - no `Gdx.app`, no GL context.

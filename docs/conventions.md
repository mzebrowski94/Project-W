# Coding conventions

## Language

Java 25, `--release 25`. Use the finalized modern features: records, pattern matching for
`switch`/`instanceof`, record patterns, unnamed variables (`_`), exhaustive switches, sealed types.

No preview or incubator APIs. No Structured Concurrency, no Vector API. If a feature needs
`--enable-preview`, it is not available to us.

## Architecture

- Composition over inheritance. ECS and strategy objects, not deep class hierarchies.
- Program to interfaces; hide implementations behind them. Swap behaviour by swapping an
  implementation or a lambda, not by subclassing.
- Manual constructor DI. No Spring, no IoC container, no service locator. Lombok's
  `@RequiredArgsConstructor` is fine for the boilerplate.
- Immutable `record`s for static configuration and definitions. Mutable pooled classes for runtime
  entities: mutate in place, never replace a pooled instance.
- Small, cohesive types. One class per file. Don't over-engineer a small addition.

### The one exception to encapsulation

ECS components use **public mutable fields**, not getters and setters:

```java
public final class Position implements Component, Pool.Poolable {
    public float x;
    public float y;
}
```

Components are data, read and written thousands of times per frame by systems that already own
them. Accessors add nothing but indirection here, and this is the idiom every Ashley codebase (and
every Ashley example the model has seen) uses. Everywhere else - pooled non-component runtime
classes included - use Lombok `@Getter`/`@Setter` plus a `reset()`.

## Performance

The hot loop is per-frame entity, projectile and collision updates. There:

- **Zero allocation per frame.** No `new` in a system's update path. Pool everything. No boxing, no
  varargs, no `Optional`, no string concatenation.
- Classic `for` or enhanced-`for`. No streams, no lambda capture, no iterator churn.
- Cache the `ImmutableArray` from `engine.getEntitiesFor(...)` once (in the constructor or
  `addedToEngine`), never per frame. Ashley keeps the view live.
- Cache `ComponentMapper`s in `private static final` fields.
- **Never log.** Not at any level.

Streams, lambdas and allocation are all fine in setup, loading, tooling and tests.

## Determinism

The simulation must produce identical output from identical input on every run and every machine.
This is what makes replays and any future lockstep networking possible, and it is cheap to keep and
expensive to recover.

In `sim`:

- Advance only through `Simulation.step()`, always by `SimConfig.tickSeconds()`.
- Randomness comes only from `Rng`. Never `Math.random()`, never `java.util.Random`.
- Never read a wall clock: no `System.currentTimeMillis()`, no `System.nanoTime()`.
- Never iterate a `HashMap` or `HashSet` - their order is not reproducible. Use libGDX's
  `ObjectMap`/`Array`, or sort explicitly.
- Keep floating-point operation order stable. Do not reorder arithmetic "for readability".

`ArchitectureTest` enforces the mechanical parts of this. `SimulationDeterminismTest` catches the
rest. Both live in `sim/src/test`.

## Nullability

Every package has a `package-info.java` carrying JSpecify's `@NullMarked`, so everything is
non-null by default. Mark the exceptions with `@Nullable`. NullAway enforces this at compile time
and a violation fails the build.

Framework-initialised fields (created in libGDX's `create()`, not the constructor) are the one place
`@SuppressWarnings("NullAway.Init")` is acceptable - see `PwtGame`.

## Clean code

- `var` only where the type is obvious from the right-hand side.
- No magic numbers in game logic or rendering. Tunables go in `assets/game.properties` (simulation)
  or `assets/graphic.properties` (rendering), loaded into a config record.
  Changing `graphic.properties` must never change what the game *does*.
- One-line Javadoc on public classes and methods. Minimal inline comments - the code explains
  itself; comments explain *why*. No `@author` tags.
- Formatting is not a matter of opinion: Spotless with palantir-java-format runs in the verify loop
  and rewrites the file. Never hand-format.
- Reuse the vocabulary in `docs/glossary.md`. One concept, one word, everywhere.

## Logging

`Log` (in `core`), a facade over `Gdx.app`, works before libGDX starts and in plain unit tests.

- `Log.info` - lifecycle: startup, shutdown, screen changes.
- `Log.debug` - initialisation detail and resolved configuration.
- `Log.error` - failures, always with the cause when there is one.

Resource-load failures log and fall back gracefully. They never crash the game.

## Tests

JUnit 5 + AssertJ + Mockito, plus ArchUnit for the architecture rules. Conventions - naming, shape,
when to mock, which existing class to copy - are in the `unit-tests` skill.

Two rules that are architectural rather than stylistic, so they live here:

- All `sim` logic must be testable without a graphics context. The module split makes this
  structural: rendering libraries are not on `sim`'s classpath.
- Write the general-level tests first, implement, then fill in the specific cases. The order is in
  `CLAUDE.md`; it is the house workflow, not a preference.

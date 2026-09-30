# Coding conventions

Banned APIs (below) are the index for the whole project. Per-entity cost rules live in
`docs/performance.md`; reproducibility rules in `docs/determinism.md`.

## Banned, and what to use instead

| Never | Use | Where | Caught by |
|---|---|---|---|
| `Math.random()`, `java.util.Random`, `ThreadLocalRandom` | `Rng` (SplitMix64); one stream per concern via `Rng.stream` | `sim`, `map` | `simulationDoesNotUseMathRandom`, `mapDoesNotUseMathRandom`, `simulationDoesNotUseUnseededRandomnessOrHashIteration` |
| `Math.sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `exp`, `expm1`, `log`, `log10`, `log1p`, `pow`, `cbrt`, `hypot` | `StrictMath` - bit-identical everywhere; `Math`'s are specified only to within 1 ulp. Better still: resolve the trig once at spawn and store it. **`Math.sqrt`, `abs`, `min`, `max`, `floor`, `ceil` and `round` are exactly specified and stay fine.** | `sim`, `map` | `simulationDoesNotUseNonReproducibleMath`, `mapDoesNotUseNonReproducibleMath` - identical lists |
| `System.currentTimeMillis()`, `nanoTime()`, `Instant.now()`, `LocalDate.now()` | Nothing. A tick count is the only time the simulation has. | `sim`, `map` | `simulationDoesNotReadTheWallClock`, `mapDoesNotReadTheWallClock` |
| Iterating a `HashMap`, `HashSet`, or anything with unspecified order | libGDX `ObjectMap`/`Array`, or sort explicitly first | `sim`, `map` | `simulationDoesNotUseUnseededRandomnessOrHashIteration`, `mapDoesNotUseUnseededRandomnessOrHashIteration` |
| Removing an entity anywhere but `ReaperSystem` | Mark it; `ReaperSystem` removes it | `sim` | `onlyTheReaperRemovesEntities` |
| `com.badlogic.*` of any kind | Plain Java - `map` depends on nothing | `map` | `mapKnowsNothingOfTheGameAroundIt` |
| Anything graphical: `SpriteBatch`, `ShapeRenderer`, `Texture`, `Gdx.*` | Publish state on a component; the client decides how to draw it (G0003) | `sim`, `map` | `simulationIsHeadless`, `simulationDoesNotTouchTheRunningApplication` |
| `setScreen` from inside a screen; a screen depending on `Game` | `GameContext.queueScreen` - the swap happens after the frame | `core` | `screensDoNotSwitchThemselves`, `screensCannotReachPastTheirContext` |
| `Gdx.*` in a class named `*Session`, `*Controls`, `*Selection` | Take input as values or one-method interfaces, so it tests without GL | `core` | `decisionLogicDoesNotTouchTheRunningApplication` |
| `new`, boxing, varargs, `Optional`, string concatenation, streams, capturing lambdas, iterators over a `List` | Reuse a scratch instance, index a libGDX `Array`, cache the view | inside a per-entity loop | nothing - `docs/performance.md` |
| Any log call, any level | Log outside the loop or not at all | inside a per-frame or per-entity loop | nothing |
| A nested loop over two entity collections (O(N²)) | `SpatialGrid` | gameplay interactions | nothing |
| A timer counted down in seconds (`remaining -= deltaTime`) | Whole ticks: a `...Ticks` field set once through `Ticks.of(seconds, tickSeconds)` where the duration enters from the config, taken down by one in `CooldownSystem`. A float tick is not exact; the residue ran 71 shipped durations a tick long. Continuous quantities - heat, bloat - stay float; a clock whose rate varies (the bleed-out, the stabilise) counts hundredths of a tick (`Vitality.TICK`), exact all the same | `sim` | `TicksTest`, `WoundClockTest` |
| `./gradlew` in the sandbox | `.claude/scripts/gradle.sh` - the wrapper cannot reach the network there | sandbox shell | a confusing `UnknownHostException` |
| `com.badlogic.gdx.Input*`, `gdx.controllers..` - polling a device | Input arrives as a per-tick value object (`CommanderInput`) | `sim` | `simulationDoesNotReadInputDirectly` |
| `Simulation.setCommanderInvulnerable` from a scenario | Nothing. God mode is a harness switch; pillar 2 says the commander can be killed. | `sim.scenario` | `scenariosCannotSwitchOnGodMode` |
| A magic number in game logic or rendering | `assets/sim/*.properties` (sim) or `assets/graphic.properties` (render), into a config record | everywhere | nothing |
| An invented asset path | A constant in `Assets` | `core` | nothing |
| Preview or incubator APIs, Structured Concurrency, Vector API | The finalized Java 25 feature, or nothing | everywhere | `--release 25` |
| Box2D, Spring, any IoC container, a service locator, an event bus | Circle-circle collision (ADR 0007); manual constructor DI; reaction components and spawned entities (G0004) | everywhere | review |
| Hand-formatting | `spotlessApply`; `verify.sh` runs it | everywhere | Spotless |

Anything not listed is allowed until someone has a reason. Add a row when a mistake is made twice, not
when it is imagined once.

**The two `NonReproducibleMath` rules are kept byte-identical** (widened 2026-09-12, each watched
failing against a probe). They ban every `java.lang.Math` method whose result is not exactly specified:
`sin cos tan asin acos atan atan2 sinh cosh tanh exp expm1 log log10 log1p pow cbrt hypot`. Adding a
method means adding it to **both**, and each file's javadoc says so.

## Language

Java 25, `--release 25`. Use the finalized features: records, pattern matching for `switch`/`instanceof`,
record patterns, unnamed variables (`_`), exhaustive switches, sealed types. Nothing needing
`--enable-preview`.

## Architecture

- Composition over inheritance. ECS and strategy objects, not deep hierarchies.
- Immutable `record`s for static configuration and definitions. Mutable pooled classes for runtime
  entities: mutate in place, never replace a pooled instance.
- `SimConfig` and its nested records have `with…` helpers. Use those; never write a record literal for
  a config that already exists.
- Manual constructor DI. Lombok's `@RequiredArgsConstructor` for the boilerplate.
- Small, cohesive types. One class per file. Don't over-engineer a small addition.

### Interfaces only at real boundaries

An interface earns its place where substitution, ownership or an external boundary is real: a seam the
client uses to keep `Gdx` out of a testable class, a strategy genuinely swapped at runtime, a module
boundary.

Do **not** introduce one because a class has several methods, to satisfy "program to interfaces", to
enable a hypothetical second implementation, or to make a test easier when a simpler seam exists.
Prefer concrete types inside a cohesive module. An interface with one implementation and no prospect of
a second is indirection with a maintenance bill.

Same for a test that seems to need a mock in `sim`: that is a design signal, not a testing problem -
see the `unit-tests` skill.

### The one exception to encapsulation

ECS components use **public mutable fields**, not accessors:

```java
public final class Position implements Component, Pool.Poolable {
    public float x;
    public float y;

    @Override
    public void reset() {
        x = 0f;
        y = 0f;
    }
}
```

`reset()` must clear **every** field. A pooled component that forgets one leaks state into the next
entity that reuses it, and that bug is very hard to find.

Components are data, read and written thousands of times per frame by systems that already own them.
Accessors add only indirection, and this is the idiom every Ashley codebase uses. Everywhere else -
pooled non-component runtime classes included - Lombok `@Getter`/`@Setter` plus a `reset()`.

## Nullability

Every package has a `package-info.java` with JSpecify's `@NullMarked`: non-null by default, exceptions
marked `@Nullable`. NullAway enforces it at compile time; a violation fails the build.

Framework-initialised fields (created in libGDX's `create()`, not the constructor) are the one place
`@SuppressWarnings("NullAway.Init")` is acceptable - see `PwtGame`.

## Clean code

- `var` only where the type is obvious from the right-hand side.
- One-line Javadoc on public classes and methods. Minimal inline comments - the code explains itself,
  comments explain *why*. No `@author`.
- **A comment is contract, invariant and a link** - what the thing promises, what must stay true, and
  the G-record or ADR that decided it. Not its history: "until", "used to", "what it replaced", the bug
  that prompted it and the alternatives turned down belong in the decision record and in git, where
  nobody pays for them on every read. A pillar is named, not re-argued; one sentence of *why* is
  enough when no record carries it. Why a number is tuned where it is belongs beside the number in
  `assets/sim/*.properties`; the record component says only what it means, its unit and what zero does.
- **A citation outlives the comment**: a G record, an ADR, or a doc that does not expire. Never a scope
  doc's numbering - `(D11)`, `upgrades S5`, `H3`, `(B)` - which is deleted when its work lands and leaves
  the comment pointing at nothing; the UI plan's `plan 7.2` only while that plan exists.
  `SourceCitationsTest` fails the build on one, and on any `docs/...md` path that does not exist.
- **Inserting a method above another orphans that method's javadoc**, and `-Werror` is on, so it fails
  the build. Check the method below the one you added.
- Reuse the vocabulary in `docs/glossary.md`. One concept, one word, everywhere.
- Changing `graphic.properties` must never change what the game *does*.

## Logging

`Log` (in `core`) is a facade over `Gdx.app`, working before libGDX starts and in plain unit tests.
Connections have their own: `NetLog` (in `net`), one file per instance - see `docs/architecture.md`. What
happens per message goes into its `NetTrace`, never into a log call.

- `Log.info` - lifecycle: startup, shutdown, screen changes.
- `Log.debug` - initialisation detail, resolved configuration.
- `Log.error` - failures, always with the cause when there is one.

Resource-load failures log and fall back gracefully. They never crash the game.

The ban is on logging **inside a per-frame or per-entity loop**, at any level - not on logging. Do not
delete an existing `Log.error` on the strength of that rule.

## Tests

JUnit 5 + AssertJ + Mockito, plus ArchUnit for the architecture rules. Naming, shape, when to mock,
which class to copy: the `unit-tests` skill.

Two rules that are architectural rather than stylistic:

- All `sim` logic must be testable without a graphics context. Structural, not aspirational: rendering
  libraries are not on `sim`'s classpath.
- Test order is the house workflow, in `CLAUDE.md`, not a preference.

Tests are evidence, not ceremony. Prefer ones that catch a behavioural regression, pin a deterministic
outcome, or protect an architectural boundary. Skip cases that exist only to move a number.

**Every ArchUnit rule here was watched failing against a deliberate probe before being trusted.** A new
one is not finished until you have seen it fail.

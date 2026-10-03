---
name: unit-tests
description: How tests are written in Project-W - naming, shape, assertions, when to mock. Use when writing, reviewing or refactoring any Java test in this repository.
---

# Unit tests

**Read the exemplar first. It outranks everything below.**

- `sim/src/test/java/pl/mzebrows/pwt/sim/system/motion/MovementSystemTest.java` - testing a system.
- `core/src/test/java/pl/mzebrows/pwt/client/ScenarioSessionTest.java` - testing client logic.
- `sim/src/test/java/pl/mzebrows/pwt/sim/ConfigFileTest.java` - plain logic and fallback paths.
- `sim/src/test/java/pl/mzebrows/pwt/sim/def/WholeTest.java` - a parameterized test.

Rules here cover only what those files cannot show.

## Naming

Name the behaviour, not the method: `movesAnEntityByItsVelocityEachStep`, not `testProcessEntity`.
A failing test should read as a sentence describing what broke. ECS systems have one entry point,
so method-derived names collide immediately.

## Shape

Separate arrange, act and assert with **blank lines**. No `// given` / `// when` / `// then`
comments - on a five-line test they outnumber the code.

```java
Simulation simulation = simulation();
Entity entity = simulation.spawn(0f, 0f, 60f, 0f, 1f);

simulation.step();

assertThat(entity.getComponent(Position.class).x).isCloseTo(1f, Offset.offset(1e-5f));
```

- AssertJ `assertThat` for everything. `SoftAssertions` when checking several fields of one object.
- Floats need an `Offset` - see `MovementSystemTest`. Exact equality only for values that were never
  arithmetic (a position that was set and not integrated).
- `var` only where the type is obvious from the right-hand side.
- Repeated literals and shared config become `private static final` constants, passed straight to
  the code under test. No local aliasing of a constant.
- Shared construction goes in a small private static factory (`simulation()` in `MovementSystemTest`)
  rather than mutable `@BeforeEach` fields, so tests stay independent by construction.
- Repeated assertion logic goes in private helpers at the bottom.
- Order in the class: tests, then data providers, then private helpers.

## Mocking - usually the wrong tool here

`sim` has no external collaborators: no I/O, no clock, no network, no services. **A test in `sim`
that seems to need a mock is telling you about a design problem, not a testing problem.** Use real
objects and a seeded `Rng`.

When a genuine collaborator does appear - most likely in `core`, behind an interface - use
`@ExtendWith(MockitoExtension.class)` at class level. Strict stubs, no manual init. Reach for
`verify` and `ArgumentCaptor` only when the interaction *is* the thing under test, never as a
default addition.

There is no mock-using test in the repository yet. The first one sets the pattern - write it
carefully, and add it to the exemplar list above.

## Testing the client

`core` has no GL context in tests and is not getting one. Anything that constructs a
`ShapeRenderer`, `SpriteBatch`, `BitmapFont` or `Texture` cannot be unit-tested here.

The boundary runs between deciding and drawing, in both directions:

- **Decisions are testable, and are tested.** What a key means, what a command does, how a selection
  wraps, how config parses and falls back. These classes never touch `Gdx`; they take their input as
  values or one-method interfaces - `KeyPresses`, a delta, a `CommanderInput` - so a test can supply
  them directly.
- **Drawing is not tested.** Screens, renderers and the HUD hold GL objects and turn already-decided
  state into draw calls. Keep them thin enough to review by eye, and verify them by running the game
  on Mateusz's machine.

`ClientArchitectureTest` enforces this rather than trusting it: no `Screen` may call `setScreen` or
depend on `Game`, and no class ending in `Session`, `Controls` or `Selection` may depend on `Gdx`.
All three rules were checked against deliberate violations when they were written; a rule nobody has
seen fail is not a guard.

**When a client bug turns out to be untestable, move the decision out of the drawing class - do not
reach for a headless backend.** The screen-switch crash of 2026-09-07 is the worked example: a
decision about when to swap screens was living in a method that also drew, so the only way to hit it
was to run the game, and the only symptom was the JVM dying with an access violation. Adding a
backend would have made that code reachable from a test; moving the decision made it *simple*, which
is worth more.

A headless backend would still be the right answer for one thing we do not test today: `Assets` and
anything else that goes through `Gdx.files`. That is a small, separate question - raise it when
something there actually breaks, not pre-emptively.

## Consolidation

Two tests belong in one `@ParameterizedTest` when they call the same method and their assertions
reduce to the same shape with different inputs - even when the inputs come from different validation
branches. Keep them separate when the assertions differ in intent: one checking a return value and
one verifying an interaction are not the same test.

Check this once before finalising a class.

Data feeder methods stay flat - arrays of plain values, no wrapper records or nested classes unless
the data genuinely needs them. No inline comments annotating `null`, `""` or an empty collection.

`WholeTest` is the model: one `@CsvSource` of plain values, beside the edge cases that read better as
tests of their own.

## Coverage

Happy path, plus the edge cases a caller could realistically hit: null and empty inputs, boundary
values, the error paths the code explicitly handles. `ConfigFileTest` is the model - it covers no file
at all, a missing value, an unreadable one, a key nothing has and a key given twice, because all of them
happen to a player's config file.

Skip cases that exist only to move a coverage number.

## Comments

No Javadoc on tests. Two exceptions: a helper doing something genuinely non-obvious, and a
class-level note explaining why a test matters more than it looks - see
`SimulationDeterminismTest`, which records that a failure there is a design bug rather than a flake.

## When something resists testing

Say so rather than building scaffolding around it. Static state, hidden construction, a constructor
doing I/O, anything needing a GL context inside `sim` - name the design issue, propose the smallest
change that would make it testable, and wait for a decision.

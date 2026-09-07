---
name: unit-tests
description: How tests are written in Project-W - naming, shape, assertions, when to mock. Use when writing, reviewing or refactoring any Java test in this repository.
---

# Unit tests

**Read the exemplar first. It outranks everything below.**

- `sim/src/test/java/pl/mzebrows/pwt/sim/system/MovementSystemTest.java` - testing a system.
- `core/src/test/java/pl/mzebrows/pwt/client/GamePropertiesTest.java` - plain logic and fallback paths.

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

## Consolidation

Two tests belong in one `@ParameterizedTest` when they call the same method and their assertions
reduce to the same shape with different inputs - even when the inputs come from different validation
branches. Keep them separate when the assertions differ in intent: one checking a return value and
one verifying an interaction are not the same test.

Check this once before finalising a class.

Data feeder methods stay flat - arrays of plain values, no wrapper records or nested classes unless
the data genuinely needs them. No inline comments annotating `null`, `""` or an empty collection.

There is no parameterized test in the repository yet either.

## Coverage

Happy path, plus the edge cases a caller could realistically hit: null and empty inputs, boundary
values, the error paths the code explicitly handles. `GamePropertiesTest` is the model - it covers
missing key, blank value, unparseable value and missing file, because all four happen in a shipped
build.

Skip cases that exist only to move a coverage number.

## Comments

No Javadoc on tests. Two exceptions: a helper doing something genuinely non-obvious, and a
class-level note explaining why a test matters more than it looks - see
`SimulationDeterminismTest`, which records that a failure there is a design bug rather than a flake.

## When something resists testing

Say so rather than building scaffolding around it. Static state, hidden construction, a constructor
doing I/O, anything needing a GL context inside `sim` - name the design issue, propose the smallest
change that would make it testable, and wait for a decision.

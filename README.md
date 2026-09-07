# Project-W

A top-down 2D arcade shooter in Java 25 and [libGDX](https://libgdx.com/), inspired by
*Triumph! War 2099* (Darksun Games, 2001).

## Modules

| Module | Contents |
|---|---|
| `sim` | The deterministic simulation - entities, components, systems, world state. Headless; no rendering, no assets, no I/O. |
| `core` | The libGDX application - rendering, input, assets, screens, config. Depends on `sim`. |
| `lwjgl3` | Desktop launcher. |

Dependencies point one way: `lwjgl3` -> `core` -> `sim`.

## Requirements

JDK 25. Gradle comes from the wrapper.

## Running

```
./gradlew lwjgl3:run      # start the game
./gradlew build           # compile, lint, test everything
./gradlew spotlessApply   # reformat
./gradlew :core:packTextures   # pack assets-raw/ into assets/atlas/
./gradlew lwjgl3:jar      # runnable jar in lwjgl3/build/libs
```

## Configuration

Tunables are external, never literals in code:

- `assets/game.properties` - simulation. Changing these changes what the game does.
- `assets/graphic.properties` - rendering. Changing these must never change what the game does.

## Quality gates

Every build runs Spotless (palantir-java-format), Error Prone, NullAway in JSpecify mode, ArchUnit
architecture rules, and the JUnit 5 test suite.

## Working with Claude

`CLAUDE.md` is the entry point; `docs/` holds the detail and `docs/adr/` the reasoning behind the
structural decisions. Claude builds and tests through `.claude/scripts/verify.sh`, which runs the
real Gradle build inside its sandbox - see `docs/workflow.md`.

# Project-W

A top-down 2D arcade shooter in Java 25 and [libGDX](https://libgdx.com/), inspired by
*Triumph! War 2099* (Darksun Games, 2001).

## Modules

| Module | Contents |
|---|---|
| `map` | The ground a level is played on - tile grid, terrain, regions, generation. Depends on nothing: not libGDX, not Ashley, not `sim`. |
| `sim` | The deterministic simulation - entities, components, systems, world state. Headless; no rendering, no assets, no I/O. |
| `core` | The libGDX application - rendering, input, assets, screens, config. |
| `lwjgl3` | Desktop launcher. |

Dependencies point one way: `lwjgl3` -> `core` -> `sim` -> `map`. `map` owns the field; `sim` owns
what stands on it and what happens over time.

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
architecture rules across all three source modules, and the JUnit 5 test suite. Warnings are errors.

## Working with Claude

`CLAUDE.md` is the entry point; `docs/` holds the detail and `docs/adr/` the reasoning behind the
structural decisions. Claude builds and tests through `.claude/scripts/verify.sh`, which runs the
real Gradle build inside its sandbox - see `docs/workflow.md`.

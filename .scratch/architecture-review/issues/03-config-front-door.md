# 03 - One config front door

Status: needs-triage

Strength: Strong. Dependency: in-process. Report candidate 4. Pairs with 04.

## Problem

Four loaders do the same jobs their own way - blank text means defaults, a `Parsed` record, logging the
problems, and a `Shipped` holder with `VALUES` plus `bindStrictly`:

- `core/.../client/SimConfigLoader`, holder at `sim/.../SimConfig:243`
- `core/.../client/NetConfigLoader`, holder at `net/.../NetConfig:84`
- `core/.../client/GraphicConfig`, holder at `:355`
- `core/.../client/ControlsConfig`, holder at `:334`

Three Gradle codegen tasks do the same thing (`sim`, `net/build.gradle:60`, `core/build.gradle:39`), and
four `Shipped*Test` suites repeat the same three tests: names every key, is the default, a misspelt key
keeps the shipped value.

`GameProperties.text()` is the file reader all four share - keep it. But `GameProperties` is also a second
parser (`java.util.Properties`, with escape and separator rules unlike `ConfigBinder.parse`): its
`getInt/getLong/getFloat/getBoolean` are called only by `GamePropertiesTest`, and the only production
caller of the rest is `UiStrings` -> `getString`.

Files are re-read: `JoinScreen:174` passes `context.netConfig()` and also `NetConfigLoader.latencySteps()`,
which re-reads and re-logs the file; `UiStrings.load()` runs in three screens (`MenuScreen:42`,
`JoinScreen:36`, `ScenarioScreen:164`).

## Direction

One module binds any file to its record the same way, extending `ConfigBinder` rather than standing beside
it. The four `Shipped*Test` suites become one.

## Watch out

`GameProperties` is in CLAUDE.md's reuse list and `GamePropertiesTest` is an exemplar in the `unit-tests`
skill; both change if it goes.

## Comments

# 03 - One config front door

Status: done

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

**Online contract (grilling, with 11).** A config has one of three scopes - **world config**, **session
config**, **local config** (glossary). What the front door owes the network:

- **It keeps the text it bound**, beside the record. The host ships that text, never a fresh read:
  `OnlineSession.host:120` ships `SimConfigLoader.shipped()`, which reads the files again, while the seed and
  `tickSeconds` it pairs with it come from the `SimConfig` loaded at start (`PwtGame:48`). A file edited
  between the two makes the sequencer pace by one `tickSeconds` and every world count by another. Same
  defect as `JoinScreen:174`'s re-read. Shipped comment-stripped, as now.
- **It names its source when it logs problems.** A joiner parsing the host's world config logs one line -
  `host's world config: N problem(s), the shipped value stands` - not each problem against its own
  `sim/*.properties`. The host logged each already, and the build id says the joiner's parse finds the same.
- **Session config is not its job** - 11 carries it in `WELCOME` as values, not text.

**Done ahead of the front door** (with 11): `SimConfigLoader.loaded()` keeps the text beside the config, the
host ships it through `GameContext.worldConfigText()`, and `SimConfigLoader.shipped()` is gone;
`ScenarioWorlds` parses the host's text with `parseFromHost`, one line for its problems. The front door
inherits both.
- 2026-10-03: Taken (system-modules 06). Gates `.scratch/client-packages/`, which splits `core/client` by layer
  once these are in; order 2nd, with 04.
- 2026-10-03: Done, with 04, in four commits. `ConfigFile<T>` (sim, beside `ConfigBinder`) is the one way a
  config file is read: shipped text compiled in and bound strictly once, a player's text bound against it,
  every problem named. `SimConfig.FILE`, `NetConfig.FILE`, `GraphicConfig.FILE`, `ControlsConfig.FILE`; their
  own `parse`/`Parsed`/`Shipped` holders are gone. `ConfigLoader` (core) reads a file off the classpath,
  binds it and logs the problems in one format. The four `Shipped*Test` suites are `ShippedConfigFilesTest`,
  parameterised over the four files; what is particular to one stays beside it (`ShippedConfigTest` for the
  sim files' list, keys-once and comment stripping; `ControlsConfigTest`, `GraphicConfigTest`).
  `JoinScreen` no longer re-reads `network.properties`. `GameProperties` is the file reader only; `UiStrings`
  reads `ui.properties` through `ConfigBinder.parse`, `UiStringsTest` proving every word reads as
  `java.util.Properties` read it. The `unit-tests` exemplar is `ConfigFileTest`. The codegen tasks stay three
  - one per module, which is where each shipped text has to be compiled.

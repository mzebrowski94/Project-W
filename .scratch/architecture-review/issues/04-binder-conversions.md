# 04 - Let the binder convert; drop the mirror records

Status: done

Strength: Strong. Dependency: in-process. Report candidate 5. Pairs with 03.

## Problem

`docs/workflow.md` says a tunable is "a component here and a line there". That holds for `sim` (2 files)
but not the client, where each value is hand-copied a second time into a differently shaped record:

- `core/.../client/GraphicFile` (190 lines) and `ControlsFile` (82) mirror the files as strings.
- `GraphicConfig.of`/`hud()` (`:158-352`; `hud()` takes 27 positional arguments at `:252`) and
  `ControlsConfig.of` (`:256-316`) copy them into the real records; `HudStyle` has 27 positional
  components.
- One new HUD float touches 6 files: the properties, `GraphicFile`, `GraphicConfig.hud()`, `HudStyle`, the
  consumer, and `ConfigDefaultsTest:47` (a positional `new HudStyle`).
- One new world mark touches 7: a new `XMark` record, `GraphicFile`, the `GraphicConfig` component and
  `of()`, the `WorldRenderer` field, the 14-argument constructor call at `ScenarioScreen:188-202`, and the
  properties.
- `ConfigDefaultsTest:177-250` holds literal copies of shipped numbers - the drift commit `c8bc320` set
  out to remove.

The cause: `ConfigBinder` cannot convert a `Color` or a key name, so `colours()` (`GraphicConfig:141`) and
`names()` (`ControlsConfig:235`) run as string pre-passes.

## Direction

Give the binder a conversion for those two types; the `*File` mirrors and their `of()` mappings (about
400 lines) go, and `ConfigDefaultsTest` reads the defaults rather than literals.

## Comments
- 2026-10-03: Taken (system-modules 06). Gates `.scratch/client-packages/`, which splits `core/client` by layer
  once these are in; order 2nd, with 03.
- 2026-10-03: Done, with 03. The binder takes `Conversions` (type to reading); core passes `Color` (hex),
  `KeyCode` and `MouseButton` (by name). `GraphicConfig` and `ControlsConfig` are shaped as their files and
  bound directly: `GraphicFile`, `ControlsFile`, `HudStyle` and the `of()` mappings are gone, and every mark
  binds as its own block (`CrateMark.color` under `@ConfigKey("dotColor")`). The layout, the palettes, the
  ground's detail and the toast's pace are methods; `ScenarioScreen` builds its palette once. A pad action is
  `fire` and `fireTrigger`, as the file has it; `PadBinding`, `KeyAxis`, `Feel` and `Hardware` are gone.
  `ConfigDefaultsTest` holds no copy of a shipped number. No key in `assets/` changed.

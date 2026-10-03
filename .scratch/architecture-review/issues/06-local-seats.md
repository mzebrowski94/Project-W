# 06 - Local seats and top bar out of ScenarioScreen

Status: done

Strength: Strong. Dependency: in-process; devices through ports and adapters. Report candidate 7.

## Problem

`ScenarioScreen` (646 lines) says it "does as little thinking as possible" (`:49`), yet decides which
**seat** drives the **top bar** and whose input is masked:

- `play.slotOf(seat)` loops in `draft()` `:373`, `crate()` `:401`, `followCrate` `:437`, `playersIn`
  `:452`, `seatPanels` `:515`, `onALocalPad` `:536`, `aimAhead` `:553` - 14 calls.
- `crate()` `:420-429` rewrites players' input, suppressing "acting" for other seats while a bar is open.
- The "one bar per machine" rule is spread over `InputReader:167-185`, `ScenarioScreen`, ui
  `CrateBar.intruder` and ui `DraftPause`.

`InputReader` (280 lines) "decides nothing", yet `read()` `:122-209` routes draft and crate keys, detects
the rising edge of a crate take (`wasActing`), special-cases the keyboard's crate close, and masks to
neutral - while calling `Gdx.input` directly (`:83`, `:86`, `:132`, `:212`). `PlayMenus.update` takes the
concrete `InputReader` (`PlayMenus:98`). One binding, `crateClose`, crosses nine modules.

None of it is tested: no `InputReaderTest`, no `PlayMenusTest`, and the screen runs only on the desktop.
`FakeGamepads` already exists to build on.

## Direction

One pure module owns a machine's seats and its top bar - who drives it, whose input is masked - taking the
readout, the keys as data and `slotOf`. Device state reaches it through polled adapters. Tests beside
`DraftBarTest` and `CrateBarTest`.

## Constraints

ADR 0011: polled device state, never an event-stream port. ADR 0015: decisions about what the player is
shown live in `ui`, so the module probably does too.

## Comments
- 2026-10-03: Taken (system-modules 06). Gates `.scratch/client-packages/`, which splits `core/client` by layer
  once these are in; order 3rd. Open at its start: whether the seat and top-bar module lives in `ui` or `core` - ask first.
- 2026-10-03: Done. Grilled first: the module lives in `ui` (ADR 0015), named `TopBarSeats`, and takes the
  bar and the masking but not the other seat loops. It owns the `DraftBar`, the `CrateBar` and the
  `DraftPause`; `route` turns the drafting or crating seat's input into card keys (the take on the act press's
  rising edge, the keyboard's crate key) and holds every seat still under a menu, `work` works the bars and
  drops another seat's act on a crate in reach while the bar is taken, `follow` keeps them true to the world
  and shuts a second local crate. `SeatedPlay` is its port; `ScenarioPlay` extends it. `DraftKeys` and
  `CrateKeys` moved to `ui` with their tests; the two that read `controls.properties` went to
  `ControlsConfigTest`. `InputReader` reads devices and says which seats pressed their draft key and whether
  the keyboard's crate key went down; `ScenarioScreen` lost `draft()`, `crate()`, `followCrate()` and
  `playersIn()`, and ignores zoom under a menu as `InputReader` did. The keyboard's seat is
  `TopBarSeats.KEYBOARD`, which `PlayerSeats.KEYBOARD_SLOT` names. Left as they were, by choice: the slot-of-
  seat loops in `seatPanels`, `aimAhead` and `onALocalPad`, and `PlayMenus.update` taking the concrete
  `InputReader`.

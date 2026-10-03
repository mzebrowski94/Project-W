# Client packages - map

Split `core/src/main/java/pl/mzebrows/pwt/client` (91 classes, flat) into fenced packages by layer, after
the open `architecture-review` tickets in `core` have reshaped it. Grilled 2026-10-03 (system-modules
ticket 06). Same words as `sim`: a **fenced package**, a **port** (ADR 0020).

## Notes

What the code said on 2026-10-03, measured with a throwaway copy of `system-graph.py` (comments and strings
stripped, classes only).

- **Public by habit.** 64 of 91 classes are public; outside `client` only four are needed - `PwtGame`,
  `LaunchOptions` (`lwjgl3`), `GraphicConfig`, `SystemPointer`. Package-private fences nothing here today.
- **Hubs.** `Log` 19 users, `ControlsConfig` 15 (input, hints, screens, gamepads), `Hud` 12, `UiStrings`
  10, `PlayerSeats` 10, `HudStyle` 9. `ScenarioScreen` (667 lines) uses 31 client classes;
  `GraphicConfig` wires every world mark.
- **Clusters, by what they import:** world drawing (`WorldRenderer`, twelve `*Mark`s, `Palette`,
  `StateTint`, `TerrainRenderer`, `TerrainPalette`, `GroundDetail`); screen space (`Hud`, `HudStyle`, the
  five painters, `RankPips`, the fonts, `PixelViewport`, the text classes `UiStrings`, `CardText`,
  `DispatchText`, `ActionPrompts`, the `*Hints`); devices and bindings (`Gamepad*`, `Pad*`, `Key*`,
  `*Keys`, `InputReader`, `NavInputReader`, `PlayerSeats`, `ControlsConfig`/`File`); play (`ScenarioPlay`,
  `ScenarioSession`, `OnlineSession`, `ScenarioWorlds`, `Invitation`, `LocalMachineId`, `HarnessCommand`,
  `OverlayMode`); config (`GameProperties`, `SimConfigLoader`, `NetConfigLoader`, `GraphicConfig`/`File`);
  and the application (`PwtGame`, `GameContext`, the three screens, `PlayMenus`, `Assets`, `Log`).
- `ClientArchitectureTest` fences by **name suffix** (`*Session`, `*Controls`, `*Selection`, `*Director`
  may not touch `Gdx`), not by package.
- 34 test classes, all in package `client`.

## Decisions

1. **The review tickets first, then the split** - sim's ticket 02 reasoning: deepen once, then move.
   `architecture-review` 05 (a likely live bug - a pick made before `PLAYING` is dropped), then 03 + 04 (one
   config front door; the `*File` mirrors go), then 06 (seats and top bar out of `ScenarioScreen` and
   `InputReader`), then 09 (the overlay reads the readout). Each its own session.
2. **By layer**, not by screen and not decides-versus-draws: `core` is adapters, and its adapters are its
   layers. Measured and placed in decision 8.
3. **ADR 0020's shape**: package-private by default, declared ports, rules in `ClientArchitectureTest`
   that fail on zero classes. The `Gdx`-free rule moves from name suffixes to packages. Recorded as a new
   ADR amending 0020's *Scope* ("Not `core`").

The sketch that stood here is replaced by decision 8's [`split.txt`](split.txt).


4. **Review 05 done** (2026-10-03): `HeldEvents` is new in `client` - the `play` layer's.
5. **Reviews 03 + 04 done** (2026-10-03): `GraphicFile`, `ControlsFile` and `HudStyle` are gone; new are
   `ConfigLoader` (the `config` layer's) and `KeyCode`, `MouseButton` (the `input` layer's). `ConfigFile` lives
   in `sim`.
6. **Review 06 done** (2026-10-03): `DraftKeys` and `CrateKeys` left `client` for `ui.hud`, beside the new
   `TopBarSeats`; `InputReader` is the `input` layer's device reader and decides nothing about the bar.
7. **Review 09 done** (2026-10-03): `Drawn` is new (the `draw` layer's); `DebugOverlay` reads the readout and
   no longer touches `Gdx`. Every review ticket that gated this effort is in - ticket 01 can run.
8. **The split** (ticket 01, approved by Mateusz 2026-10-03, all four points as recommended): [`split.txt`](split.txt),
   checked by `system-graph.py --root core/src/main/java/pl/mzebrows/pwt/client --split`; the reasoning,
   the public list, where every test goes and the libGDX rule by package are in
   [01's answer](issues/01-measure-and-split.md#answer). Against the sketch: a config record lives with its
   layer (`GraphicConfig` in `draw`, `ControlsConfig` in `input`); `LaunchOptions`, `HarnessControls`,
   `LocalMachineId` in the root; `SystemPointer` in `hud`. 14 ports, 35 entries, layers acyclic. The `Gdx`
   rule is by package (`play`, `config` no libGDX; `draw`, `hud` no devices; `input` no drawing) plus the
   suffix rule kept for the decision classes outside `play`. `ConfigDefaultsTest` splits three ways.

9. **Ticket 02 done** (2026-10-03): every client class is in its layer or the root, behind the fences, and
   ADR 0021 is in `docs/adr/` ([02's answer](issues/02-fence-and-move.md#answer)). The effort is complete.

## Fog

- Where 06's seat and top-bar module lives: `ui` (ADR 0015 says decisions about what the player is shown
  do) or `core`. First question of that ticket.
- ~~Whether the screens stay in the root~~ - they do (decision 8).
- ~~Whether `Log` and `Assets` are a package's ports or the root's~~ - the root's, with `LaunchOptions`
  (decision 8).

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
   layers. Sketch below; ticket 01 measures it again once the review tickets are in.
3. **ADR 0020's shape**: package-private by default, declared ports, rules in `ClientArchitectureTest`
   that fail on zero classes. The `Gdx`-free rule moves from name suffixes to packages. Recorded as a new
   ADR amending 0020's *Scope* ("Not `core`").

Sketch - to be replaced by ticket 01's measurement:

```
client/            PwtGame, GameContext, Assets, Log, the screens, PlayMenus
client/input/      Gamepad*, Pad*, Key*, *Keys, *Controls, InputReader, NavInputReader, PlayerSeats
client/draw/       WorldRenderer, *Mark, Palette, StateTint, Terrain*, GroundDetail, LocalAim, CameraDirector
client/hud/        Hud, HudStyle, *Painter, RankPips, fonts, PixelViewport, LetterboxViewport, text, *Hints
client/play/       ScenarioPlay, ScenarioSession, OnlineSession, ScenarioWorlds, Invitation, HarnessCommand ...
client/config/     GameProperties and what 03 + 04 leave of the loaders
```

## Fog

- Where 06's seat and top-bar module lives: `ui` (ADR 0015 says decisions about what the player is shown
  do) or `core`. First question of that ticket.
- Whether the screens stay in the root or form a package of their own.
- Whether `Log` and `Assets` are a package's ports or the root's.

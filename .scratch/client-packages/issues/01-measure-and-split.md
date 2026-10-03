# 01 - Measure the client and choose the split

Type: research

Blocked by: architecture-review 05, 03, 04, 06, 09

## Question

Which layer packages, holding which classes, leave the fewest ports - and what does each port carry?

## Read first

`.scratch/client-packages/map.md`, `docs/adr/0020-systems-live-in-fenced-packages-by-domain.md`,
`.scratch/system-modules/issues/03-measure-system-graph.md` (*Answer* - the same deliverables, for `sim`),
`ClientArchitectureTest`.

## Settled

By layer; package-private by default; ports declared; the review tickets are in.

## Deliver

1. `system-graph.py` taught a second root (`--root core/src/main/java/pl/mzebrows/pwt/client`), not a copy.
2. A split file beside this map, checked by the script; every public class and why.
3. Where each of the client tests goes, and which reach package-private code in two packages.
4. The `Gdx`-free rule by package: which packages may touch `Gdx`, devices or GL.
5. Every hard-coded path: `ClientArchitectureTest`, `docs/architecture.md` (*core (client)*), the
   `unit-tests` skill's exemplars, `lwjgl3`.

## Done when

The split is in `map.md` *Decisions*, approved by Mateusz.


## Answer

Measured 2026-10-03 on the tree after review tickets 03-06 and 09. **Approved by Mateusz 2026-10-03**, all
four points of *Left for Mateusz* as recommended.

### 1. The graph

`system-graph.py --root DIR` reads any package root; the default is still `sim.system`, whose output is
unchanged but for its group labels (now real package names: `sim` for the root, `ui.hud` for `ui`). What it
learned on the way holds for both roots:

- **Outside users** are every main source file in every module not under the root, grouped by package,
  where `sim` had a hand-kept list.
- **A name after a dot, or a type the file declares inside itself, is not a use** of the top-level class
  of that name: `GraphicConfig.Hud`, a nested record, read as a use of `Hud`. `sim`'s output is identical
  either way.
- **`entry` beside `port`.** A class only the root's own classes use is an entry point - public as a
  system is, because the root builds and drives it - and not a port. The screens construct nearly every
  renderer and painter; without the distinction every one of them reads as a port.
- What each class touches of libGDX: `Gdx`; `GL` (g2d, glutils, `GLxx`, textures, viewports, assets);
  `device` (input processors, controllers, lwjgl); `keys` (the `Input.Keys` tables, no device). `Color`,
  math and collections are plain library. And the test fixtures, with their users.

### 2. The split (`../split.txt`)

| Package | Classes | Ports (used by a sibling) |
|---|---|---|
| `client` (root) | `PwtGame`, `GameContext`, `MenuScreen`, `JoinScreen`, `ScenarioScreen`, `PlayMenus`, `HarnessControls`, `Log`, `Assets`, `LaunchOptions`, `LocalMachineId` | `Log`, `Assets`, `LaunchOptions`; `LocalMachineId` until 02 |
| `input` | `Gamepad`, `GamepadAxis`, `GamepadButton`, `GamepadSource`, `GdxGamepads`, `ChosenGamepads`, `LatchedGamepad`, `PadPolicy`, `PadControls`, `PadLabels`, `KeyCode`, `MouseButton`, `KeyControls`, `KeyHolds`, `KeyPresses`, `OrderKeys`, `ViewControls`, `PageControls`, `MenuSelection`, `InputReader`, `NavInputReader`, `PlayerSeats`, `ControlsConfig` | `ControlsConfig`, `PlayerSeats`, `PadLabels`, `KeyCode` |
| `draw` | `WorldRenderer`, the eleven `*Mark`s, `Palette`, `StateTint`, `TerrainRenderer`, `TerrainPalette`, `GroundDetail`, `LocalAim`, `CameraDirector`, `Drawn`, `LetterboxViewport`, `GraphicConfig` | `GraphicConfig`, `Palette` |
| `hud` | `Hud`, the five painters, `RankPips`, `GlyphFont`, `GlyphTable`, `PixelFont`, `UiFont`, `PixelViewport`, `SystemPointer`, `UiStrings`, `CardText`, `DispatchText`, `ActionPrompts`, `CrateHints`, `DraftHints`, `PageHints`, `DebugOverlay` | - |
| `play` | `ScenarioPlay`, `ScenarioSession`, `OnlineSession`, `ScenarioWorlds`, `Invitation`, `HarnessCommand`, `OverlayMode`, `HeldEvents`, `JoinChoices` | `OverlayMode` |
| `config` | `GameProperties`, `ConfigLoader`, `SimConfigLoader`, `NetConfigLoader` | `ConfigLoader`, `GameProperties`, `SimConfigLoader` |

**14 ports (13 after 02), 35 entries, `PwtGame` public for the launcher, 40 classes package-private.**
Today 69 of 90 are public.

**The layers lean one way:** `config` <- `input` <- `play` <- `hud`, and `draw` <- `hud`; `hud` reads every
other layer (the hints name bindings, the bars are styled and coloured by `draw`, the panel is laid out by
`OverlayMode`) and nothing reads `hud` but the root. Nothing points back up but the root's ports - the shape `sim` has
with its root.

What the measurement moved against the sketch, each for a counted reason:

- **A config record lives with its layer.** `GraphicConfig` in `config` wires every mark, so **fourteen
  `draw` classes become ports** for it alone and `config` depends on `draw`. In `draw` they are
  package-private and `GraphicConfig` is its port. `ControlsConfig` in `input` the same way: it is made of
  `KeyCode`, `GamepadButton`, `MouseButton`. What stays in `config` is the front door (`ConfigLoader`,
  `GameProperties`) and the two loaders that feed `sim` and `net`.
- **`LaunchOptions` -> root.** In `config` it names a `PadPolicy`, and `input` loads through `config`:
  a cycle. It is the application's - the launcher parses it, `PwtGame` and `GameContext` hold it - and
  `OnlineSession` names its log after it, which makes it a root port.
- **`HarnessControls` -> root**, beside `ScenarioScreen`, its one user. In `input` it makes a
  `HarnessCommand` while `play` reads `PlayerSeats`: a cycle. In `play` it imports `Input.Keys`, the one
  libGDX name left there. In the root it costs no port at all.
- **`LocalMachineId` -> root.** It reads libGDX preferences (`Gdx.app`). Its one user is
  `OnlineSession.host`/`.join`; 02 hands the id in from the two screens that call them (one parameter
  each), and it stops being a port.
- **`SystemPointer` -> `hud`.** It is hidden while `Hud` draws the reticle; its users are `Hud`,
  `PwtGame` and `lwjgl3`'s `GlfwPointer`. In `input` it was a port for `Hud` alone.
- **`PlayMenus` stays with the screens** - `ScenarioScreen` is its one user, and in `hud` it would make
  `InputReader`, `NavInputReader` and `ScenarioPlay` ports. **`JoinChoices` -> `play`**, beside
  `Invitation` (G0038's two halves). **`DebugOverlay`, `ActionPrompts`, `DispatchText` -> `hud`**: words the
  player reads.

**Public, and why** (each port's users are in `split.txt`'s output):

- `Log` - every layer logs: 12 users in 5 packages. `Assets` - `UiFont` and `UiStrings` read its path
  constants; `PwtGame` loads and disposes it, and its atlas is the world's, not the bar's.
  `LaunchOptions` - see above.
- `ControlsConfig` - the hints name the bound keys. `PlayerSeats` - the hints and every `play` class key
  by seat. `PadLabels`, `KeyCode` - the hints say what a button is called.
- `GraphicConfig` - every painter is styled by it. `Palette` - the bars draw a player in his colour.
- `OverlayMode` - `TopBarPainter` lays its panel out by it.
- `ConfigLoader` - `ControlsConfig` and `GraphicConfig` load through it. `GameProperties` - `UiStrings`
  reads its file through it. `SimConfigLoader` - `ScenarioWorlds` parses the host's text with it.
- Outside `client`, `lwjgl3` needs `PwtGame`, `LaunchOptions`, `GraphicConfig` (`Lwjgl3Launcher`) and
  `SystemPointer` (`GlfwPointer`).
- **Entries (35)**, what `PwtGame` and the screens build and call: `input` - `InputReader`,
  `NavInputReader`, `GdxGamepads`, `ChosenGamepads`, `GamepadSource`, `PadPolicy`, `KeyHolds`,
  `KeyPresses`, `PageControls`; `draw` - `WorldRenderer`, `TerrainRenderer`, `CameraDirector`, `Drawn`,
  `LocalAim`, `LetterboxViewport`; `hud` - `Hud`, the five painters, `UiStrings`, the three hints,
  `ActionPrompts`, `DispatchText`, `DebugOverlay`, `SystemPointer`; `play` - `ScenarioPlay`,
  `ScenarioSession`, `OnlineSession`, `JoinChoices`, `HarnessCommand`; `config` - `NetConfigLoader`.
  For the client ADR 0020's "its systems and its ports" reads "its entries and its ports", both named in
  `package-info.java`; the fence can tell them apart (an entry's users are all in the root).

**The root's fence.** What the layers use of the root is `Log`, `Assets`, `LaunchOptions` and, until 02,
`LocalMachineId`. Nothing uses a screen, `PwtGame`, `GameContext`, `PlayMenus` or `HarnessControls` from a
layer package.

### 3. Where the tests go

Beside what they test. Every test reaches package-private code in at most one package, but one.

- `client` (test root): `ClientArchitectureTest`, `DecisionRecordsTest`, `SourceCitationsTest` (the last
  two read docs and module paths, no class), `HarnessControlsTest` (follows `HarnessControls`).
- `input`: `ControlsConfigTest`, `MenuSelectionTest`, `OrderKeysTest`, `PadControlsTest`,
  `PlayerSeatsTest`, `ViewControlsTest`, `LaunchOptionsTest` (it drives `ChosenGamepads` through
  `FakeGamepads`; `LaunchOptions` is a port); fixtures `FakeGamepad` and `FakeGamepads`, whose three users
  are all here, so they stay package-private.
- `draw`: `CameraDirectorTest`, `DrawnTest`, `GraphicConfigTest`, `LocalAimTest`, `TerrainPaletteTest`.
- `hud`: `CardTextTest`, `DebugOverlayTest`, `DispatchTextTest`, `PixelFontTest`, `TopBarPainterTest`,
  `UiFontTest`, `UiStringsTest`.
- `play`: `DesyncReportRunnerTest`, `HeldEventsTest`, `InvitationTest`, `JoinChoicesTest`,
  `OnlineSessionTest`, `ScenarioSessionTest`, `ScenarioWorldsTest`.
- `config`: `GamePropertiesTest`, `ShippedConfigTest`, `ShippedConfigFilesTest` (it reaches only the
  `ControlsConfig` and `GraphicConfig` ports).

**The hard case: `ConfigDefaultsTest`** reaches `StateTint`, `TerrainPalette` (`draw`) and `GamepadButton`
(`input`), none of them ports. It is three tests in one file. Proposed: the graphic cases (the fallback,
the alert, the palettes, the tint) join `GraphicConfigTest` in `draw`; `drivesPagesWithEnterEscape...`
joins `ControlsConfigTest` in `input`; the sim, net and graphic-and-controls fallbacks stay as
`ConfigDefaultsTest` in `config`. No class made public for a test.

### 4. libGDX by package

| Package | Touches today | Rule proposed |
|---|---|---|
| root | `Gdx`, GL, devices, keys - the screens, `PwtGame`, `Assets`, `Log`, `LocalMachineId`, `HarnessControls` | anything |
| `input` | `Gdx.input` (`InputReader`, `NavInputReader`), controllers (`GdxGamepads`), key tables; `InputReader` imports `Viewport`, to unproject the pointer | no drawing: no g2d, glutils, `GLxx`, `Texture`, `Pixmap`, `ScreenUtils` |
| `draw` | GL, and `Gdx.gl` for blending (`WorldRenderer`) | no devices: no `Input`, `InputProcessor`, controllers |
| `hud` | GL, `Gdx.graphics` for the back buffer (`Hud`), `Gdx.files` for the font (`UiFont`) | no devices |
| `play` | nothing | **no libGDX at all** (Ashley is not libGDX) |
| `config` | nothing | **no libGDX at all** |

The suffix rule today guards `*Session`, `*Controls`, `*Selection` and `*Director`. By package, `play`
covers every `*Session`. But four `*Controls` and `MenuSelection` sit in `input`, `HarnessControls` in the
root and `CameraDirector` in `draw`, and none of those packages can be `Gdx`-free: `InputReader` reads the
devices, `WorldRenderer` sets GL blending, the screens are the application. Package rules alone drop the
guard on seven decision classes, five of them tested - Mateusz's call (*Left for Mateusz*).

### 5. Hard-coded paths

- `ClientArchitectureTest`: `@AnalyzeClasses(packages = "pl.mzebrows.pwt.client")` already takes
  subpackages; the suffix rule becomes the package rules; new `FENCED`, `PORTS` and entries; the two
  screen rules (`areAssignableTo(Screen)`) and the `Upgrades`/`Drafts` rule need nothing.
- `core/build.gradle`, `generateShippedClientConfig`: writes `ShippedGraphicConfig` and
  `ShippedControlsConfig` into `pl/mzebrows/pwt/client`, package-private. Each must go to its record's
  package - `client.draw`, `client.input` - so its map gains a package per file. They are not under
  `src/`, so `move-classes.py` never sees them.
- `.claude/scripts/component-index.py`: `CORE.glob("*.java")` is flat - becomes `rglob`, or every client
  reader of a component drops out of `docs/components.md` unnoticed.
- `docs/architecture.md`, *core (client)* (line 250 on): a list of classes, which becomes the package
  table (02, step 3); line 274 states the suffix split.
- `unit-tests` skill, line 11: `core/src/test/java/pl/mzebrows/pwt/client/ScenarioSessionTest.java` ->
  `.../client/play/`.
- `lwjgl3`: `GlfwPointer` imports `client.SystemPointer` (-> `client.hud`), `Lwjgl3Launcher` imports
  `GraphicConfig` (-> `client.draw`); `LaunchOptions` and `PwtGame` stay. `move-classes.py` rewrites both.
- Nothing to change: `UiArchitectureTest`, `MapArchitectureTest` and ADR 0015 (`pl.mzebrows.pwt.client..`
  takes subpackages), `system-graph.py` (the root is an argument), `DecisionRecordsTest` (module paths),
  `docs/ui/ui_and_graphics_action_plan.md` ("`client` (existing, flat)" - a plan of its day).
- The new ADR is **0021**, amending 0020's *Scope* (02, step 3).

### Settled with Mateusz

1. The split above, with its moves against the sketch: `GraphicConfig` -> `draw`, `ControlsConfig` ->
   `input`, `LaunchOptions`, `HarnessControls` and `LocalMachineId` -> root, `SystemPointer` -> `hud`.
2. The `Gdx` rule: the package rules plus the suffix rule kept for the decision classes outside `play`
   (recommended), or the package rules alone.
3. `ConfigDefaultsTest` split three ways (recommended), rather than three classes made public.
4. The fog: the screens stay in the root, and `Log` and `Assets` are its ports (recommended) - or a
   `screen` package of its own, which would make `PlayMenus`' and `HarnessControls`' users ports too.

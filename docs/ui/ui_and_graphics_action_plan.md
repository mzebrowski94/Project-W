# UI and graphics - action plan

**This doc expires.** It covers one run of work - the letterboxed screen, the menus, the HUD, the
event pipeline into the terminal, per-life tallies and the commander's first animation - and is
deleted when the last stage lands. The permanent parts go to the records listed in section 16, to
`docs/architecture.md` and to `menu_layout_agent.md`, which does not expire.

Written 2026-09-25 by Claude for Mateusz; the four decisions in section 1 were agreed in that session.
Everything else is a proposal: a future session reads it, checks it against the code as it then is,
and asks where the code has moved on.

Reviewed the same day in a second pass (UI/UX, engine and technical-art eyes): the HUD's hierarchy
fixed, the stages reordered around two playtest gates, the UX checklist answered in section 17. What
changed from the first draft and why is section 18 - read it if you knew the old stage or question
numbers.

---

## 0. How an agent uses this document

1. Read sections 1-3 and 17 whole. Read the section for your stage. Do not read the tree wholesale
   (CLAUDE.md "Read narrow").
2. Work **one stage at a time**, in the order of section 12. Every stage ends at a stop point where
   the game builds, runs and looks no worse than before. A session may take several stages.
   A stage that ends in a **gate** stops there for Mateusz at the Windows machine: the questions it
   names are answered before the next stage starts, because everything after it is built on those
   numbers.
3. Each task names its skill. Gameplay or `sim` work runs `feature`, then `new-system`. Tests follow
   `unit-tests`. Anything in a per-entity loop gets `perf-audit` before you call it done.
4. The CLAUDE.md checklist applies to every task unchanged. This plan adds to it; it never relaxes it.
5. When the plan and the code disagree, the code wins and you say so. When the plan and a decision
   record disagree, the record wins until Mateusz supersedes it.
6. Finish every stage by listing the `graphic.properties` / `controls.properties` / `game.properties`
   keys it added or moved, with values, and a one-line commit message. Mateusz commits.

---

## 1. Decisions already taken (2026-09-25)

| # | Question | Answer | Consequence |
|---|---|---|---|
| D1 | What resets the bottom-bar stats? | **Per life.** | The tally is a component on the commander entity. Dying reaps it; the respawned commander is a new entity with a zeroed tally. No reset code exists anywhere, because none is needed. Restart and a new level start from nothing for the same reason. |
| D2 | The menu's ARENA collides with the glossary's *arena*. | **PROVING GROUND.** | Glossary gets *proving ground*; code says `PROVING_GROUND`. |
| D3 | Commander sprites contradict G0003's temporary half. | **Supersede it for the commander.** | New record G0043 (section 16). The first atlas is the commander's puppet, the portrait and the UI skin. Every other look stays a shape until its own decision; a puppet with missing art falls back to the circle. |
| D4 | Where does Gdx-free UI logic live? | **A new Gradle module `ui`.** | `lwjgl3 -> core -> {ui, net} -> sim -> map`. New ADR 0015. Same reasoning ADR 0003 gave for `sim`: a compile-time wall, not a remembered convention. |
| D5 | The UI's "focus" collides with the glossary's *focus* (who the camera keeps in shot). (2026-09-26) | **selection.** | Glossary and `menu_layout_agent.md` say *selection*; code says `SelectionMemory`, `ui/selection` for the ring. Where this plan still says focus in the UI sense, read selection. |
| D6 | The greybox needs text at the planned metrics before Q3 is answered. (2026-09-26) | **A hand-authored 5 x 8 ASCII pixel font, drawn in code, 6 px advance.** | It is the permanent fallback instead of libGDX's built-in font (E11 changes accordingly: the fallback has the real metrics, so `TextFit` never switches to measured widths). Glyphs outside it draw as `?`. Q3 still picks the real font. |
| D7 | Gate A (2026-09-26): Q1, Q2. | **Accepted as proposed**: view aspect 1.6-2.39, 90 world units tall; maps narrower than the view show ground beyond them. | - |
| D8 | Q3: which font? | **monogram** (datagoblin, CC0), built from its glyph table - Latin-1, Latin Extended-A (Polish), Cyrillic. | `assets/ui/fonts/monogram/`, `UiFont`; `graphic.ui.fontSize` 9 (cap 7 + descender 2), `leading` 3 (accents above a capital). Bars 53 / 41 UI px; 1080p view 640 x 266. `PixelFont` stays the fallback. |
| D9 | Gate B (2026-09-26): Q10, Q33. | **Name the gun in the panel** (for now). **An empty quarter stays black; a disconnected player's panel is grey** and says OFFLINE. | Offline an unplugged pad is detected; a remote player's disconnection is not published to peers yet - a net change, not built. |
| D10 | Q15: remote players in the bottom bar? | **Everybody while there are four players or fewer, local and online**; with more, this machine's seats. A compact **roll call** view for big games is future work (section 19); a placeholder swaps in on `` ` ``. | `PanelAssignment`; *roll call* in the glossary (not *roster*: G0017). |
| D11 | Q22: is every hostile blow decisive? | **Not decided.** The interface acts as if an outcome is either lethal (KIA) or non-lethal (DOWN); no health bar, no invariant test yet. | Revisit if a chipping blow appears. |
| D12 | Q24: a reticle for pad players? | **Yes, a fixed distance along his aim** (`graphic.hud.reticle.padDistance` 24 world units) - and both reticles, the keyboard's included, **off by default** (`graphic.hud.reticle.keyboard`, `.pads`). | With the keyboard's off the system pointer shows. |
| D13 | Q30: who owns the top bar's right block? | **A reserved slot** (`UI:hud/top/aside`), content open; the dev overlay sits in it in the alpha. | - |

---

## 2. What this work must not break

| Source | What it means for UI and graphics |
|---|---|
| Pillar 1 - arcade first | Nothing on screen makes the player stop and compute: no number he must compare with another to act, no UI animation that makes input wait (6.4), two confirms from the main menu into a level (17.2). |
| Pillar 2 - the commander is a unit | No HUD element replaces seeing him. The view follows him; the letterbox never hides him; the pause menu docks beside the view's centre, not over it (6.5); his puppet draws above the other bodies (10.4). |
| Pillar 3 - no economy | A tally is shown, never spent. Nothing buys anything with it. |
| Pillar 4 / G0008 - one fight, **no minimap** | No minimap, radar or second view in either bar. Off-screen markers are an open question in `docs/design/README.md`, not this work's to answer. |
| Pillar 5 - state is visible | Every number the HUD shows is one the game already decides. The HUD never invents state; it reads published facts. Anything a puppet stops showing (the facing spoke) must still be readable from the puppet. **A commander has no health worth a bar**: every hostile blow today takes all 100 of it, so he is standing, down or dead - that state is what the panel shows (7.2). If a blow ever chips, a hidden number appears (Q22). |
| One screen, up to four players | Feedback about one player goes in **his** panel and on **his** man, never over the whole view: a red vignette on a shared screen tells three players they are dying when one is. Full-screen effects are for events that are everybody's (a blast's shake). |
| Pillar 6 - reproducible | World animation runs on **simulation time** (`tick` + render alpha), never the wall clock, so a paused game is a frozen picture and a replay looks identical. UI animation (typewriter, cursor, blink) runs on frame time. |
| Pillar 7 - online is the same game | Anything per player is keyed by **slot** and sized from `Players.MAX` or `PlayerSeats.SEATS`, never assumed to be one or four. The pause is shared; the menu that asks for it is each machine's own (4.3). Tallies live in the snapshot. |
| G0002 - aim is independent of movement | The torso and gun follow the aim; the legs follow the movement. Never derive one from the other. |
| G0003 (permanent half) | Drawing reads `Appearance` and never asks what an entity is. A puppet is chosen by `Look`, never by class or by the presence of `Commander`. |
| G0004 - no event bus in `sim` | Its scope is the simulation; "the client is free to observe the engine however it likes". The HUD pipeline **observes published state from outside** (section 8). Nothing is added to `sim` to notify the HUD. |
| G0021 | The action prompt belongs beside the commander, **not** in the terminal. Out of scope here. |
| G0028 / G0038 | No lobby, ready screen, host start button, player list or slot/colour choice on any page. |
| ADR 0011 | Menu navigation reads the `Gamepad` port, never gdx-controllers, and binds pad buttons by position. |
| ADR 0013 / 0014 | A new component (the tally) is one line in `Components.ALL`; its fields are primitives; `reset()` clears every field. |
| `GameContext` | A screen never switches itself; every page change inside the menu is a stack push, not a `Screen` swap. |
| `ClientArchitectureTest` | Classes ending `Session`, `Controls`, `Selection`, `Director` never touch `Gdx`. New pure classes go in `ui`, where the module itself enforces it. |
| `graphic.properties` header | "Changing anything here must never change what the game does." Every key in this plan is rendering-only, except the tally, which has none. |

---

## 3. Architecture overview

### 3.1 Module boundary

```
lwjgl3 ──▶ core ──┬──▶ ui ──▶ sim ──▶ map
                  └──▶ net ──▶ sim
```

| Module | Owns | May depend on | Must not |
|---|---|---|---|
| `ui` (new) | What the player is shown and how he moves through it, **as data**: screen layout, pages and transitions, navigation and modality, the HUD readout, reporters and the terminal queue, goal resolution, puppet and portrait pose resolution. | `sim` (read components through families), `com.badlogic.gdx.utils`, `com.badlogic.gdx.math`, Ashley | import `com.badlogic.gdx.Gdx`, `..graphics..`, `..scenes..`, `..Input`, `..backends..`, `pl.mzebrows.pwt.net..`, `pl.mzebrows.pwt.client..`; **write** any field of a `sim` component; call `Simulation.step`; log per frame |
| `core` | Drawing and devices: viewports, batches, fonts, skin, atlas, shaders, `Screen`s, input adapters, `Assets`, config loading. Thin by design. | `ui`, `net`, `sim`, libGDX | put a decision in a drawing class (move it to `ui` and test it there) |
| `sim` | Publishes facts: `Appearance` (plus `travelled` and `fired`, section 10), `Level`, `Waves`, `ControlPoint`, `Tally` (section 9). | as today | know that a HUD or a renderer exists |

`UiArchitectureTest` (in `ui`, ArchUnit - already a project dependency) enforces the "must not"
column, including `noClasses().should().setFieldWhere(owner in sim.component)`. Watch each rule fail
against a deliberate violation before trusting it (project habit).

### 3.2 Component diagram

```
┌──────────────────────────────────────────── core (libGDX) ────────────────────────────────────────────┐
│ PwtGame ── GameContext.queueScreen                                                                      │
│                                                                                                         │
│  MenuScreen ───────── MenuStage (scene2d.ui, Skin) ◀── builds from ── ui.page.MenuTree                  │
│     │  page stack in UiFlow                             focus from ─── ui.nav.FocusMemory               │
│  JoinScreen (existing)                                                                                  │
│  ScenarioScreen ──┬── WorldRenderer (circles) ─┐                                                        │
│                   │   PuppetRenderer ──────────┴── reads Appearance; poses from ui.anim.PuppetPoser     │
│                   ├── TerrainRenderer                                                                   │
│                   ├── TopBarPainter ─── reads ui.hud.Readout + ui.hud.Terminal + DebugOverlay text      │
│                   ├── BottomBarPainter ─ reads ui.hud.Readout; portraits from ui.anim.PortraitPoser     │
│                   └── MenuStage (modals) ◀── UiFlow modal stack                                         │
│                                                                                                         │
│  Input: InputReader (commander input, existing) · NavInputReader (keys, mouse, Gamepad port) ─▶ NavInput │
│  Look:  Hud (becomes the UI canvas: PixelViewport + batch + font) · UiSkin · UiStrings (I18NBundle)     │
│  Assets (every path) · GraphicConfig / ControlsConfig (every key)                                       │
└───────────────┬───────────────────────────────────────────────────────────────┬────────────────────────┘
                │ values in, values out                                           │ ScenarioPlay
┌───────────────▼──────────────── ui (no Gdx drawing, no devices) ─────────┐   ┌───▼──── net ────┐
│ layout : ScreenLayout ──▶ Rects (canvas, view, topBar, bottomBar)         │   │ Transport, ...  │
│ page   : UiPage, HudSlot, MenuTree, UiFlow, UiIntent, UiEffect           │   └───┬─────────────┘
│ nav    : NavInput, InputModality, FocusMemory, NavRepeat                  │       │
│ hud    : ReadoutWriter ─▶ Readout ─▶ Reporter[] ─▶ Dispatch ─▶ Terminal   │       │
│          GoalLine, TextFit                                                │       │
│ anim   : PuppetPoser ─▶ PuppetPose ;  PortraitPoser ─▶ PortraitPose       │       │
└───────────────┬──────────────────────────────────────────────────────────┘       │
                │ reads components through families; never writes                   │
┌───────────────▼──────────────────────────────── sim ───────────────────────────────▼┐
│ Appearance (+travelled, +fired)  Level  Waves  ControlPoint  Nest  Commander  Tally  │
│ Respawning  Weapon  Health  Action                                                    │
└───────────────────────────────────────────┬──────────────────────────────────────────┘
                                            ▼ map
```

### 3.3 One frame, in order

`ScenarioScreen.render(delta)` keeps its existing order and adds the steps marked **+**:

```
1  read input                       InputReader, NavInputReader(+); ReleaseLatch masks every press
                                    held across a context change until it is released (+, 6.2)
2  modal input                      NavInput → intent → UiFlow.apply  (+)  → pause request/resume/restart/leave
3  step the session                 play.advance(delta, players)            (0..n ticks)
                                    his input is neutral while a menu is open on this machine (+)
4  fill the readout                 ReadoutWriter.fill(world, locals, readout)            (+)
5  report                           reporters compare readout vs previous → Terminal.offer  (+)
6  advance UI clocks                Terminal.advance(frameDt), panel and portrait timers    (+)
7  aim the camera                   CameraDirector (existing), view size from ScreenLayout
8  draw world into the view rect    TerrainRenderer, WorldRenderer, then PuppetRenderer's layer
9  draw the bars                    TopBarPainter, BottomBarPainter
10 draw over the view               scrim, paused banner, menu (MenuStage), reticle last   (+)
```

Steps 4-6 run once per frame, after however many ticks step 3 ran. Consequence, and the rule that
follows from it: **report on counters, not flags** (section 8.2).

### 3.4 Where each new class lives

| Package (module `ui`) | Classes | Tested by |
|---|---|---|
| `pl.mzebrows.pwt.ui.layout` | `ScreenLayout`, `LayoutSpec`, `Rect`, `HudMetrics` (bar heights from what the bars hold) | `ScreenLayoutTest`, `HudMetricsTest` |
| `pl.mzebrows.pwt.ui.page` | `UiPage`, `HudSlot`, `MenuTree`, `MenuEntry`, `UiFlow`, `UiIntent`, `UiEffect` | `UiFlowTest`, `MenuTreeTest`, `UiSpecTest` |
| `pl.mzebrows.pwt.ui.nav` | `NavInput`, `InputModality`, `FocusMemory`, `NavRepeat`, `ReleaseLatch` | `InputModalityTest`, `NavRepeatTest`, `ReleaseLatchTest` |
| `pl.mzebrows.pwt.ui.hud` | `Readout`, `PlayerReadout`, `ReadoutWriter`, `Reporter` + one per fact, `Dispatch`, `DispatchKind`, `Terminal`, `GoalLine`, `TextFit`, `PanelLayout` | `ReadoutWriterTest` (on `AlphaWorlds`), `TerminalTest`, `ReportersTest`, `GoalLineTest`, `TextFitTest`, `PanelLayoutTest` |
| `pl.mzebrows.pwt.ui.anim` | `PuppetPose`, `PuppetPoser`, `Sector`, `PortraitPose`, `PortraitPoser` | `PuppetPoserTest`, `PortraitPoserTest` |

| Package (module `core`) | Classes |
|---|---|
| `pl.mzebrows.pwt.client` (existing, flat) | `Hud` (grows into the canvas), `PixelViewport`, `NavInputReader`, `MenuStage`, `UiSkin`, `UiStrings`, `TopBarPainter`, `BottomBarPainter`, `ReticlePainter`, `PuppetRenderer`, `PuppetLibrary`, `SharpPixelShader` |

Existing classes are not moved to new packages (no opportunistic refactor). `MenuSelection` moves
to `ui.page` only if `MenuTree` needs it from there; say so when you do.

---

## 4. UI state machine

### 4.1 Shape

Two levels, because libGDX `Screen` swaps are expensive and disposing, while page changes must not be:

- **Screens** (existing): `MenuScreen`, `JoinScreen`, `ScenarioScreen`. Swapped only through
  `GameContext.queueScreen`.
- **Pages and modals** (new): a stack owned by one `UiFlow` per screen. `MenuScreen` holds the
  `UI:main...` page stack; `ScenarioScreen` holds the `UI:play...` modal stack.

`UiFlow` is pure: `apply(UiIntent) -> UiEffect`, plus `observe(paused, ended, worldPresent)` for the
states that are **derived** rather than chosen. It holds the stack and nothing else; the screen
performs the effect.

### 4.2 States

```
UI:main ─OPEN─▶ campaign ───────────── START ───────────┐
   │    ─OPEN─▶ proving-ground (placeholder)             │
   │    ─OPEN─▶ online ─▶ join ── JOIN ─▶ UI:connecting ─┼─ world arrives ─┐
   │                   └▶ host ── HOST ──────────────────┤                 │
   │    ─OPEN─▶ options (placeholder)                    ▼                 │
   │    ─OPEN─▶ exit ── QUIT_APP ─▶ app exits         UI:play ◀────────────┘
   │                                      Esc / START │   ▲ RESUME, BACK here,
   │                                                  │   │ or anybody resumes
   │                                                  ▼   │
   │                                            UI:play/pause ── LEAVE_CONFIRM ─▶ UI:play/leave
   │                                                                                  │ LEAVE
   ◀──────────────────── LEAVE, or play.ended() (with the notice) ────────────────────┘

BACK pops one page anywhere; BACK on UI:main only moves focus to exit.
Paused with no menu open on this machine: the paused banner over UI:play (derived, not a page).
UI:play/game-over: specified, blocked.
```

The full table, with effects, is section 5 of `menu_layout_agent.md`.

### 4.3 Derived versus chosen

| State | How it is entered | Why |
|---|---|---|
| `UI:play/pause` (the pause menu) | **chosen** on this machine: `Esc` or `START` opens it, and asks for the pause if the session is not paused. It closes on RESUME or BACK here, **and** whenever `paused()` turns false, whoever resumed. | The pause is shared - online it is anybody's and stops every machine - but the menu is not. A friend's pause must not steal this player's focus or open a page he did not ask for, and the harness's `P` must pause without covering the world `.` is stepping through. |
| paused banner (`UI:hud/view/paused`) | **derived** from `ScenarioPlay.paused()` while no menu is open here | State is visible: a world frozen by somebody else says so, and says which button opens the menu. A local flag could show a running game as paused, or the reverse; this cannot. |
| `UI:connecting` | derived from `play.simulation() == null` | Existing behaviour. |
| back to `UI:main` with a notice | derived from `play.ended()` | Existing behaviour. |
| `UI:play/leave`, `UI:main/exit`, every menu page | chosen | Local to this machine. |
| `UI:play/game-over` | derived, **blocked** | Nothing in the simulation says a run is over; the commander always respawns (scaffolding). |
| "Mission completed" | **not a state** - a goal-line text | What a completed level does is an open question; showing that it is complete settles nothing. |

Online the menu opens on the press and the world stops when the pause command lands a few ticks
later - under the scrim, with his input neutral meanwhile. Waiting for the round trip before showing
anything would make the pause button feel broken on exactly the machine with the worst link.

### 4.4 Harness keys after this work

`Esc` in a level opens the pause menu instead of leaving (the menu's LEAVE does that). `P`, `.`,
`R`, `G`, `F1`-`F3` stay dev keys; `P` pauses **without** a menu - the banner shows - so single-stepping
with `.` still sees the world. `F1` now cycles the overlay panel: **panel → expanded → off**
(expanded draws the full overlay over the view's top-right corner). Both tables change in the same
change: `alpha-phase.md` harness table, `docs/design/README.md` input map.

---

## 5. Pixel-perfect screen layout

### 5.1 Two pixel grids, one rule each

| Grid | What is drawn on it | Rule |
|---|---|---|
| **UI canvas** | bars, text, portraits, pages, modals | Integer scale only. One UI pixel = `s x s` screen pixels, `s` a whole number. Nearest filtering, integer positions (`BitmapFont.setUseIntegerPositions(true)`), no mipmaps, never a fractional scale on text. |
| **World** | terrain, shapes, puppets | The camera zooms continuously (`nearZoom` 1 → `farZoom` 2.2, smoothed) and moves by sub-pixels, so an integer texel-to-pixel ratio cannot hold. Sprites are sampled with a **sharp-pixel shader** (antialiased nearest): crisp texel edges at any scale, rotation and sub-pixel offset, no shimmer. Shapes are unaffected. |

Rejected, with the reason: rendering the world into a low-resolution framebuffer and upscaling by an
integer - pixel-exact at one zoom only, and the camera has two zooms and a smoothed transition
between them.

### 5.2 The solver - `ScreenLayout.solve(screenW, screenH, spec)`

Pure, in `ui.layout`, fully tested. Input is back-buffer pixels (`Gdx.graphics.getBackBufferWidth()`
on HiDPI). Output is four rects in UI pixels plus the scale and the centring offset.

The bars are sized by **what they hold**, the view takes the rest, and its aspect is kept inside a
**range** rather than pinned. A pinned 2.39 spent a third of a Steam Deck's height and 44 % of a 4:3
screen on bars holding nothing extra; a range gives those screens a taller view and leaves every 16:9
screen where the mock-ups put it.

```
sMax   = max(1, floor(screenH / spec.canvasHeight))                 // canvasHeight = 360
s      = spec.uiScale == 0 ? sMax : clamp(spec.uiScale, 1, sMax)    // 0 = auto; a player may pick smaller
cw, ch = floor(screenW / s), floor(screenH / s)                     // canvas, UI px
ox, oy = (screenW - cw*s) div 2, (screenH - ch*s) div 2             // remainder < s px, black
topMin, bottomMin = HudMetrics                                      // from font, lines, padding, portrait
vw, vh = cw, ch - topMin - bottomMin                                // the tallest view the bars allow
if vh < ch / 2:  topMin = bottomMin = 0;  vh = ch                   // too short for both: the fight wins (E2)
if vw / vh > aspectMax:  vw = round(vh * aspectMax)                 // ultrawide: pillars, black
if vw / vh < aspectMin:  vh = floor(vw / aspectMin)                 // tall screen: the height goes to the bars
vx     = (cw - vw) div 2
spare  = ch - vh - topMin - bottomMin
top    = topMin + ceil(spare * topShare);   bottom = ch - vh - top
bottomBar = (0, 0, cw, bottom);  view = (vx, bottom, vw, vh);  topBar = (0, bottom + vh, cw, top)
```

`HudMetrics`: `topMin = 2*padding + lines*fontSize + (lines-1)*leading` (4 lines: mission, goal, two
terminal lines) = **46**; `bottomMin = 2*padding + portrait.size` = **40**. Derived, not configured:
a second number for a bar's height would be a key that lies the day the font changes.

Bars span the full canvas width even when the view has pillars; their black fill does. Their
**content** is laid out inside the view's horizontal span (`vx .. vx+vw`) and anchored to the edge that
**touches the view**, so on an ultrawide the text does not drift to the far corners of the screen and a
tall bar grows empty space outwards, away from the fight. Eye travel is bounded by the view, not by
the monitor.

Worked examples (`aspectMin 1.6`, `aspectMax 2.39`, `canvasHeight 360`, `topShare 0.5`, `uiScale 0`)
- these become `ScreenLayoutTest` cases. The last column is what the first draft's pinned 2.39 gave.

| Screen | s | Canvas | View (UI px) | Aspect | Bars top/bottom | View on screen | First draft |
|---|---|---|---|---|---|---|---|
| 1920 x 1080 | 3 | 640 x 360 | 640 x 274 | 2.34 | 46 / 40 | 1920 x 822 | 1920 x 801 |
| 2560 x 1440 | 4 | 640 x 360 | 640 x 274 | 2.34 | 46 / 40 | 2560 x 1096 | 2560 x 1068 |
| 1280 x 720 | 2 | 640 x 360 | 640 x 274 | 2.34 | 46 / 40 | 1280 x 548 | 1280 x 534 |
| 3840 x 2160 | 6 | 640 x 360 | 640 x 274 | 2.34 | 46 / 40 | 3840 x 1644 | 3840 x 1602 |
| 1366 x 768 | 2 | 683 x 384 | 683 x 298 | 2.29 | 46 / 40 | 1366 x 596 | 1366 x 570 |
| 1280 x 800 (Steam Deck) | 2 | 640 x 400 | 640 x 314 | 2.04 | 46 / 40 | 1280 x 628 | 1280 x 534 |
| 1920 x 1200 | 3 | 640 x 400 | 640 x 314 | 2.04 | 46 / 40 | 1920 x 942 | 1920 x 801 |
| 2560 x 1080 | 3 | 853 x 360 | 655 x 274, pillars 99 | 2.39 | 46 / 40 | 1965 x 822 | 2007 x 840 |
| 3440 x 1440 | 4 | 860 x 360 | 655 x 274, pillars 102 | 2.39 | 46 / 40 | 2620 x 1096 | 2676 x 1120 |
| 1024 x 768 | 2 | 512 x 384 | 512 x 298 | 1.72 | 46 / 40 | 1024 x 596 | 1024 x 428 |
| 800 x 600 | 1 | 800 x 600 | 800 x 500 | 1.60 | 53 / 47 | 800 x 500 | 800 x 334 |
| 3840 x 2160, `uiScale 4` | 4 | 960 x 540 | 960 x 454 | 2.11 | 46 / 40 | 3840 x 1816 | - |

`uiScale` is the UI-size option (C33): a smaller whole scale than the largest that fits gives a
smaller HUD **and** a bigger view, and never a fractional one.

### 5.3 The world inside the view rect

- The world viewport is set to the view rect in screen pixels. Its world size is
  `(virtualHeight x vw / vh, virtualHeight)` - the exact ratio of the pixel rect, so nothing is ever
  stretched. `graphic.virtualHeight` (90) keeps its meaning; `graphic.virtualWidth` stops being a
  key, because it is now derived and a second number for it would be a key that lies.
- On a 16:9 screen the near view is about **210 x 90 world units** (184 x 90 on 16:10, 155 x 90
  on 4:3), against 160 x 90 today. Different screens see different widths of one field; this is
  co-op, so there is nothing to be fair about, and nothing in `sim` reads it.
  Consequences, all to check with Mateusz at the first playtest:
  - A default-size map (`sim.map.*`, 160 wide) is now narrower than the view. The camera already
    centres on a level smaller than itself; the margin beside the map draws as out-of-bounds ground
    (section 13), and small scenarios want wider maps as content later.
  - The leash (`sim.commander.leashRadius=120`) was sized by hand against the near view (glossary);
    it is still wider than the view, and the row stays true, but the ratio moved.
  - `CameraDirector.update(viewW, viewH, ...)` already takes the view size; nothing in it changes.
- The mouse aims through the letterboxed viewport: `InputReader` already unprojects the pointer
  through the world viewport, which now has a screen offset, so a pointer over a bar aims at the
  world point beyond the view's edge (E33). Aim is a direction; nothing about range changes.
- Nothing in `sim` reads any of this. Aim is a direction and zoom is rendering (invariant rows).

---

## 6. Menu system

The page-by-page specification, ids, transitions, input table and placeholder paths are in
**`menu_layout_agent.md`** (this folder). This section is the engineering around it.

### 6.1 Drawing

- Pages and modals are **scene2d.ui** (`Stage`, `Table`, `Label`, `TextButton`, `ScrollPane`) on the
  UI canvas viewport, looks from a `Skin` (`assets/ui/skin.json` over the atlas). Swapping the skin
  file restyles every page without touching code (constraint 1).
- The page's content comes from `MenuTree` (data, in `ui`); `MenuStage` (core) turns one page of it
  into widgets and back into `UiIntent`s. `MenuStage` holds no decisions.
- scene2d's own keyboard focus is **not** used for navigation. Focus is ours (`FocusMemory`, per page)
  and `MenuStage` only paints it, because scene2d has no gamepad story and ADR 0011 forbids reaching
  gdx-controllers anyway.
- HUD bars are **not** scene2d. They are painted directly with the canvas batch: nothing in them is
  interactive, and painting them directly keeps them allocation-free per frame.

### 6.2 Navigation input - `NavInput`

A per-frame value built by `NavInputReader` (core) from `KeyPresses`, the mouse, and every seated
`Gamepad`, and read by `UiFlow`, `FocusMemory` and `InputModality` (ui). It carries: `up down left
right confirm back tabPrev tabNext` (edges, after repeat), `pointerX pointerY pointerMoved
pointerPressed pointerSecondary`, and `lastPadLabels` (which label family to print).

- Stick and d-pad repeat (`NavRepeat`, pure): first repeat after `ui.nav.repeatDelaySeconds`, then
  every `ui.nav.repeatIntervalSeconds`; the stick counts past `ui.nav.stickThreshold`.
- Pad buttons by position, from `controls.properties` (`controls.ui.*`, section 11). In pages,
  `SOUTH` confirms and `EAST` goes back; in a level they keep their game meanings. The input map gets
  a "pages and modals" context so nobody reads that as two features claiming one button.
- **A press that changes context is spent.** `SOUTH` confirms RESUME and is also *act*; `EAST` backs
  out of the menu (= RESUME) and is also *throw*; the left mouse button clicks RESUME and is also
  *fire*; `Enter`, `Space`, `SOUTH` or a click starts a level. Without a guard every one of those
  reaches the first tick of play: he fires, gets on a turret, throws a grenade. `ReleaseLatch` (ui, pure): on every context change (menu opened or closed, level started,
  page swapped) it records each key, button and trigger then held, per device, and the game side of
  `InputReader` ignores each until it has been released once. Tested (`ReleaseLatchTest`), and E32.

### 6.3 Modality - see `menu_layout_agent.md` section 6

`InputModality` is pure and tested: the last device that did something meaningful decides whether
focus is drawn as hover or as a ring, whether the cursor shows, and which hint glyphs the footer uses.
Hints always show the glyphs of the device in use (a Steam Deck is a pad in Xbox positions); a
keyboard hint after a pad press is the bug Valve's review fails a game for.

### 6.4 Motion - what moves, how long, and why

UI motion exists to say that something **changed**, never to decorate. Budgets, all on the UI clock
(frame time, keeps running while paused), all multiplied by `graphic.ui.motionScale` - the one place
every UI duration and amplitude is read through, so reduced motion is one number (0 = none):

| Motion | Says | Budget |
|---|---|---|
| Page push / pop | "you went in / came back out" - direction of travel | ≤ `graphic.ui.motion.pageSeconds` (0.12 s): a slide of a few UI px plus fade. Instant at `motionScale 0`. |
| Scrim in / out | "the world is frozen / live again" | `graphic.ui.motion.scrimSeconds` (0.08 s) |
| Focus move | "this is selected" | instant ring; nothing slides between entries |
| Terminal typing | "this just happened" | normal and low dispatches at `charsPerSecond`; **high ones appear at once** (8.4) |
| Panel alarm | "you, specifically, must act" - entering DOWN, KIA or OVERHEAT | frame flash + 1 UI px shake, ≤ `graphic.hud.panel.alarmSeconds` (0.15 s) |
| Alert pulse | "act on this now": DOWN, OVERHEAT, KIA | `graphic.hud.alert.pulseHz` (2), refused above 3 by config validation (C34) |
| Goal completed | "that part is done" | two pulses on the rising edge, then still |

Rules that make the table safe:

- **Input never waits on motion.** An input during a transition finishes it at once and is applied;
  `UiFlow` changes state on the press and the animation only catches up. A menu that eats a press
  because it was still sliding is the most common way a UI feels slow.
- **Nothing in the HUD moves when nothing changed.** Peripheral vision is built to catch motion, so
  idle motion in the bars steals the eye from the fight. The cursor blinks only while a line types
  and for `cursorLingerSeconds` after; the portrait's blink is the single idle motion, slow and
  low-contrast (10.5).
- UI shakes move by whole UI pixels on the canvas; nothing in the bars is drawn at a fractional
  position.
- World animation is not in this table: it runs on simulation time (pillar 6) and stops with the pause.

### 6.5 The pause, on screen

- **Scrim, not blur.** The view is dimmed by `graphic.ui.scrimAlpha` (0.55) and the frozen fight
  stays readable under it, so the player can re-orient before he resumes. Blur costs a framebuffer
  pass and turns pixel art to soup, which is the opposite of letting him read the scene.
- **Docked, not centred.** The camera centres on him, so a centred menu covers exactly what he needs
  to see. The pause menu docks to the left third of the view; the bars stay as they are.
- **Banner when it is not his menu.** Paused with no menu open here: a one-line banner at the top of
  the view, `PAUSED - {menu key}: menu`, over a lighter scrim. No focus taken, nothing opened.
- **Resume lands on the press.** Offline the world runs on the next frame; online when the command
  lands. Whether a short "get ready" beat should follow is Q26.
- **Focus loss** (alt-tab, a notification) opens the menu offline, per Q7. A seated pad unplugged
  mid-level does the same offline, per Q27; pausing is not leaving, so G0028 stands.

---

## 7. HUD

### 7.0 What the HUD is for - hierarchy and colour

The fight is the HUD's competition, and the HUD should lose until it matters. Four tiers, each with
its place:

| Tier | Needed | Examples | Where it lives |
|---|---|---|---|
| 0 | every moment | what is coming at him, where he is aiming | the world: his man, the reticle (7.5) |
| 1 | a glance every few seconds, mid-fight | his state (down and bleeding out, or not), his gun's heat | **on his man first** (heat arc, casualty ring, charge - built); repeated in his panel for the moment he loses his man in a crowd |
| 2 | when it changes | the goal, a wave, a point taken | top bar: goal line, terminal |
| 3 | between fights, or never | tally, mission and planet, dev overlay | bars, dim |

At 1080p the top bar's text sits about 400 px from the view's centre: a look there costs a saccade and
a refocus, a fifth of a second or more. Right for tiers 2-3, wrong for 0-1 - which is why tier 1
lives on his man and the panel only repeats it.

**Colour policy.** Every hue already means something in the world - red and orange are enemies,
barrels and fire, purple a spitter, amber heat and blasts, green the stabilise, frogs and mines, blue
troopers and turrets - and eight commander colours sit on top. No hue can be *reserved* for warnings,
so the HUD stands out by **place** and **treatment** instead:

- The bars are black and nothing of the world is ever drawn in them; that is the separation.
- HUD text is neutral: `graphic.hud.color.text` for what is current, `.textDim` for what is static
  or old.
- A player's colour (`graphic.color.commander[.N]`, the world's own lookup) appears only as **his**
  identity: his panel's frame and his `P1`-`P8` label. The first draft's `goalDone` cream was
  `commander.6` exactly and its mission green sat beside `commander.2`; both are gone.
- A world meaning keeps its world colour: the panel's heat bar uses `graphic.color.heat` / `.overheat`.
- **Alert** (`graphic.hud.color.alert`) is red, used only inside the bars, only as inverse video
  (black text on a red block, 5.7:1 - white on it would fail 4.5:1), only for DOWN, OVERHEAT and KIA,
  pulsing at ≤ 3 Hz. Nothing UI-red is ever drawn over the view: red there is an enemy.

### 7.1 Top bar (mock-up `game_hud_top.png`)

```
┌──────────────────────────────────────────── top bar, 46 UI px ──────────────────────────────────────┐
│ PLANET: ZXC-123  MISSION: Adventure            [dim]        ┌── overlay panel (F1) ─── red frame ──┐│
│ CURRENT GOAL: Capture next control point  2/4  [bright]     │ Adventure   fps 144   tick 20371     ││
│ LOG: Mission started                           [dim grey]   │ commanders 1  enemies 44  proj 12    ││
│ LOG: Flamethrower acquired _                   [white]      │ online: host  delay 2t 33ms  ABCD    ││
└─────────────────────────────────────────────────────────────┴──────────────────────────────────────┘┘
  left block: 1 - overlay.widthShare                            right block: overlay.widthShare
```

- Line 1 `UI:hud/top/mission`, dim (tier 3, read once): scenario name, and a planet when
  `Scenario.planet()` returns one (a default method returning `""`; no scenario overrides it until the
  open question is answered).
- Line 2 `UI:hud/top/goal`, bright - the one line in the bar that is always current:
  `GoalLine.resolve(readout)` - the first **unmet** required objective in
  the order `CAPTURE_POINTS` (with `taken/total`), `HOLD_POINT` (with `wave sent/total`),
  `CLEAR_ENEMIES`; all met → `MISSION COMPLETED`, pulsing twice on the rising edge; no objectives → the line is
  empty. `Level.met` is recomputed every tick, not latched, so the line may go back from completed to
  a goal - that is the truth and it is shown. A change of text waits `graphic.hud.goal.minHoldSeconds`
  so a one-tick flip never flickers.
- Lines 3-4 `UI:hud/top/terminal`: section 8.
- Right `UI:hud/top/overlay`: `DebugOverlay` drawn into a rect instead of the whole screen. Its lines
  are ranked (scenario and pause, fps and tick, connection, invitation, commander, counts, entities,
  last order, key help) and the panel shows as many as fit; `F1` cycles to the expanded overlay.
  When hidden the space stays reserved - the layout never reflows. A build without the dev overlay
  needs a player-facing owner for this block; that is Q30.

### 7.2 Bottom bar - four player panels (mock-up `game_hud_bottom.png`, re-ranked)

The bar is **four fixed quarters, one per local seat**, left to right in seat order (the keyboard is
seat 0), tiling the view's horizontal span (5.2; the odd UI px go to the gaps). A seat with a commander
standing or a respawn pending draws its panel in its quarter; any other quarter stays black (Q33). One
player gets the left quarter, as the mock-up has it; four get all four. Online, only this machine's
seats (`ScenarioPlay.slotOf(seat)`) - never more than `PlayerSeats.SEATS`, whatever the session
holds (pillar 7).

Fixed quarters rather than dividing the bar by the number of players: plugging a pad in **is** joining
(G0028) and can happen mid-fight, and a divided bar would move and resize everybody's panel at that
moment. Fixed quarters never reflow, a player's panel is always where it was, and there is **one**
layout to fit and test - four panels is the only case; one player is three quarters left dark.

```
┌─ P1 cyan ─────────────────────┬─ P2 amber ────────────────────┬─ P3 green ────────────────────┬─ P4 pink ─────────────────────┐
│┌──────┐ P1                    │┌──────┐ P2 [DOWN ▮▮▮▮▯▯▯▯▯]   │┌──────┐ P3 [KIA 1.2]          │┌──────┐ P4 ABOARD             │
││ face │ RAILGUN ▮▮▮▮▮▮▯▯▯▯    ││ face │ SHOTGUN ▮▯▯▯▯▯▯▯▯▯    ││ face │ FLAMER [OVERHEAT]     ││ face │ TURRET ▮▮▮▯▯▯▯▯▯▯     │
│└──────┘ ☠123  ⚑2  +2          │└──────┘ ☠  7  ⚑0  +1          │└──────┘ ☠ 45  ⚑1  +0          │└──────┘ ☠  0  ⚑0  +3          │
└───────────────────────────────┴───────────────────────────────┴───────────────────────────────┴───────────────────────────────┘
```

**What a panel does not show.** Health: every hostile blow to a commander takes all of his 100 (claws,
spit, blast, fire, slick - `game.properties`), so he is standing, down or dead and there is nothing in
between to draw; Q22 proposes keeping it that way. Ammunition: guns overheat, and grenades are
unlimited (alpha-phase row). What it does show:

| Row | Element | Shows | From `PlayerReadout` |
|---|---|---|---|
| - | Frame | 1 UI px in his commander colour, inside the quarter's padding | colour variant |
| - | Portrait | 32 x 32 (10.5); a block in his colour until stage 8 | posture, fired, overheated |
| 1 | Label | `P{slot+1}` in his colour: a colour **and** a word (7.4) | slot |
| 1 | **Status** | **empty while he stands** - quiet until it matters, and always in the same place when it does. `DOWN` and his bleed-out as a draining bar (the fraction the world's ring shows); `KIA` and the seconds to his respawn; `ABOARD` on a turret. `DOWN` and `KIA` in alert. | posture, charge, respawn seconds |
| 2 | **Weapon** | the gun's name (`ui.weapon.*`, ≤ 7 characters) and a heat bar in the world's heat colours; while overheated the bar becomes `OVERHEAT` in alert - same place, same width. Aboard: the turret's gun and its heat (G0025: heat belongs to the gun). | weapon kind, heat, overheated |
| 3 | Tally | three icons and numbers, dim: kills (skull), goals (flag), stabilised (a **green plus** - never a red cross, a protected emblem); `999+` above 999. The mock-up's words (`KILL COUNT:` ...) are in the pause menu, where there is room and time to read them. | tally |

**Pixel budget** at the 640-wide canvas every 16:9 and 16:10 screen gets - and the reason the font must
have a 6-px advance (Q3):

| Part | UI px |
|---|---|
| Quarter | 160 x 40 (163 on ultrawide, 170 at 1366 x 768, 200 at 800 x 600) |
| Padding, frame inside it | 4 each side |
| Portrait + gap | 32 + 4 |
| Text column | 116 = 19 characters |
| Rows | 3 x 8 + 2 x 2 leading = 28 of the 32 inside the padding |
| Row 1, worst | `P8 KIA 1.2` = 10 characters (60); `P2 DOWN` = 42 + a bleed-out bar of the rest |
| Row 2, worst | `SIDEARM` = 42 + 6 + a 68-px heat bar; `OVERHEAT` (48) in the bar's place |
| Row 3, worst | 3 x (icon 7 + 1 + `999+` 24) + 2 gaps of 6 = 108 |

- **A narrow canvas** (4:3, 512 wide, quarters of 128) drops the portrait, which leaves a text column
  of 120 and everything else fits. That is the whole degradation rule (`PanelLayout`): portrait only
  in a quarter at least 160 wide. Label, status, weapon and tally are never dropped.
- **Alarm, not hurt.** There is no chip damage to react to - a hit downs or kills him - so feedback
  is for the **transition**: entering DOWN, KIA or OVERHEAT flashes his frame and shakes it by one
  UI px (6.4) as the status field turns alert. No full-screen vignette: the screen is shared (section 2).
- With no commander standing, the tally shows the life that just ended until the new one starts -
  the readout keeps the last value while `Respawning` is pending, then zeroes on the new commander.
- Four panels are not four distractions: the portraits' blinks are offset by seat (10.5), and
  nothing else in the bar moves while nothing changes (6.4).

### 7.3 Text robustness

| Rule | Detail |
|---|---|
| One monospace pixel font | `assets/ui/fonts/terminal.ttf` through gdx-freetype (already a dependency) at its native size, `mono = true`, no hinting, nearest filtering. Width = `chars x advance`, so every fit is arithmetic (`TextFit`, pure). |
| No wrap in bars | Every bar slot is exactly one line. Overflow truncates: argument first, then template, ending `...`. |
| Wrap in pages | Descriptions wrap on words to two lines, then truncate. |
| Fixed-width numbers | Tallies right-aligned in 4 columns in the panel; above 999 → `999+` (the pause menu shows the whole number). |
| Legibility floor | The smallest glyph that carries meaning (digits, lowercase) ≥ 5 UI px tall and capitals ≥ 6, so at `s = 2` - 720p, 768p and the Steam Deck's 1280 x 800 - they are ≥ 10 and ≥ 12 screen px. Valve's Deck review: never below 9 px at 1280 x 800, 12 recommended. `s = 1` happens only in windows under 720 px tall: accepted, logged once. |
| Compact forms | The panel's tally is icons and numbers, never words - the quarter is 19 characters wide (7.2). The words live in the pause menu. |
| Localisation-ready | Every string is a key in `assets/i18n/ui.properties` (`I18NBundle`). `TextFitTest` checks every template with its longest argument fits its slot at 640 (every 16:9 and 16:10 screen) **with four panels**, and that at 512 (4:3) every slot degrades by its order instead of overflowing. The same checks run on a pseudo-locale - every template 30 % longer, with Polish diacritics - because a translation runs longer than English and the font must have the glyphs (Q3). |
| Missing glyph | Draws `?`. Missing key: `#key`, logged once per key. |
| No allocation per frame | Painters rebuild a reused `StringBuilder` only when a readout value changed; one reused `GlyphLayout`. |

### 7.4 Accessibility - built in from the first stage, not bolted on

| Need | Rule | Where |
|---|---|---|
| Read it on a small screen | The legibility floor (7.3). At `s = 2` - 720p, 768p, the Steam Deck - text passes Valve's review. | `ScreenLayout`, font (Q3) |
| Never colour alone | His panel carries `P1`-`P8` as text; states carry a word (`DOWN`, `KIA`, `OVERHEAT`) and a shape (the bar changes form); a goal done is a word. **Measured**: simulated at full severity (Machado 2009, ΔE in CIELAB), commander colours 2 and 3 (amber, green) are 3 apart under deuteranopia - the same colour - and 6 and 7 are 5 apart under protanopia; under about 10, on a small moving circle, it is a guess. The world half - a mark over each man - is Q23. | 7.2, Q23 |
| Size | `graphic.ui.scale` (0 = auto), 5.2 | `ScreenLayout` |
| Motion | `graphic.ui.motionScale` (0 = none): every UI motion reads it (6.4). `graphic.camera.shakeScale` already does the same for the camera. | 6.4 |
| Flashes | `graphic.ui.flashScale` (0 = none: a flash becomes a steady change). Nothing flashes more than three times a second - the WCAG 2.3.1 threshold - and config validation refuses a pulse above it. | 6.4 |
| Contrast | HUD text ≥ 4.5:1 on the bar. Lowest in this plan: `textDim` 8a8a8a on black, 6.1:1. | 7.0 |
| Remapping | `controls.properties` today; an in-game remap needs somewhere to save it (Q4). | stage 10 |
| Text entry on a pad | Valve's Deck review requires controller-only entry; the room code is Q6. | `UI:main/online/join` |

Each option's **key** arrives with the stage that introduces its effect, so the Options page (stage
10, blocked on Q4) is a page over keys that already work, not a retrofit.

### 7.5 The reticle

The keyboard player aims with the mouse, and today the only thing at his aim point is the operating
system's arrow. In a twin-stick game the aim point is where his eyes spend most of their time, so it
gets the game's own mark:

- The system cursor is hidden in `UI:play` and comes back in pages (modality, 6.3).
- `ReticlePainter` (core) draws `ui/reticle` at the pointer's **screen** position - not snapped to the
  UI grid, which would step it by `s` pixels and feel laggy - scaled by `s`, in his commander colour
  with a one-texel dark outline, so it reads on every ground and through a blast.
- Drawn last, above the scrim, and over a bar when the pointer is there (it still aims, E33).
- A pad player aims with a stick and has no point to mark; a reticle at a fixed distance along it is Q24.

---

## 8. HUD event pipeline - readout, reporters, terminal

### 8.1 Why this shape

The request was a loosely-coupled event listener from gameplay triggers to the terminal. G0004 bans
an event bus **in the simulation** and explicitly leaves the client free to observe the engine. So:
the simulation keeps publishing state exactly as it does; the client takes one copy of the facts it
shows per frame (the **readout**), and small **reporters** compare this frame's copy with the last
one and file **dispatches**. This is the pattern `Announcements` already uses for the wave line and
the action prompt, generalised - and `Announcements` is folded into it rather than kept beside it
(two ways to do one thing is a defect).

It also survives online play for free: every peer's world is the same, so every peer's reporters
file the same dispatches; a peer that drops in reports from its first frame (section 8.4).

### 8.2 Types (module `ui`, package `hud`)

| Type | What it is | Rules |
|---|---|---|
| `Readout` | Every fact the HUD shows, copied once per frame into preallocated fields: `worldSerial`, mission name, planet, objective bits required/met, points taken/total, waves sent/total/armed, nests standing, troopers standing, and `PlayerReadout[Players.MAX]` by slot. | Filled only by `ReadoutWriter`. Plain fields, no Gdx, no allocation after construction. |
| `PlayerReadout` | Per slot: present, local, seat, posture (standing / casualty / dying / aboard / respawning), weapon kind, heat, overheated, fired, bleed-out (the `charge` fraction while a casualty), respawn seconds left, tally (kills, goals, stabilised), colour variant. | Keyed by **slot**, never by `Entity`. |
| `ReadoutWriter` | The **one place** the HUD reads `sim`. Iterates cached families (commanders, control points, nests, troopers) and the level entity. | Families cached (a `Family` built per frame allocates). Reads only. |
| `Reporter` | `void report(Readout previous, Readout now, Terminal out)`. One per fact: `MissionReporter`, `PlayerReporter` (joined, weapon, turret, down, lost, back), `PointReporter`, `WaveReporter`, `SquadReporter`, `NestReporter`, `ObjectiveReporter`. | Compares **counters** where the sim has them (points taken, waves sent, nests standing), because steps 4-6 run once per frame over any number of ticks and a flag that flips twice between frames is invisible. Never formats text. |
| `Dispatch` | `kind`, `slot` (or -1), `a`, `b` (int arguments), `count` (for coalescing), `bornAt`. | Pooled in a fixed ring inside `Terminal`; never allocated per event. |
| `DispatchKind` | Enum: template key, priority, coalescing rule. A new event is a constant here, a key in `ui.properties`, and - only if the fact is new - one reporter. | Mirrors the house rule for kinds (G0008): a constant and a definition, not a class per event. |
| `Terminal` | Bounded queue plus the two visible lines and their typewriter state. `offer(kind, slot, a, b)`, `advance(frameDt)`, `visible(i)`, `reset()`. | Pure, frame-time driven, fully tested. |

### 8.3 Dispatch catalogue (initial)

| Kind | Fact it reads | Priority | Coalesce | Default copy (`ui.properties`) |
|---|---|---|---|---|
| `MISSION_STARTED` | `worldSerial` changed | high | - | `Mission started` |
| `HOSTING` | this machine hosts, on world bind | high | - | `Hosting - room {code}` |
| `PLAYER_JOINED` | slot present for the first time since bind | normal | - | `{player} joined` |
| `WEAPON_ACQUIRED` | slot's weapon kind changed | low | per slot, newest wins | `{player}{weapon} acquired` |
| `TURRET_CREWED` | slot posture → aboard | low | per slot | `{player}Turret crewed` |
| `POINT_TAKEN` | points taken rose | normal | count | `Control point taken {a}/{b}` |
| `WAVE_INBOUND` | waves sent rose | normal | - | `Wave {a}/{b} inbound` |
| `REINFORCED` | troopers standing rose | low | sum | `Reinforcements +{count}` |
| `SQUAD_LOSS` | troopers standing fell | low | sum | `Squad losses {count}` |
| `NEST_DESTROYED` | nests standing fell | normal | count | `Nest destroyed` |
| `OBJECTIVE_MET` | a required objective bit rose | normal | - | `{objective} - done` |
| `MISSION_COMPLETE` | all required bits met (rising edge) | high | - | `Mission completed` |
| `COMMANDER_DOWN` | slot posture → casualty | high | per slot | `{player}Commander down` |
| `COMMANDER_LOST` | slot present → absent | high | per slot | `{player}Commander lost` |
| `COMMANDER_BACK` | slot absent → present, and it had a commander earlier since bind (the first time is `PLAYER_JOINED`) | normal | per slot | `{player}Commander back` |

`{player}` is empty while one commander is on the field and `P2 ` (and so on) once there are more.
`{weapon}` and `{objective}` resolve through `ui.properties` keys named after the enum constants
(`ui.weapon.FLAMER = Flamethrower`), so no string is built from `toString()`.
Troopers standing rises on a reinforcement and falls on a death; a casualty is still standing in
this count, so a man shot down and stabilised files nothing - the tally and the world show that.

### 8.4 Terminal behaviour

```
offer(kind, slot, a, b):
    if a pending or visible dispatch has the same kind and slot and was born
       less than coalesceSeconds ago and the kind coalesces:  merge (count++, a/b per rule); return
    if queue full: evict the oldest dispatch of the lowest priority present
                   (a high dispatch is evicted only by a newer high one)
    enqueue
advance(dt):
    typewriter: reveal newest line at charsPerSecond - a high dispatch at highCharsPerSecond (0 = at once);
                cursor blinks every blinkSeconds while typing and for cursorLingerSeconds after, then stops
    drop pending low and normal dispatches born more than maxAgeSeconds ago (a high one always waits)
    if newest line fully shown and held >= minHoldSeconds and queue not empty:
        push newest up to "older" (dimmed), take the highest-priority, oldest pending as newest
reset():  on a new world (restart, heal, first world online) - clear everything
```

- **World bind is silent.** When `worldSerial` changes, every reporter first takes the new readout as
  its baseline without reporting, then `MISSION_STARTED` (and `HOSTING`) are filed. A peer dropping
  in mid-level therefore does not announce every point already taken.
- Frame time, not simulation time: the terminal keeps typing while the game is paused, and receives
  nothing, because a paused world changes nothing.
- Lines are not faded by age; the terminal always shows the last two events (pillar 5: nothing the
  player needs disappears on a timer he cannot see).
- **A high dispatch appears at once.** `Commander down` typed at 45 characters a second arrives half
  a second late, which is the half second that mattered; the typewriter is for news, not for alarms.
- **Stale news is dropped before it is shown.** Eight pending dispatches at `minHoldSeconds` each are
  ten seconds of backlog after a big fight - `Reinforcements +2` read ten seconds after the crate is
  noise. Low and normal dispatches that waited longer than `maxAgeSeconds` go; a high one never does.

### 8.5 Extending it - the contract for a future task

1. New fact the readout does not carry → add fields to `Readout` / `PlayerReadout` and read them in
   `ReadoutWriter` (the only reader). Test in `ReadoutWriterTest` on an `AlphaWorlds` world.
2. Add a `DispatchKind` constant and its `ui.properties` key.
3. Report it: an existing reporter if the fact belongs to one, else one new `Reporter`. Register it in
   the reporter list `ScenarioScreen` builds - that list is the whole registry.
4. `TextFitTest` picks the template up automatically.

No painter, layout or bar code changes. If step 1 needs the simulation to publish something new,
that is a `sim` change: `feature` then `new-system`, and a component rather than a message (G0004).

---

## 9. Tally - per-life run metrics (a `sim` feature)

A gameplay change: it decides what counts as a kill. Run the `feature` skill first; it will ask the
questions in section 15 marked *tally*. What follows is the proposed shape.

- `Tally` component (`sim.component`), pooled, on every commander from spawn: `int kills`, `int goals`,
  `int stabilised`. `reset()` clears all three. One line in `Components.ALL`; snapshot carries it; a
  drop-in peer sees the right numbers at once.
- **D1 makes reset structural:** death reaps the entity, respawn spawns a new one. No code resets a
  tally, and a test proves it (`TallyTest.startsFromNothingWhenHeComesBack`).
- **Credit is a slot, never an entity** (G0016): shots, strikes and throws carry `creditSlot` (`-1` =
  nobody's). `Damage` accumulates the credit alongside the amount, and when two players' blows land on
  one tick a fixed rule picks one - proposed: the higher slot, the rule orders already follow (G0028
  invariant row) - never whichever the collision grid visited first. `Deaths.kill` - already the single writer of a death - increments the commander in
  that slot, found through the commanders family by slot. One place reads the credit; every source
  goes through it.
- `stabilised`: incremented where a stabilise completes, for the commander whose action finished it.
- `goals`: proposed - a control point taken while he stood inside its radius. Open question.
- Pillar 3: shown, never spent. Do-not-build list for the record: a score, a currency, anything that
  buys; a tally on the level entity (D1); credit by `Entity` reference.

Tests (names in house style): `TallyTest.creditsTheKillToTheSlotWhoseShotLandedIt`,
`.creditsNothingForAKillNoPlayerCaused`, `.startsFromNothingWhenHeComesBack`,
`.countsAStabiliseForTheManWhoFinishedIt`, `.countsAPointTakenWhileHeStoodOnIt`; plus the existing
`SimulationDeterminismTest` and `ComponentResetTest` covering the new component.

---

## 10. Commander animation - the puppet

**Puppet**: the layered sprite a look is drawn as - shadow, legs, torso, gun - posed every frame from
published facts alone. (Not *rig*: `CameraDirector` already calls itself one.)

### 10.1 Facts it needs

| Fact | Where it comes from | New? |
|---|---|---|
| look, variant, state, facing (= aim for a commander), heat, overheated, charge, chargeLook | `Appearance` | existing |
| movement this tick | `Position - PreviousPosition` (the renderer already reads both to interpolate) | existing |
| simulation time | `tick` and render alpha | existing |
| **ground covered** | `Appearance.travelled`, world units, accumulated by `AppearanceSystem` as `|Position - PreviousPosition|` each tick. Distance, not time, for the reason `Weave` gives: feet never slide when a unit is slowed, shoved or stuck. | **new field** |
| **shot recency** | `Appearance.fired`, `Weapon.remainingSeconds / cooldownSeconds` (0 with no weapon or no cooldown): 1 on the tick a shot leaves, falling to 0 over the cooldown (check that the gun's clock is set to its cooldown on the firing tick). Drives the torso's fire frame, the muzzle flash and the portrait's glow. | **new field** |

Both are published by `AppearanceSystem` (position 44, after everything that moves or fires) and
cleared in `Appearance.reset()`. `Weave.travelled` is the same measure with a spawn-seeded offset:
the implementer checks whether one accumulation can serve both before writing a second.

Why published rather than kept in the renderer: a renderer holding per-entity state across frames
holds it against a pooled `Entity` (G0016's hazard), and keying it by slot would be the renderer
asking what an entity is (G0003). A published fact is also in the snapshot, so a joining peer draws
the same stride.

### 10.2 State matrix

Posture first (from `Appearance.state`, highest precedence first), then locomotion and overlays only
while standing.

| `state` | Posture | Legs | Torso | Gun | Marks kept |
|---|---|---|---|---|---|
| `ABOARD` | hidden | - | - | - | none; the turret is drawn |
| `DYING` | down | - | `down` frame | dropped | charge ring. The dying animation needs a published throes fraction - scaffolding row stays. |
| `CASUALTY` | down | - | `down` frame | dropped | bleed-out ring (charge) |
| `GRAPPLING` | grapple | idle | `grapple` frame | lowered | - |
| `STAGGERED` | stagger | idle | `stagger` frame | lowered | - |
| `NORMAL`, `SLOWED` | stand | locomotion table | aim clip | aim | heat arc, order ring, charge core, `StateTint` for slowed |

Overlays while standing: `fired > graphic.puppet.commander.fireFrameAt` → torso `fire` frame, and a
muzzle flash at the gun's tip while `fired > flashAt`. `chargeLook == MENDING` and `charge > 0` →
torso `kneel` frame (he is stabilising someone). Strike and throw are not published on the
commander's `Appearance`; their frames wait for a fact (section 15).

Locomotion (standing only):

| Condition | Legs clip | Legs direction | Frame |
|---|---|---|---|
| `|movement| <= moveEpsilon` | idle | the torso's sector | idle loop on simulation time |
| moving, angle between movement and aim `<= backpedalDegrees` | walk | sector of movement | `floor(frac(travelled / cycleLength) x walkFrames)` |
| moving, angle `> backpedalDegrees` | backpedal | sector of **-movement** | the walk frame, reversed |

Torso: sector of the aim. Gun: the **exact** aim angle, rotated freely about the hand pivot - the
sectors keep the art cheap, the free gun keeps 360-degree aim readable (pillar 5, and the facing
spoke's job). Gun drawn **behind** the torso when the aim points up-screen, in front otherwise.

### 10.3 Directions

- 8 sectors, 0 = east, counter-clockwise: `sector = floor((angleDeg + 22.5) / 45) mod 8`. Trigonometry
  is allowed here - `ui` and `core` are not `sim` - but nothing downstream feeds back into the tick.
- Authored: E, NE, N, SE, S. Mirrored: W from E, NW from NE, SW from SE
  (`graphic.puppet.commander.mirror`). Handedness flips when mirrored; accepted for placeholders.
- No hysteresis at sector edges. It would need per-entity memory (see 10.1); the free gun already
  carries the exact aim, so a torso flipping between neighbours only when the aim sits on the edge is
  a one-frame change.

### 10.4 Drawing

- `PuppetPoser` (ui, pure): `pose(appearance fields, dx, dy, simTime, spec, out PuppetPose)`. No
  allocation; writes into a reused `PuppetPose`. This is where every rule above lives and is tested.
- `PuppetLibrary` (core): per `Look`, the atlas regions for each clip, sector and frame, resolved and
  **validated once at load**. A look with no complete set has no puppet and is drawn as today's
  circle - the fallback is per look, logged once.
- `PuppetRenderer` (core): for each drawable whose look has a puppet, draw shadow, legs, torso and gun
  (order per 10.2) with the sprite batch, tinted by the player's commander colour; everything else is
  handed to `WorldRenderer` unchanged. Existing marks (heat arc, order ring, charge) are drawn after,
  by the shape pass. The facing spoke is not drawn for a puppeted look.
- Depth: layers by look (`graphic.layer.<look>`: ground / bodies / **heroes** / air), chosen by
  `Look`, never by class (G0003). Circles keep today's order; the commander's look sits in *heroes*,
  above every other body, and puppets in that layer are y-sorted among themselves (insertion sort on
  a reused index array, no allocation). Two reasons against y-sorting puppets in among the circles:
  his man must never be hidden behind a trooper (pillar 2), and interleaving `ShapeRenderer` circles
  with `SpriteBatch` sprites flushes a batch at every switch - one flush per trooper standing near
  him. When a second look gets a puppet (a named second user), draw circles through the sprite batch
  from an atlas region and sort once.
- **The shadow tells the truth.** Collision is circles (ADR 0007); a sprite overhangs its circle, a
  shadow does not. The shadow is an ellipse drawn at the body's radius, so the player reads where he
  can be hit from the one part of the sprite that never lies.
- **The flash is where the shot is.** The muzzle flash is drawn where the simulation puts the shot on
  the tick it leaves, from the same numbers the simulation uses; the gun art is drawn to reach that
  point. If they disagree the art changes, never the simulation.
- `SharpPixelShader`: linear filtering plus the antialiased-nearest UV snap
  (`uv = (floor(p + 0.5) + clamp((p - floor(p + 0.5)) / fwidth(p), -0.5, 0.5)) / texSize`, with
  `p = uv x texSize`). Atlas packed with `duplicatePadding` so linear sampling never bleeds a
  neighbour. Mipmaps on for the far zoom's minification. If the shader fails to compile: nearest
  filtering, logged once.
- World animation clock: `simTime = (tick + alpha) x tickSeconds`. A paused game is a frozen picture.

### 10.5 Portrait

`PortraitPoser` (ui, pure) from `PlayerReadout` alone - with no chip damage there is no hurt timer
to keep:

| Condition (first match) | Pose | Frames |
|---|---|---|
| absent, respawn pending | `kia` | static; the seconds are in the status field |
| posture casualty | `down` | - |
| overheated | `strain` | - |
| `fired > glowAt` | `fire` | teeth plus muzzle glow |
| otherwise | `idle`, `blink` every `blinkSeconds` | blink phase offset by slot, so four faces never blink together |

UI time, not simulation time: the face keeps blinking in the pause menu. Frames are greyscale and
tinted with the player's colour, so one set serves eight players.

### 10.6 Placeholder art (low cost, replaced later)

Every frame is a **32 x 32 texel canvas with the feet pivot at (16, 8)**, greyscale, transparent
background; TexturePacker's whitespace stripping keeps the offsets, so every frame aligns without
per-frame anchors.

**Texel density** (`graphic.art.texelsPerWorldUnit` 6) against the view of 5.2: at 1080p a world unit
is 9.1 screen px near and 4.2 far, so a texel is 1.5 px near and **0.7 px far** (1440p: 2.0 / 0.9;
Steam Deck: 1.2 / 0.5). Below one pixel per texel, one-texel detail flickers in and out as he moves.
Rule for every frame, placeholder or final: nothing that carries meaning - the front notch, the gun,
the legs' gap - is narrower than **two texels**, and the silhouette reads in solid black at 16 px.
Whether 6 is the right density at all is Q31.

| Source path (`assets-raw/`) | Frames |
|---|---|
| `puppet/commander/shadow.png` | 1 |
| `puppet/commander/legs_idle_<s>.png` | 1 per authored sector `e ne n se s` |
| `puppet/commander/legs_walk_<s>_<i>.png` | `walkFrames` (6) per sector |
| `puppet/commander/torso_aim_<s>.png`, `torso_fire_<s>.png`, `torso_kneel_<s>.png` | 1 each per sector |
| `puppet/commander/torso_down.png`, `torso_grapple.png`, `torso_stagger.png` | 1 each |
| `puppet/commander/gun.png` | 1, pointing east, pivot at the grip |
| `puppet/commander/flash_<i>.png` | 2 |
| `ui/portrait/commander_<pose>.png` | 1 per pose, 32 x 32 - the largest that fits the smallest bottom bar (40 UI px less padding) |

Placeholders are generated, not drawn: a short script (Python with Pillow in the sandbox, or a
throwaway Java main) that paints silhouettes - an oval torso with a notch for the front, two
rectangle legs swinging by frame, a bar for the gun. The generator is a question for Mateusz
(section 15): where it lives, or whether only its output is committed.

---

## 11. Assets and configuration

### 11.1 New asset constants (`Assets`)

`UI_FONT = "ui/fonts/terminal.ttf"`, `UI_SKIN = "ui/skin.json"`, `UI_STRINGS = "i18n/ui"`;
atlas regions stay inside `GAME_ATLAS`. Each is queued with the existing `queueIfPresent`, so a
missing file logs and falls back (section 13).

### 11.2 Proposed keys

None of these exist yet. Each stage adds the ones it needs and lists them in its summary.

`graphic.properties` - rendering only:

| Key | Default | What it does to the feel |
|---|---|---|
| `graphic.view.aspectMin` / `aspectMax` | `1.6` / `2.39` | Range the view's width-over-height is kept in (5.2). Wider sees further left and right; equal values pin it, as the first draft did. Validation refuses min > max. |
| `graphic.virtualHeight` | `90` (kept) | World units from top to bottom of the near view: the camera distance. |
| `graphic.virtualWidth` | **removed** | Derived from the view rect. |
| `graphic.ui.canvasHeight` | `360` | UI pixels from top to bottom of the screen; smaller means chunkier UI. Integer divisors of common screens: 360. |
| `graphic.ui.scale` | `0` | UI scale option; 0 = the largest whole scale that fits. Smaller = smaller HUD and a bigger view. |
| `graphic.ui.padding` | `4` | UI px between bar edge and content. |
| `graphic.ui.fontSize` / `leading` | `8` / `2` | The font's native pixel size, and UI px between lines. Both feed the bars' heights (5.2). |
| `graphic.ui.motionScale` | `1` | Every UI duration and amplitude; 0 = reduced motion (6.4). |
| `graphic.ui.flashScale` | `1` | Every UI flash; 0 = flashes become steady changes (7.4). |
| `graphic.ui.motion.pageSeconds` / `scrimSeconds` | `0.12` / `0.08` | Page slide and scrim fade. |
| `graphic.ui.scrimAlpha` | `0.55` | How dark the paused world goes. Lower = more of the fight visible. |
| `graphic.hud.overlay.widthShare` | `0.34` | Share of the top bar the dev overlay panel takes. |
| `graphic.hud.terminal.lines` | `2` | Visible dispatches. Feeds the top bar's height. |
| `graphic.hud.terminal.charsPerSecond` / `highCharsPerSecond` | `45` / `0` | Typewriter speed; a high dispatch at 0 = at once. |
| `graphic.hud.terminal.minHoldSeconds` | `1.2` | Least time a dispatch stays newest. Lower = busier terminal. |
| `graphic.hud.terminal.maxAgeSeconds` | `4.0` | A low or normal dispatch still waiting after this is dropped. Lower = fresher, sparser. |
| `graphic.hud.terminal.queue` | `8` | Pending dispatches kept. |
| `graphic.hud.terminal.coalesceSeconds` | `2.0` | Window in which repeats merge into one line with a count. |
| `graphic.hud.terminal.blinkSeconds` / `cursorLingerSeconds` | `0.5` / `1.0` | Cursor blink, and how long it keeps blinking after the line is typed. |
| `graphic.hud.goal.minHoldSeconds` | `1.0` | Least time a goal text stays before it may change. |
| `graphic.hud.color.text`, `.textDim`, `.alert`, `.overlayFrame`, `.bar` | `e8e8e8ff`, `8a8a8aff`, `ff3030ff`, `e02020ff`, `000000ff` | 7.0. Under `graphic.hud.color`, not `graphic.color`, which is keyed by `Look`. Player colours and heat colours are **not** repeated here - the world's keys are read. |
| `graphic.hud.panel.alarmSeconds` / `alarmShake` | `0.15` / `1` | Frame flash and shake (UI px) as he goes down, dies or overheats. |
| `graphic.hud.alert.pulseHz` | `2` | DOWN / OVERHEAT / KIA pulse. Validation refuses above 3. |
| `graphic.hud.portrait.size` | `32` | UI px. Feeds the bottom bar's height. |
| `graphic.hud.portrait.blinkSeconds`, `.glowAt` | `3.5`, `0.5` | Face timing. |
| `graphic.hud.reticle.size` | `9` | Reticle texels, drawn at `s`. |
| `graphic.art.texelsPerWorldUnit` | `6` | Sprite detail per world unit; a commander (radius 2) is 24 texels across. Q31. |
| `graphic.puppet.commander.walkFrames` | `6` | Frames per walk cycle. |
| `graphic.puppet.commander.cycleLength` | `9` | World units walked per full cycle (two steps). Shorter = busier legs. |
| `graphic.puppet.commander.backpedalDegrees` | `100` | Past this angle between aim and movement, he walks backwards. |
| `graphic.puppet.commander.moveEpsilon` | `0.02` | World units per tick below which he stands. |
| `graphic.puppet.commander.mirror` | `true` | Draw west-facing sectors mirrored. |
| `graphic.puppet.commander.idleSeconds` | `0.6` | Idle breathing period. |
| `graphic.puppet.commander.fireFrameAt`, `.flashAt` | `0.7`, `0.85` | How much of the cooldown the fire pose and the flash last. |
| `graphic.layer.<look>` | `ground` / `bodies` / `heroes` / `air` | Draw layer per look (10.4); `commander` is `heroes`. |

Removed from the first draft: `graphic.view.aspect` (now a range), `graphic.ui.barMinHeight` (derived
from what the bars hold), the six per-line HUD colours (now `text` / `textDim` by tier).

`controls.properties` - menu context (bindings keep the file's own naming):

| Key | Default |
|---|---|
| `controls.ui.key.confirm` / `confirmAlias` | `Enter` / `Space` |
| `controls.ui.key.back` / `backAlias` | `Escape` / `Backspace` |
| `controls.ui.pad.confirm` / `back` / `tabPrev` / `tabNext` | `SOUTH` / `EAST` / `L1` / `R1` |
| `controls.ui.nav.stickThreshold` | `0.5` |
| `controls.ui.nav.repeatDelaySeconds` / `repeatIntervalSeconds` | `0.35` / `0.09` |
| `controls.ui.nav.pointerWakePixels` | `4` |

`game.properties`: nothing. The tally has no tunables.

---

## 12. Roadmap - stages and atomic tasks

Every task: tests first where there is logic (general tests → implementation → specific cases found
while implementing); `.claude/scripts/verify.sh` green before it is called done; no commit.

**Order, and why.** Risk and playtest value first, polish last. The letterbox changes how every screen
looks, so it is built and judged (gate A) before anything is laid out inside it. The HUD's tier-1 and
tier-2 content comes next because it needs no new simulation facts and is what a playtest feels. The
menu machinery is proved on the two smallest consumers - the pause menu and its confirm - before eight
main-menu pages are built on it. The tally waits for four design answers; portrait and puppet are art.
Stage 9 depends only on stages 1-2 and may be pulled forward if Mateusz wants the sprite sooner.

```
recommended order   0 → 1 → 2 ═ GATE A ═ 3 ═ GATE B ═ 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11
hard dependencies   2 needs 1 · 3 and 4 need 2 · 5 needs 4 · 6 and 8 need 3 · 7 needs 3 and Q11-Q14
                    9 needs only 1-2 · 10 needs 5 and Q4 · 11 needs the rest
```

### Stage 0 - Records and words (no code)

| Task | Do | Done when |
|---|---|---|
| T0.1 | Write ADR 0015 "The interface logic is its own module" (section 16) and add its row to `docs/adr/README.md`. | Record has the six fields; Do-not-build names in the table. |
| T0.2 | Write G0043 "The first atlas is the commander's", superseding G0003's temporary half for looks with a puppet. Update G0003's status cell. Edit the "Visuals stay minimal" rule in `alpha-phase.md` to point at it. | Records agree; nothing else in docs still says "no placeholder sprites" unqualified. |
| T0.3 | Glossary, **only the words stages 1-5 use**: page, modal, entry, focus, modality, letterbox, UI canvas, top bar, bottom bar, view (as the rect between the bars - extend the existing sense), scrim, paused banner, reticle, player panel, status (the panel's), readout, proving ground. Read each section end to end first. Terminal, dispatch, reporter (stage 6), tally (7), portrait (8), puppet and pose (9) and planet (Q9) land with their stage, so a stage that is dropped leaves no orphan word. | Every term defined once; *arena* untouched. |
| T0.4 | Input map in `docs/design/README.md`: a "pages and modals" context; `Esc` in a level → pause menu. Harness table in `alpha-phase.md`: `Esc`, `F1`, `P` (pauses without a menu). | Same change as the code in T4.5 if done later - say which. |
| T0.5 | Propose (do not apply) a CLAUDE.md edit: `ui` in the Modules table and the dependency line; a "Where to look" row for `docs/ui/`. | Mateusz has the diff to accept. |

G0044 (the tally) moved to T7.1: it cannot be written before its questions are answered.

### Stage 1 - The `ui` module

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T1.1 | - | `ui/build.gradle` (`api project(':sim')`, ArchUnit for tests), `settings.gradle` include, `core` depends on `ui`. One placeholder class so the module compiles. | - | `verify.sh` green. |
| T1.2 | `unit-tests` | `UiArchitectureTest`: the "must not" column of 3.1. Watch every rule fail against a probe class, then delete the probes. | the rules themselves | Each rule seen failing once. |

**Stop point:** the build has a new empty module and a wall around it.

### Stage 2 - Letterbox, UI canvas and a greybox HUD

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T2.1 | `unit-tests` | `ScreenLayout`, `LayoutSpec`, `Rect`, `HudMetrics` (ui.layout). | `ScreenLayoutTest`: every row of 5.2, plus `.neverScalesTheCanvasByAFraction`, `.keepsTheViewInsideItsAspectRangeOnEveryScreen`, `.givesHeightBeyondTheRangeToTheBars`, `.honoursAUiScaleSmallerThanTheLargestThatFits`, `.survivesAZeroSizedWindow`; `HudMetricsTest.sizesEachBarFromWhatItHolds`. | Table rows pass. |
| T2.2 | - | `GraphicConfig`: `graphic.view.*` and the `graphic.ui.*` layout keys, fallbacks logged; remove `graphic.virtualWidth` (derived). `ConfigDefaultsTest`: refuses `aspectMin > aspectMax`. | config tests | Keys listed in summary. |
| T2.3 | - | `PixelViewport` (core): applies a `ScreenLayout` result - canvas camera at `s`, offsets. `Hud` grows into the canvas owner: its viewport becomes `PixelViewport`, its font the pixel font with the built-in font as fallback. Its existing `line()` API keeps working for `MenuScreen` and `DebugOverlay`. | - (GL) | Menu and overlay text still draw; crisp at 1080p and 1440p. |
| T2.4 | - | `ScenarioScreen`: world viewport set to the view rect, world size per 5.3; bars cleared to `graphic.hud.color.bar`; `DebugOverlay` still full screen for now. Resize recomputes the layout. The pointer still aims from over a bar. | `CameraDirectorTest` unchanged and green | Playable, letterboxed. |
| T2.5 | - | **Greybox**: a throwaway painter fills every slot of 7.1 and 7.2 with its longest English text and draws four player panels, so the layout is judged full rather than empty. Stage 3 deletes it. | - (GL) | Every slot visible at 1080p and in a 1280 x 800 window. |

**Stop point and GATE A** - Mateusz on Windows at 1080p, 1440p, a 1280 x 800 window (the Deck's shape)
and an ultrawide if one is at hand. Answers **Q1** (aspect range), **Q2** (maps narrower than the view)
and **Q3** (the font - its metrics feed every fit from here on), and one check: *is every slot readable
at arm's length?* Changing any of these after stage 3 means refitting every slot.

### Stage 3 - Readout, top bar and player panel

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T3.1 | `unit-tests` | `Readout`, `PlayerReadout`, `ReadoutWriter` - every fact of 7.2 but the tally. `Scenario.planet()` default method returning `""`. | `ReadoutWriterTest` on `AlphaWorlds`: `.copiesEachLocalCommanderIntoHisOwnSlot`, `.countsPointsTakenAndWavesSent`, `.leavesAnEmptySlotAbsent`, `.keepsNothingFromAPreviousWorld`, `.publishesHisBleedOutWhileHeIsDown` | `perf-audit`: no allocation per frame. |
| T3.2 | `unit-tests` | `GoalLine`, `TextFit`, `PanelLayout`. | `GoalLineTest.namesTheFirstUnmetObjective`, `.saysCompletedWhenEveryRequiredObjectiveIsMet`, `.goesBackToTheGoalWhenAnObjectiveIsLostAgain`, `.holdsATextForItsMinimumTime`; `TextFitTest` (four panels at 640, degradation at 512, pseudo-locale); `PanelLayoutTest.fitsFourPlayersInQuartersOf160`, `.dropsOnlyThePortraitOnANarrowCanvas`, `.keepsEveryPanelWhereItWasWhenAPlayerJoins` | - |
| T3.3 | - | `TopBarPainter`: mission (dim), goal (bright), terminal area empty; `DebugOverlay.render` into the overlay rect with ranked lines; `F1` cycles panel / expanded / off. | `HarnessControlsTest`/`ScenarioSessionTest` for the cycle | Matches the mock-up layout. |
| T3.4 | - | `BottomBarPainter`: four seat quarters - frame and label, status, weapon and heat, portrait as a block in his colour, tally row reserved; alarm flash and shake on the UI clock. Deletes the greybox painter. Rewrites the alpha-phase row "the commander's gun is named only in the F1 overlay". | - (GL) | Four pads on one PC show four panels; the keyboard player's `P1` is cyan. |
| T3.5 | - | `ReticlePainter` and the system cursor hidden in `UI:play` (7.5). | - (GL) | Reticle readable on every biome of `Adventure`. |

**Stop point and GATE B** - Mateusz plays `Adventure` with one and with four players. Two checks:
*can you tell you are down, and how hot your gun is, without looking at the bar?*, *can each of four
players find his panel at once?* and *does anything in the bars pull
your eye mid-fight?* Answers **Q10** (confirm), **Q15**, **Q22**, **Q24**, **Q30**.

### Stage 4 - Flow, navigation and the pause menu

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T4.1 | `unit-tests` | `UiPage`, `HudSlot` enums with `id()` = the `UI:` ids of `menu_layout_agent.md`. `UiFlow`, `UiIntent`, `UiEffect`: the whole transition table (spec section 5), derived states via `observe(...)`. | `UiFlowTest.goesBackOnePageAtATime`, `.neverQuitsOnBackFromTheMainPage`, `.opensTheMenuOnlyOnTheMachineThatAskedForIt`, `.closesTheMenuWhenAnybodyResumes`, `.showsTheBannerAndTakesNoFocusWhenSomebodyElsePauses`, `.hidesRestartFromAPeerThatDoesNotHost`, `.cannotReachGameOver` | Table covered row by row. |
| T4.2 | `unit-tests` | `NavInput`, `NavRepeat`, `InputModality`, `FocusMemory`, `ReleaseLatch`. | `NavRepeatTest`, `InputModalityTest.switchesToThePointerWhenTheMouseMovesFarEnough`, `.ignoresATwitchUnderTheWakeDistance`, `.restoresTheLastFocusedEntryWhenAKeyIsPressed`; `ReleaseLatchTest.ignoresAPressHeldAcrossAContextChangeUntilItIsReleased`, `.letsAFreshPressThroughAtOnce` | - |
| T4.3 | - | `NavInputReader` (core): keys via `KeyPresses`, mouse, every seated `Gamepad` through the port; `controls.ui.*` in `ControlsConfig`. `InputReader` applies the latch and sends neutral input for his slot while a menu is open. | `ControlsConfig` tests for the new keys | - |
| T4.4 | - | `UiStrings` (I18NBundle with the `#key` fallback), `UiSkin` (skin file, else a programmatic skin from a generated 1 x 1 pixel), `MenuStage` - enough for modals. | - (GL) | - |
| T4.5 | - | `UI:play/pause`, `UI:play/leave`, scrim, paused banner, docked (6.5); room code and addresses on the pause menu while hosting (`Invitation`). `Esc` / `START` → menu; `HarnessControls` loses `LEAVE` on `Esc`; `P` stays a menu-less pause. Focus loss and pad unplug per Q7 and Q27. Docs of T0.4 in the same change. | `HarnessControlsTest` updated | Pausing on one machine shows the banner on the other and the menu only where it was asked for; resuming from either clears both; clicking RESUME fires nothing (two copies, `docs/workflow.md`). |
| T4.6 | - | `UI:connecting` restyled from `DebugOverlay.renderWaiting`. | - | - |
| T4.7 | `unit-tests` | `UiSpecTest`: reads `docs/ui/menu_layout_agent.md`, compares its `UI:` ids with `UiPage` and `HudSlot`. | itself (probe: delete a row, watch it fail) | Doc and enums locked together. |

**Stop point:** the game has a pause menu; leaving asks first; no press leaks into the game.

### Stage 5 - The main menu

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T5.1 | `unit-tests` | `MenuTree`, `MenuEntry` built from the spec's tables. | `MenuTreeTest` | Every page's entries match the spec. |
| T5.2 | - | `MenuScreen` becomes the page stack: MAIN, CAMPAIGN (today's list, `MenuSelection` reused, focus on the last scenario played), PROVING_GROUND and OPTIONS placeholders, ONLINE → JOIN (today's `JoinScreen`, pad entry per Q6) / HOST (list, confirm hosts), EXIT confirm. `H`/`J` on the list retire; the harness table says so. | - (GL) | Every page reachable by keyboard, mouse and pad; two confirms from launch to a level. |
| T5.3 | `unit-tests` | Page motion (6.4): push and pop within `pageSeconds`, finished at once by any input. | `PageMotionTest.finishesAtOnceWhenThePlayerActsDuringIt`, `.doesNotMoveAtAllWithMotionOff` | - |

**Stop point:** a real main menu; everything that worked from the scenario list still works.

### Stage 6 - Reporters and the terminal

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T6.1 | `unit-tests` | `Dispatch`, `DispatchKind`, `Terminal`. Glossary: terminal, dispatch, reporter. | `TerminalTest.showsTheTwoLatestDispatches`, `.typesTheNewestAtItsRate`, `.showsAHighDispatchAtOnce`, `.stopsBlinkingOnceTheLineIsTyped`, `.holdsANewLineBeforeTheNextPushesItUp`, `.mergesRepeatsInsideTheWindow`, `.dropsANormalDispatchThatWaitedTooLong`, `.evictsTheOldestOfTheLowestPriorityWhenFull`, `.neverEvictsAHighDispatchForALowerOne`, `.startsEmptyOnANewWorld` | No allocation per offer. |
| T6.2 | `unit-tests` | The reporters of 8.3; silent bind. | `ReportersTest.reportsAPointOnceWhenTheCountRises`, `.reportsNothingOnTheFrameAWorldArrives`, `.reportsTwoWavesThatArriveInOneFrame`, `.prefixesThePlayerOnlyWhenThereIsMoreThanOne` | - |
| T6.3 | - | Fold `Announcements`: the wave half becomes `WaveReporter`; the action-prompt half stays a console line (G0021 wants it beside the commander, not in the terminal) but is computed by a reporter writing to `Log` rather than by a second edge-detector. `ScenarioPlay` loses `newlyArrivedWave`/`totalWaves` if nothing else calls them. | existing `OnlineSessionTest`, `ScenarioSessionTest` adjusted | One edge-detection mechanism in the client. |
| T6.4 | - | Terminal lines painted in the top bar: dim older, bright newer with cursor. `HOSTING` dispatch. | - | The alpha-phase rows for the wave line and the room code are rewritten. |

**Stop point:** the terminal narrates the level.

### Stage 7 - Tally (`sim`)

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T7.1 | `feature` | Answer the *tally* questions of section 15 with Mateusz; write G0044; invariant rows; glossary: tally. | - | Decisions written. |
| T7.2 | `new-system` | `Tally` component; `creditSlot` on the sources; credit through `Damage` and `Deaths.kill`; stabilise and goal credit where those complete. No new system unless the design pass needs one; if it does, register it per `docs/simulation-order.md` and say why. | section 9's list | `verify.sh` green; determinism tests green. |
| T7.3 | - | `ReadoutWriter` copies the tally; keeps the last life's numbers while a respawn is pending. | `ReadoutWriterTest.keepsTheLastLifeUntilHeComesBack` | - |
| T7.4 | - | The tally in the panel's reserved slot, full and compact (icon) forms. | `PanelLayoutTest` cases already cover it | - |

**Stop point:** the numbers exist, replay identically and show.

### Stage 8 - Portrait

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T8.1 | `unit-tests` | `PortraitPoser`. Glossary: portrait. | `PortraitPoserTest.glowsWhileHeIsFiring`, `.showsHimDownAsACasualty`, `.strainsWhileHisGunIsOverheated`, `.neverBlinksFourFacesTogether` | - |
| T8.2 | - | Placeholder portrait frames (10.6, 32 x 32); the panel draws them instead of the block. | - | The HUD of both mock-ups is on screen. |

### Stage 9 - The commander's puppet

| Task | Skill | Do | Tests | Done when |
|---|---|---|---|---|
| T9.1 | `new-system` | `Appearance.travelled`, `Appearance.fired` written by `AppearanceSystem`; `reset()` clears them; decide the `Weave.travelled` overlap (10.1). Glossary: puppet, pose. | `AppearanceSystemTest.publishesHowFarAUnitHasWalked`, `.leavesTheDistanceAloneWhileHeStandsStill`, `.publishesAShotThatFadesOverTheCooldown`, `.publishesNoShotForSomethingWithoutAGun` | Determinism green; `perf-audit`. |
| T9.2 | `unit-tests` | `Sector`, `PuppetPose`, `PuppetPoser`. | `PuppetPoserTest.turnsTheBodyWhereHeAimsWhicheverWayHeWalks` (G0002), `.walksBackwardsWhenHeMovesAwayFromHisAim`, `.standsTheLegsUnderTheBodyWhenHeIsNotMoving`, `.picksTheSameFrameForTheSameDistance`, `.hidesHimAboardATurret`, `.laysHimDownAsACasualty`, `.putsTheGunBehindHimWhenHeAimsUpScreen`, `.mirrorsTheWestSectors` | - |
| T9.3 | - | Placeholder generator and art (10.6, two-texel rule); `packTextures`; `pack.json` (duplicate padding, mipmaps). | - | Atlas builds. |
| T9.4 | - | `PuppetLibrary` (validated at load), `SharpPixelShader`, `PuppetRenderer`, the *heroes* layer, shadow at the body's radius, flash at the shot's origin; circle fallback per look. | - (GL) | Commander walks, backpedals, aims 360 degrees, fires, goes down; troopers and aliens unchanged; readable at far zoom. |

**Stop point:** the commander is a sprite; everything else is exactly as before.

### Stage 10 - Options, first cut (blocked on Q4)

| Task | Do |
|---|---|
| T10.1 | `UI:main/options` stops being a placeholder: UI scale, motion, flashes, camera shake - the keys of 7.4, written to wherever Q4 says a player's settings live, never to the shipped `assets/*.properties`. Remapping is a later cut. |

### Stage 11 - Hardening

| Task | Do |
|---|---|
| T11.1 | Walk the edge-case matrix (section 13); a test for every row that can have one. |
| T11.2 | `perf-audit` over `ReadoutWriter`, reporters, `Terminal`, painters, `PuppetRenderer`. Budget: readout, reporters and bar painting together under 0.5 ms of CPU a frame at four players; count batch flushes per frame (one atlas, no Shape/Sprite interleave in the bars). |
| T11.3 | Update `docs/architecture.md` (the `ui` module, the frame order of 3.3), `alpha-phase.md` scaffolding rows retired or rewritten, delete this plan when every stage has landed. |

---

## 13. Edge-case matrix

| # | Situation | Required behaviour | Where | Test |
|---|---|---|---|---|
| E1 | Window minimised - libGDX resizes to 0 x 0 | Keep the last layout; draw nothing new; no division by zero | `ScreenLayout`, `PwtGame.resize` | `ScreenLayoutTest.survivesAZeroSizedWindow` |
| E2 | Window shorter than `canvasHeight` | `s = 1`, canvas = window, text under the legibility floor (logged once). Bars keep what they hold and the view takes the rest; a window too short for both drops the bars - the view is never shrunk to save the HUD | `ScreenLayout` | `.dropsTheBarsBeforeTheViewGoesUnderHalfTheWindow` |
| E3 | Ultrawide or super-ultrawide | View at `aspectMax`, black pillars, bar fill full width, bar content inside the view's span | `ScreenLayout`, painters | table rows |
| E4 | Portrait-shaped window | View at `aspectMin`, the rest of the height to the bars; content anchored at the view's edge | `ScreenLayout`, painters | `.anchorsBarsToTheView` |
| E5 | HiDPI / OS scaling | Layout from back-buffer pixels, not logical | `PixelViewport` | Windows check at 150 % scaling |
| E6 | Resize or fullscreen toggle mid-level | Layout recomputed; camera keeps its target; no GL resource recreated per resize | `ScenarioScreen.resize` | Windows check |
| E7 | Map narrower or shorter than the view | Camera centres (existing); outside the map draws as out-of-bounds ground, never as the letterbox black | `TerrainRenderer` | Windows check on `Duel` |
| E8 | Far zoom | Sprites minified through mipmaps; UI untouched | atlas settings | Windows check |
| E9 | Atlas missing | Circles for everything, programmatic skin, coloured-block portraits; one log line | `Assets`, `PuppetLibrary`, `UiSkin` | run with the atlas renamed |
| E10 | One puppet region missing | That look falls back to its circle; logged once at load, never per frame | `PuppetLibrary` | `PuppetLibrary` validation is load-time |
| E11 | Font missing | libGDX built-in font; `TextFit` switches to measured widths | `Hud` | run with the font renamed |
| E12 | String key missing | `#key` drawn; logged once per key | `UiStrings` | `UiStringsTest` (pure half) |
| E13 | Shader fails to compile | Nearest filtering; logged once | `SharpPixelShader` | - |
| E14 | Long scenario name, planet or weapon copy | Argument truncated first, then template, `...` | `TextFit` | `TextFitTest` over every template |
| E15 | Many events in one frame (a blast kills 30) | Coalesced into counted lines; queue bounded; high priority survives | `Terminal` | `TerminalTest` |
| E16 | Two waves between two frames | Counter rose by 2 → two dispatches, not one | `WaveReporter` | `ReportersTest.reportsTwoWavesThatArriveInOneFrame` |
| E17 | Restart, heal, or first world online | Terminal and reporter baselines reset; `MISSION_STARTED` once; tallies zero because the world is new | reporters, `Terminal` | `.reportsNothingOnTheFrameAWorldArrives` |
| E18 | Drop-in mid-level | Tallies from the snapshot; no burst of old events | `ReadoutWriter`, reporters | `SessionHarness` test |
| E19 | Objective met then lost (a new wave after a clear) | Goal line tells the truth, with minimum hold; no "lost" dispatch spam | `GoalLine` | `GoalLineTest` |
| E20 | Commander dead, respawn pending | Panel shows `kia` and the seconds left; last life's tally until the new one | `PortraitPoser`, `ReadoutWriter` | `.keepsTheLastLifeUntilHeComesBack` |
| E21 | Pad plugged in during a page | Seated as today (app-owned `GamepadSource`); may navigate at once | `NavInputReader` | - |
| E22 | Pad unplugged while it had focus | Focus stays; modality stays until another device acts | `InputModality` | `InputModalityTest` |
| E23 | Mouse twitch while navigating by pad | Below `pointerWakePixels`: ignored | `InputModality` | `.ignoresATwitchUnderTheWakeDistance` |
| E24 | Another machine pauses | Paused banner here - no menu opened, no focus taken; resuming from either machine clears banner and menus on both | `UiFlow.observe` | `.showsTheBannerAndTakesNoFocusWhenSomebodyElsePauses`, `.closesTheMenuWhenAnybodyResumes` |
| E25 | Host leaves while a peer is in the pause menu | Back to `UI:main` with the notice (existing `ended()`) | `UiFlow` | `UiFlowTest` |
| E26 | 5-8 players online, 4 on this machine | Bottom bar shows this machine's seats only; terminal prefixes every player | painters, reporters | `.prefixesThePlayerOnlyWhenThereIsMoreThanOne` |
| E27 | Tally above 999 | `999+` in the panel; the whole number in the pause menu | `TextFit` | `TextFitTest` |
| E28 | Level with no objectives | Goal line empty; no `MISSION_COMPLETE` | `GoalLine` | `GoalLineTest` |
| E29 | Commander aboard a turret | Puppet hidden (turret drawn); portrait idle; `TURRET_CREWED` once | `PuppetPoser`, reporters | `.hidesHimAboardATurret` |
| E30 | Aim exactly on a sector edge | Torso may alternate between neighbours; gun exact - accepted (10.3) | `Sector` | `SectorTest` pins the boundary |
| E31 | Game paused | World animation frozen (simulation time); terminal and portraits keep UI time | puppets, `Terminal` | `PuppetPoserTest` takes time as input |
| E32 | RESUME clicked with the left mouse or confirmed with `SOUTH`; a level started with `Enter`, `Space`, `SOUTH` or a click | Nothing fires, acts or throws until that button is released | `ReleaseLatch`, `InputReader` | `ReleaseLatchTest` |
| E33 | Pointer over a bar in `UI:play` | Still aims, at the world point beyond the view's edge; reticle drawn over the bar | `InputReader`, `ReticlePainter` | Windows check |
| E34 | A big fight files a dozen dispatches | Coalesced; low and normal ones older than `maxAgeSeconds` dropped before shown; high ones shown at once and never dropped | `Terminal` | `.dropsANormalDispatchThatWaitedTooLong`, `.showsAHighDispatchAtOnce` |
| E35 | Four local panels on a 512-wide canvas (4:3) | Quarters of 128: portraits dropped, everything else drawn | `PanelLayout` | `.dropsOnlyThePortraitOnANarrowCanvas` |
| E41 | A pad plugged in mid-fight (a player joins) | His panel appears in his seat's quarter; no other panel moves | `PanelLayout` | `.keepsEveryPanelWhereItWasWhenAPlayerJoins` |
| E36 | A translation 30 % longer than English, with diacritics | Every slot degrades; nothing overflows; no `?` glyph | `TextFit`, font | `TextFitTest` pseudo-locale |
| E37 | Alt-tab or a pad unplugged mid-fight, offline | Pause menu opens (Q7, Q27); online nothing pauses | `ScenarioScreen.pause`, `NavInputReader` | Windows check |
| E38 | Menu opened online before the pause lands | World runs a few ticks under the scrim; his input is neutral; the menu is live at once | `UiFlow`, `InputReader` | `UiFlowTest.opensTheMenuOnlyOnTheMachineThatAskedForIt` |
| E39 | `P` (harness) pauses | Banner, no menu; `.` single-steps with the world visible | `UiFlow`, `HarnessControls` | `HarnessControlsTest` |
| E40 | Commander stands among troopers | He draws above them (the *heroes* layer); his shadow shows his true radius | `PuppetRenderer` | Windows check |

---

## 14. Refined structure of `menu_layout_agent.md`

The file exists beside this one. Its structure, which any edit keeps:

1. **How to use this file** - the `UI:` id scheme, the grep recipe, `UiSpecTest`, labels as i18n
   keys, Do-not-build cells are binding, glossary words and banned words.
2. **Traceability** - every line of the original outline mapped to an id.
3. **Page and modal table** - id, kind, parent, code anchor (`UiPage` constant), implementing class,
   status (built / planned / placeholder / blocked, with the blocking question named).
4. **Pages in detail** - per id: title key, entries (`entry id | label key = default copy | intent |
   enabled when`), default focus, back behaviour, Do not build.
5. **Transition table** - `(from, intent) → (to, effect)`, derived transitions marked.
6. **Input per context** - semantic action × keyboard / mouse / pad, and the modality rules.
7. **HUD slots** - `UI:hud/...` ids, `HudSlot` anchors, position, content, status.
8. **Text rules** - font, truncation, wrap, numbers, missing glyph and key.
9. **Placeholder assets** - source path, region, user, size; the non-atlas files and their fallbacks.
10. **Motion, feedback and accessibility** - the rules every page and slot shares: motion budgets,
   the alert treatment, the legibility floor, never colour alone.

Id grammar: `UI:` + lowercase path segments joined by `/`, kebab-case within a segment
(`UI:main/proving-ground`). Enum constant: upper snake of the last segment unless ambiguous
(`UiPage.PROVING_GROUND`, `UiPage.ONLINE_JOIN`). Each implementing class carries the id on a javadoc
line of its own, so grep finds the page from either end.

---

## 15. Open questions for follow-up sessions

Ask rather than invent (CLAUDE.md). Grouped by the stage that needs the answer; a **gate** stage
means Mateusz answers it at that gate. Q22 onwards were added by the review (section 18).

| # | Stage | Question | Proposed default until answered |
|---|---|---|---|
| Q1 | 2 gate A | View aspect: a **range** (the review's proposal, 1.6-2.39: 16:9 unchanged, 16:10 and the Deck get a taller view) or pinned (2.39 war film, or about 3:1 as the mock-ups' bar heights imply)? Keep 90 world units tall and see further sideways, or keep 160 wide? | `1.6`-`2.39`, `virtualHeight 90`. All single keys. |
| Q2 | 2 gate A | Small scenarios on 160-wide maps will show ground beyond the map edge. Widen their maps (content), or accept? | Accept and draw it as out-of-bounds ground. |
| Q3 | 2 gate A | Which font? Must be monospace, OFL or CC0 with its licence committed beside it, capitals ≥ 6 px and digits / lowercase ≥ 5 px at native size (7.3), cover Latin Extended-A - Polish is the first translation anyone will ask for - and advance at most 6 px, which the four-panel budget of 7.2 is built on. | Built-in font until chosen. |
| Q4 | 10 | Where do a player's settings live once they are editable - a user file beside the game, or `Preferences`? Never the shipped `assets/*.properties`. **Every accessibility option waits on this.** | Placeholder page. |
| Q5 | 5 | Does anything show behind the main menu - a scenario running as a backdrop, or black? | Black. |
| Q6 | 5 | Joining by room code with only a pad: an on-screen character picker, or keyboard only? Valve's Deck review requires controller-only text entry. | **Changed:** a four-slot letter picker (up/down letter, left/right slot) - the code is short and its alphabet small. Sessions heard on the network stay one press away. |
| Q7 | 4 | Offline, does losing window focus pause? (Online it cannot stop everyone for one player's alt-tab.) | **Changed to yes, offline**: an alt-tab mid-fight otherwise kills him (pillar 2 makes that a real loss). No online. |
| Q8 | 4 | Should the paused banner online say *who* paused? Nothing publishes that today. | `PAUSED`. |
| Q9 | 3 | *Planet* - real content per scenario or level, generated from the seed, or dropped? And is it *planet*, *site* or *world* in the glossary? | `Scenario.planet()` returns `""`, line 1 shows only the mission. |
| Q10 | 3 gate B | Should the player panel name the gun he holds? | **Proposed yes** (7.2): the alpha-phase row it retires names "a real HUD" as its replacement. Confirm at gate B. |
| Q11 | 7 *tally* | What counts as **his** kill: his shots and strikes only; also grenades; also a blast he set off (barrel, charged corpse); also a turret he crews; also his squad's kills? | His shots, strikes, grenades and anything he crews; a blast credits whoever's blow killed the entity that exploded; squad kills credit nobody. |
| Q12 | 7 *tally* | What is a **mission goal** for one life - a control point taken while he stood on it, a nest destroyed, an objective met while he was alive? | Control point taken while he stood inside its radius. |
| Q13 | 7 *tally* | Is friendly fire (killing a trooper or another commander) counted, subtracted or ignored? | Ignored. |
| Q14 | 7 *tally* | Stabilising another **player's** commander - does it count as "units healed"? | Yes: a stabilise is a stabilise. |
| Q15 | 3 gate B | Remote players online: shown in this machine's bottom bar (smaller), or only local seats? | Local seats only. |
| Q16 | 8, 9 | Where does the placeholder art generator live - committed under `assets-raw/`, somewhere else, or only its output committed? | Only the output; the script kept in the session's project doc. |
| Q17 | 9 | 8 authored-and-mirrored sectors, or 16 for the torso once real art exists? | 8. |
| Q18 | 9 | Strike and throw frames need a published fact on the commander's `Appearance` (a wind-up fraction or a "struck this tick"). Worth a field now? | Not now; no frame for them. |
| Q19 | 9 | The dying animation needs the throes window published as a fraction (scaffolding row). Same stage or later? | Later. |
| Q20 | later | Game over: blocked on "On failure, do you retry the level, or is the run over?" and on the commander's respawn rule. | Modal specified, unreachable. |
| Q21 | later | Where does the action prompt go - a world-space label beside the commander (G0021), and does it share the terminal's font and skin? | Out of this plan's scope. |
| Q22 | 3 gate B | **Is every hostile blow decisive for a commander?** Today each one takes all of his 100 health, which is why the panel has no health bar. A weaker blow later - or friendly fire, if it reaches a commander (sidearm 50, shotgun 22 a pellet) - would put a number back in play that nothing shows (pillar 5). | Yes: an invariant row, checked by a `ConfigDefaultsTest` case that every hostile blow is at least `sim.commander.health`. |
| Q23 | later | A mark over each commander (`P1` tag, chevron) - the alpha-phase row "no marker over your own", made pressing by the colour check of 7.4 (players 2 and 3 are one colour to a deuteranope). | Out of this plan; the next interface task after it. |
| Q24 | 3 gate B | A reticle for pad players, at a fixed distance along the stick, or the gun alone? | The gun alone. |
| Q25 | 5 | A `CONTINUE` entry at the top of `UI:main` - the last scenario, one confirm from launch? | No; focus memory gives two confirms. Revisit with progression. |
| Q26 | 4 | A short "get ready" beat after resuming (offline only - online it would be everybody's)? | None: the world was visible under the scrim the whole time. |
| Q27 | 4 | Offline, does unplugging a seated pad mid-level pause? | Yes offline, no online. Pausing is not leaving (G0028). |
| Q28 | 9 | A hit flash on sprites (a frame of white when struck) needs a published "hurt this tick" on `Appearance`. Now or later? | Not for the commander - a hit downs or kills him, and the pose shows it. Revisit when a look that absorbs hits (a brute) gets a puppet. |
| Q29 | 4 | Is the pause split right - the pause shared, the menu each machine's own, `P` a menu-less pause? (The first draft showed every machine the modal.) | Yes (4.3). |
| Q30 | 3 gate B | Who owns the top bar's right block when the dev overlay is off or gone - a link-quality glyph online (the alpha-phase row "whatever tells a player the link is slow without reading numbers"), a wave counter, nothing? | The dev overlay in the alpha; the link glyph is the candidate. |
| Q31 | 9 | Texel density: 6 per world unit leaves a texel at 0.7 px at far zoom on 1080p (10.6). Keep 6 with the two-texel rule, go to 4, or draw the circle instead of the puppet at far zoom? | Keep 6, two-texel rule. |
| Q32 | later | Interface sounds - move, confirm, back, alert. They are half of how a menu feels and all of how an alert reaches a player who is not looking. | Out of scope with audio (section 16); `UiEffect` is where a cue id would go. |
| Q33 | 3 gate B | What does an empty seat's quarter show - nothing, or a dim `P3` and how to join (plug in a pad; G0028)? | Nothing in the alpha. |

---

## 16. Records to write and docs to touch

| Record | Trigger line | Constraint | Do not build |
|---|---|---|---|
| **ADR 0015** - The interface logic is its own module | Adding UI logic, or a class that decides what the player sees | Decisions about what is shown and how the player moves through it live in `ui`, which has no drawing, no devices and reads `sim` without writing it; `core` draws. Enforced by the module graph plus `UiArchitectureTest`. | UI decisions in `Screen`s or painters; `ui` importing `Gdx`, graphics, scene2d or `net`; widgets reading `sim` directly; an event bus in `sim` for the HUD (G0004) |
| **G0043** - The first atlas is the commander's | Adding art for any look; changing a renderer | Supersedes G0003's temporary half for looks with a complete puppet; every other look stays a shape until a record says otherwise. The permanent half of G0003 is unchanged: drawing reads `Appearance`, chooses by `Look`, and a missing puppet falls back to the shape. | sprites for other looks without their own decision; choosing a puppet by class or by component; gameplay reading anything about a sprite (hitboxes stay circles, ADR 0007); animation driven by the wall clock |
| **G0044** - A tally belongs to a life | Counting anything per player | Per-life counts live on the commander and die with him; credit is a slot carried by the source; shown, never spent. | a score or currency; a tally on the level entity; credit by `Entity`; reset code |
| (optional) **G0045** - The interface lives in the letterbox | Putting anything on screen during a level | Bars hold the HUD; the view holds the fight; only modals, the scrim and paused banner, the reticle and world-space marks draw over the view. Feedback about one player stays in his panel and on his man. | HUD over the view; a minimap (G0008); a second view; a full-screen effect for one player's state on a shared screen |
| (optional) **G0046** - The pause is everybody's; the menu is yours | Changing pause, resume, or anything shown while paused | The pause is shared session state and every machine shows it (the banner); the menu that asks for it opens only where it was asked for, at once; resuming anywhere closes every menu. | a menu opened on a machine that did not ask; a local pause flag; waiting for the round trip before showing the menu |

Docs touched across the stages: `docs/glossary.md` (T0.3, then each stage's own words),
`docs/design/README.md` input map and invariant rows (T0.4, T7.1), `docs/design/alpha-phase.md`
harness table and scaffolding rows (T0.2, T0.4, T3.4, T5.2, T6.4, T11.3), `docs/adr/README.md` and
`docs/design/decisions/README.md` tables, `docs/architecture.md` (T11.3), `docs/components.md`
regenerated for `Tally` (T7.2), and CLAUDE.md (T0.5 applied 2026-09-26; edits allowed when needed and named in the summary).

### Not in scope, with the reason

- **Audio**, interface sounds included (Q32). No audio exists; a cue with no position is G0004's named revisit case and deserves its
  own decision.
- **The action prompt and the radial order menu.** Both are interface work the alpha-phase rows are
  waiting for, but neither is in this request; the first belongs in the world beside the commander,
  not in a bar.
- **Off-screen player markers.** An open design question (`docs/design/README.md`), not a HUD detail.
- **A mark over each commander.** Q23 - the next interface task after this plan; the colour check of
  7.4 makes it more than cosmetic.
- **Animations for troopers and aliens.** The puppet is built general - by `Look`, from `Appearance` -
  so each later is art plus a `PuppetLibrary` entry, but none is asked for yet.
- **The art pipeline.** Placeholders only; the generation workflow is a later session.
- **Localisation beyond English.** The bundle is in place; translations are not.

### Scaffolding this plan introduces - looks like a decision, is not

- The **1.6-2.39** aspect range and the **360** canvas height - chosen to have numbers; Q1.
- The **neutral text colours** and the **alert red** - chosen by the policy of 7.0, not by taste.
- The **motion budgets** of 6.4 - typical numbers for snappy menus, to be felt, not defended.
- The **reticle**'s shape and size.
- The **dispatch copy** and the **order of goals** in the goal line.
- The **placeholder puppet and portrait** - silhouettes, not a style.
- The **proving ground and options pages** - empty on purpose.

---

## 17. UX checklist - answered

The questions a UI lead asks of any action game's interface, answered for this one. *(added)* marks
questions the first checklist did not have. An answer that needs Mateusz points at its Q.

### 17.1 HUD and gameplay focus

| # | Question | Answer | Where |
|---|---|---|---|
| C1 | Does the player look away from the action to check his status? Diegetic, or screen-edge? | Both, by tier. What he needs mid-fight is on his man (heat arc, casualty ring, charge - built) and repeated in his panel's status and heat; the bars hold what he needs between fights. No health bar: a commander is standing, down or dead (Q22). | 7.0, 7.2, Q22 |
| C2 | How does the UI pop in a chaotic view? Which colours are reserved for warnings? | No hue is free - the world uses them all. Separation by place (black bars, nothing of the world in them) and treatment: one alert red, inverse video, bars only, three states, ≤ 3 Hz. No UI red over the view. | 7.0 |
| C3 | The single most important thing at any millisecond? | The fight around his man. The HUD's job is to be ignorable until it matters: four tiers, tier 0 in the world. | 7.0 |
| C4 | *(added)* How far are the eyes from the HUD? | About 400 px from the view's centre to the top bar at 1080p - a saccade and refocus, a fifth of a second. Fine for tiers 2-3. On ultrawide the content stays inside the view's span, so it does not drift further. | 5.2, 7.0 |
| C5 | *(added)* Does anything in the HUD move when nothing changed? | No, by rule; the one exception is a slow, low-contrast portrait blink. | 6.4 |
| C6 | *(added)* Whose feedback is it, when four share a screen? | His: his panel and his man. Never a full-screen vignette for one player's state. | 2, 7.2 |
| C7 | *(added)* Can a player find his own man and panel without colour? | Panel: yes (`P1`-`P8`). Man: not yet - players 2 and 3 are one colour to a deuteranope. | 7.4, Q23 |
| C8 | *(added)* Where is the aim point drawn, and does it read on every ground? | Nowhere today (the OS arrow). A reticle, outlined, at screen precision. Pads: Q24. | 7.5, Q24 |
| C9 | *(added)* Does the art tell the truth about where he can be hit and where the shot starts? | Shadow at the body's radius; flash at the shot's origin; the art bends, the simulation does not. | 10.4 |

### 17.2 Input and navigation

| # | Question | Answer | Where |
|---|---|---|---|
| C10 | Primary inputs? | Keyboard and mouse for player one, pads for the rest (input map). Every page works with all three; the Steam Deck is a pad. | 6.2 |
| C11 | Grid navigation or a virtual cursor on a pad? | Grid: focus moves between entries on the d-pad and stick, with repeat. A virtual cursor is slower on a pad, and every page is a short list. The mouse hovers. | 6.2, 6.3 |
| C12 | How many inputs from the main menu into play? | Two confirms from launch: MAIN (focus on CAMPAIGN) → the list (focus on the last scenario). Restart in a level: pause → RESTART. A one-confirm CONTINUE is Q25. | T5.2, Q25 |
| C13 | Pausing mid-combat: obscure the game, or overlay it? | Overlay: a 55 % scrim, the frozen fight readable under it; the menu docked left so his man at the centre stays in sight; bars untouched. A resume beat is Q26. | 6.5, Q26 |
| C14 | *(added)* Does the press that closes a menu leak into the game? | No: `ReleaseLatch` spends every press held across a context change. Without it, clicking RESUME fires the gun. | 6.2, E32 |
| C15 | *(added)* Alt-tab or a pad unplugged mid-fight? | Pause offline (defaults of Q7, Q27); never online. | 6.5, Q7, Q27 |
| C16 | *(added)* Is pausing instant online? | The menu opens on the press; the world stops when the command lands. Everybody sees the banner. | 4.3 |
| C17 | *(added)* Can a pad-only player do everything, including typing a room code? | Only with the letter picker proposed for Q6 - Valve's Deck review requires it. | Q6 |
| C18 | *(added)* Do prompts show the glyphs of the device in use? | Yes: modality picks the hint family; pad buttons by position, labels from `PadLabels`. | 6.3 |

### 17.3 Animation and juice

| # | Question | Answer | Where |
|---|---|---|---|
| C19 | What is UI animation for? | To say something changed. Every motion in the table of 6.4 names what it says; one that says nothing is not built. | 6.4 |
| C20 | How long do transitions take? | Pages ≤ 0.12 s, scrim 0.08 s, focus instant - and input never waits: a press mid-transition finishes it. | 6.4 |
| C21 | How does the HUD react to impacts? | A hit downs or kills him - there is no chip damage to react to - so the reaction is to the transition: his panel's frame flashes and shakes one UI px, the portrait goes down, DOWN, OVERHEAT and KIA pulse in alert. No full-screen red (C6). A hit flash on his sprite needs a fact: Q28. | 6.4, 7.2, Q28 |
| C22 | *(added)* Does juice survive pause and replay? | World animation on simulation time (freezes, replays identically); UI animation on frame time; neither touches `sim`. | 2, 10.4 |
| C23 | *(added)* Does juice cost glance value? | Where it would, it goes: a high dispatch appears at once instead of typing. | 8.4 |
| C24 | *(added)* Does the log stay honest under a burst? | Coalescing, priorities, and stale low-priority news dropped before it is shown. | 8.4 |

### 17.4 Architecture and technical setup

| # | Question | Answer | Where |
|---|---|---|---|
| C25 | Resolutions, aspect ratios, anchors? | Integer-scaled UI canvas; content-sized bars; view aspect kept in a range; pillars on ultrawide; content anchored to the view's edges and span. Worked for eleven screens including the Deck. | 5.2 |
| C26 | Event-driven, or polling every frame? | Change-driven, once a frame: one copy of a few dozen fields, compared with the last; text rebuilt only on change. Cheaper than an event per fact here, and the only shape G0004 allows `sim`. | 8.1 |
| C27 | How is UI state managed; does Back always go to the right place? | `UiFlow`: a page stack, pure and tested; Back pops; per-page focus memory; Back on MAIN never quits. | 4, E-rows |
| C28 | *(added)* What does the UI cost per frame? | Budget: readout, reporters and bars under 0.5 ms of CPU with four players; no allocation; one atlas; bars in one batch; no Shape/Sprite interleave. Checked in T11.2. | 10.4, T11.2 |
| C29 | *(added)* Can UI logic be tested without a display? | Yes - everything that decides lives in `ui`, which cannot import `Gdx`. The sandbox has no display. | 3.1 |
| C30 | *(added)* Can the UI desync an online game? | No: it reads `sim` and never writes it (`UiArchitectureTest`); pause and restart travel as commands. | 3.1 |

### 17.5 Accessibility

| # | Question | Answer | Where |
|---|---|---|---|
| C31 | Legible on small screens; minimum size? | Capitals ≥ 6 UI px, digits and lowercase ≥ 5 → ≥ 12 and ≥ 10 screen px at `s = 2` (Valve: 9 floor, 12 target at 1280 x 800). The font that meets it is Q3. | 7.3, Q3 |
| C32 | Never only colour? | Labels, words and shapes for every state; the measured colour-blind collisions are listed. The world half is Q23. | 7.4, Q23 |
| C33 | UI scale, screen shake, flashing options? | Keys from the stage that adds each effect - `graphic.ui.scale`, `.motionScale`, `.flashScale`, and the existing `graphic.camera.shakeScale`; the page that edits them waits on Q4. | 7.4, stage 10, Q4 |
| C34 | *(added)* Photosensitivity? | Nothing flashes more than three times a second; validation enforces it on config. | 6.4, 7.4 |
| C35 | *(added)* Remapping; hold or toggle? | `controls.properties` today, in-game later (Q4). The view pull-back is already a toggle. | 7.4 |
| C36 | *(added)* Contrast? | ≥ 4.5:1 for all HUD text; lowest is 6.1:1. | 7.4 |
| C37 | *(added)* Will a translation fit? | Pseudo-locale tests (+30 %, diacritics) and a font with Latin Extended-A. | 7.3, Q3 |

---

## 18. Changes from the first draft (review, 2026-09-25)

| Change | Why |
|---|---|
| Player panel re-ranked: status (DOWN + bleed-out / KIA + seconds / ABOARD) and weapon with heat first, tally as dim icons last; `P1`-`P8` labels | The first draft gave the panel to the least urgent numbers, showed none of his state, and left the "gun named only in F1" row unretired. |
| Bottom bar is four fixed seat quarters, with a pixel budget; no health bar; alarm on transitions rather than hurt | Mateusz, second round: up to four players must fit, and a commander has no health worth a bar - every blow is decisive, and guns overheat rather than run out. |
| Solver: bars sized by content, view aspect a range, `uiScale`, content inside the view's span | A pinned 2.39 spent a third of the Deck's height and 44 % of a 4:3 screen on bars; the range gives them an 18 % and a 50 % taller view, and 16:9 barely moves. A 40 px portrait did not fit a 46 px bar with padding. Ultrawide text drifted to the screen's corners. |
| Portrait 40 → 32 | It must fit the smallest bottom bar: 40 UI px less 8 of padding. |
| Colour policy; `goalDone` and mission green gone | `goalDone` was exactly commander 6; greens sat beside commander 2; every hue is a world meaning. |
| Pause split: shared pause (banner), local menu, `P` menu-less, docked menu over a scrim | A friend's pause stole focus on every machine; `P` + `.` single-stepping would have stepped a covered world; online the menu waited for a round trip. |
| `ReleaseLatch` | The press that closes a menu or starts a level was reaching the first tick: clicking RESUME fired, `SOUTH` acted. |
| Motion budgets; input never waits; no idle HUD motion; high dispatches instant; stale ones dropped | The first draft had no transition timings, a cursor blinking forever in the periphery, alarms typed at reading speed and a ten-second backlog after a big fight. |
| Reticle | Mouse aim had only the OS arrow at the point the eyes spend most time on. |
| Puppet: *heroes* layer instead of y-sort among circles; shadow at the radius; flash at the shot's origin; two-texel rule | Y-sorting sprites among `ShapeRenderer` circles flushes a batch per interleave and can hide his man; a sprite must not lie about the hitbox or the muzzle; far zoom is 0.7 px per texel. |
| Accessibility built in: legibility floor, colour-blind check, option keys, flash limit, pseudo-locale | The first draft had none of these; retrofitting keys across every effect costs more than adding each with its effect. |
| Stages reordered around gates A and B; menus proved on the pause menu first; tally after the panel; options as a stage blocked on Q4; glossary words per stage; G0044 moved into the tally stage | Risk and playtest value first; the tally needs four design answers and should not hold up the panel; a dropped stage should leave no orphan word or record. |
| Q3, Q6, Q7, Q10 defaults changed; Q22-Q33 added; edge cases E32-E41 added | Each is cited where it changed. |

Stage numbers, old → new: 0 → 0 · 1 → 1 · 2 → 2 (+ greybox, gate A) · 5 → 3 (+ panel, reticle, gate B)
· 3 and 4 → 4 (flow and pause) and 5 (main menu) · 6 → 6 · 7 → 7 · 8 → 3 (panel) and 8 (portrait)
· 9 → 9 · new 10 (options) · 10 → 11.

---

## 19. Future: the roll call (not built - design open)

Added 2026-09-26 at gate B (D10). Mateusz wants to think about it more before anything is built
beyond the placeholder.

- **What it is.** The bottom bar's second view: one small row per player with minimal information -
  his colour, his status, his tally - so a game of five to eight players (online, G0038) can show
  everybody, which four player panels cannot.
- **What exists.** `UI:hud/bottom/roll-call` in the spec; `` ` `` (`controls.key.rollCall`, keyboard
  only) swaps the bar between the panels and the roll call; `graphic.hud.rollCall` opens a level on
  it; it draws `ROLL CALL - PLACEHOLDER`.
- **Open.** Row layout and how many fit (8 rows of one text line need about 100 UI px - taller than
  the bar, so probably two columns of four); whether it switches on by itself above four players;
  whether a pad can swap it; what a disconnected player's row shows; whether its words become the
  pause menu's tally lines.
- **Depends on.** The tally (stage 7) for its third column, and a published per-slot connection state
  from `net` for remote players (D9).

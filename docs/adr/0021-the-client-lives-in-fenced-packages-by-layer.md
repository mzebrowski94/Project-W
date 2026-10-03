# 0021: The client lives in fenced packages by layer

**Status.** accepted

**Trigger.** Adding a class to `core`; one client package using another's class; adding a client package
or a port; a client class reaching for `Gdx`, a device or GL; proposing a package per screen.

**Enforced by.** Every class under `client` is in a declared layer or named as the root's
(`ClientArchitectureTest.everyClassLivesInALayerOrIsTheRoots`); a layer uses another layer or the root
only through the declared ports (`.aLayerUsesOnlyThePortsOfAnotherAndOfTheRoot`); `play` and `config`
touch no libGDX (`.playAndConfigTouchNoLibGdx`); `draw` and `hud` read no device (`.whatDrawsReadsNoDevice`);
`input` draws nothing (`.inputDrawsNothing`); a `*Controls`, `*Selection` or `*Director` touches no `Gdx`
(`.decisionLogicDoesNotTouchTheRunningApplication`). Each fails on zero classes rather than passing.

**Amends ADR 0020.** 0020's *Scope* said "Not `core`"; its *Revisit when* named `core/client` taking the
same shape. Here it does, by layer rather than by domain. 0020's status becomes `amended by ADR 0021: core's
client takes the same shape, by layer`. Its constraint for `sim.system` is untouched.

**Constraint.**

- **Every client class lives in a layer** - `client.input`, `client.draw`, `client.hud`, `client.play`,
  `client.config` - **or in the root**, which holds the application (`PwtGame`, `GameContext`), the screens
  and what only they use. Each layer has a `package-info.java` saying what it is, its entries and ports,
  what does not belong in it, and the records it answers to.
- **Package-private by default.** Public are a layer's **entries** - what the root builds and drives, as
  `Simulation` builds systems - and its **ports**, which another layer may use, each named in
  `package-info.java` and in `ClientArchitectureTest.PORTS`. The root's ports are `Log`, `Assets` and
  `LaunchOptions`; nothing else in the root is used from a layer.
- **A config record lives with its layer** (`GraphicConfig` in `draw`, `ControlsConfig` in `input`), with
  its generated shipped text beside it. `config` is the front door (`ConfigLoader`, `GameProperties`) and
  the loaders of configs the client does not own.
- **libGDX by package**: `play` and `config` touch none of it; `draw` and `hud` read no device; `input`
  draws nothing; the root may use anything. In the packages that may touch `Gdx`, the deciding classes are
  fenced by name: no `*Controls`, `*Selection` or `*Director` touches `Gdx`.
- **Placement.** A class goes with the layer whose job its name says; failing that, with its users if they
  are all in one layer; a class only the screens use goes to the root. Measure a move with
  `.claude/scripts/system-graph.py --root core/src/main/java/pl/mzebrows/pwt/client --split FILE` before
  making it: ports should not grow, and layers should not lean on each other both ways.
- Every fence says `.because(...)` naming this record, and fails rather than passes on zero classes.

**Scope.** `core/src/main/java/pl/mzebrows/pwt/client` and its tests, which sit in their subject's package.
Not `lwjgl3`, the launcher, and not `ui` or `net`, which are modules with their own fences.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A package per screen.* `ScenarioScreen` uses thirty classes across every layer; a screen package
would hold everything, and the menus and the level share the painters, the fonts and the seats.

**No:** *Packages by what decides versus what draws.* `core` is adapters, and its adapters are its layers;
the decide/draw line runs inside `input` and `draw`, and the name rule keeps it there.

**No:** *All config records in `config`.* `GraphicConfig` builds every mark: in `config` fourteen `draw`
classes become ports, and `config` depends on `draw`.

**No:** *The suffix rule alone, or packages alone.* Suffixes fence nothing but four kinds of name; packages
alone leave the decision classes in `input`, `draw` and the root unguarded, since those packages read devices
and set GL state.

**No:** *A screen package apart from the root.* `PlayMenus` and `HarnessControls` would become ports for it
alone, and the root would be the application and two ports.

**No:** *`@Internal`, `.internal` subpackages, a facade per package, a Gradle module per layer.* ADR 0020's
reasons, unchanged.

**Revisit when** a layer's ports outnumber its entries, or a layer has to read another both ways.

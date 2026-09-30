# 0015: The interface logic is its own module

**Status.** amended by ADR 0017: a subsystem with nothing to wall off from rendering may be a fenced package instead of a module

**Trigger.** Adding UI logic - a layout rule, a page, navigation, a HUD readout, a pose - or any class
that decides what the player is shown; proposing to fold `ui` back into `core`.

**Enforced by.** The module graph (`ui` has no libGDX backend, no `core` and no `net` on its classpath),
plus `UiArchitectureTest` for what the classpath still offers: `ui` touching `Gdx`,
`com.badlogic.gdx.graphics..`, `..scenes..`, `com.badlogic.gdx.Input`, a backend, `pl.mzebrows.pwt.net..`
or `pl.mzebrows.pwt.client..`; writing a field of a `sim` component; calling `Simulation.step`.

**Constraint.** What the player is shown and how he moves through it is decided in the Gradle module
`ui`, as data - screen layout, pages and their transitions, navigation and modality, the HUD's readout
and what it reports, poses for anything animated. `ui` reads `sim` through families and never writes
it. `core` draws what `ui` decides and owns the devices: viewports, batches, fonts, skins, shaders,
`Screen`s, input adapters. A decision found in a drawing class moves to `ui` and is tested there.

`lwjgl3 -> core -> {ui, net} -> sim -> map`, one way. `ui` and `net` never see each other.

**Scope.** Everything that decides. Not drawing, not devices, not assets, not config loading - those
stay in `core`, which is thin by design. Not the simulation: the HUD observes published state from
outside (G0004), and nothing is added to `sim` to notify it. Existing `core` classes are not moved
wholesale; a class moves only when a `ui` class needs it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *UI decisions in a `Screen` or a painter.* They need a GL context to run, so the sandbox cannot
test them, and they are exactly the code that grows.

**No:** *Packages in `core` plus an ArchUnit rule instead of a module.* ADR 0003's reasoning: the
classpath would still offer `Gdx` to logic, one import away.

**No:** *`ui` importing `Gdx`, graphics, scene2d, an input device or `net`.* Math and collections from
libGDX are allowed, as in `sim`.

**No:** *Widgets or painters reading `sim` directly.* One reader (the readout) keeps the set of facts
the HUD depends on in one place.

**No:** *An event bus in `sim` for the HUD.* G0004. The client compares published state frame to frame.

**Revisit when** a UI decision genuinely needs a GL object to be made - measuring a proportional font
is the likely one, and the answer is a metrics value handed in, not a dependency.

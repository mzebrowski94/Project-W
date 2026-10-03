# 09 - Dev overlay reads the readout

Status: done

Strength: Worth exploring. Dependency: in-process. Report candidate 10.

## Problem

The glossary says the **readout** is copied out of the simulation "by the one class that reads sim for
it" (`ui/.../hud/ReadoutWriter`). `core/.../client/DebugOverlay:75-95` derives wounds, down, weapon and
invulnerability itself, with different rules:

- down from `vitality.begun`, where `ReadoutWriter:195-211` takes posture from `Appearance.state`;
- a missing weapon shown as `""`, where `ReadoutWriter:181` defaults to `SIDEARM`;
- no turret weapon when aboard, where `ReadoutWriter:257` has one.

The `Readout` is already filled at `ScenarioScreen:304`, before `overlay.lines` at `:323`. `count()` builds
`Family` queries every frame, and `Gdx.graphics` is called inline, so the overlay cannot be tested.

Also: the interpolation `previous + (position - previous) * alpha` is written four times
(`WorldRenderer:145, 231`, `ScenarioScreen:502, 628`), and the reticles read `Commander`, `Facing` and
`Position` directly (`ScenarioScreen:490-506`) although the readout holds posture.

## Direction

The overlay and the reticles read the readout filled earlier in the same frame. A test beside
`ReadoutWriterTest`.

## Comments
- 2026-10-03: Taken (system-modules 06). Gates `.scratch/client-packages/`, which splits `core/client` by layer
  once these are in; order 4th.
- 2026-10-03: Done, with three calls taken first. The overlay describes the commander from the readout
  filled earlier in the frame (`DebugOverlay.commanderState(PlayerReadout, godMode)`): down from posture, the
  gun his panel shows - a sidearm when he has none, the turret's gun aboard - and `PlayerReadout.invulnerable`,
  new, which `ReadoutWriter` fills beside `shielded`. God mode stays a session flag read from the world, as do
  the entity counts, which are the world's diagnostics rather than a player's state. Frames a second come in as
  a value, so the overlay is tested without `Gdx` (`DebugOverlayTest`). The reticles stay with drawing - they
  need the two positions and the frame's alpha, the renderer's inputs (G0003) - and the four interpolations are
  one helper, `Drawn` (client).

# 09 - Dev overlay reads the readout

Status: ready-for-agent

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

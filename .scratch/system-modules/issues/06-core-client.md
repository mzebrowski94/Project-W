# 06 - Shape of core/client

Type: grilling

## Question

`core/src/main/java/pl/mzebrows/pwt/client` is one flat package of 91 classes: rendering (`WorldRenderer`,
painters, the `*Mark` classes), input (`Gamepad*`, `Key*`, `Pad*`), config loading, the online session,
screens. Does it want the same treatment as `sim.system`, and on what axis?

## Read first

`docs/architecture.md` (*core (client)*), ADR 0011 and 0015, `ClientArchitectureTest`, and what tickets
01-05 settled in `.scratch/system-modules/map.md`. Skills: `grilling`, `codebase-design`.

## Settled

Its own effort, in a fresh session, after 05. `core` and `sim` are already cleanly apart: `core` never
imports `sim.system`, and the renderer reads only `Appearance`, `Position`, `Facing`, `Body`, `Commander`
(the rest is `DebugOverlay`).

## Open

The axis (rendering, input, session, screens?), whether package-private holds there as it does in `sim`,
and the open `architecture-review` tickets in `core`: 03, 04, 05, 06, 09.

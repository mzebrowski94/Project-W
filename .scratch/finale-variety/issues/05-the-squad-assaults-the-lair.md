# The squad assaults the lair

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: 01 (a green `main`)
Spec: [spec.md](../spec.md) answer 9
G-record: **G0122** *The squad assaults the lair, its nests first and then the boss* (exists; **amends G0061**)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system`

## What

In the keep, once its lair has risen, troopers stop standing round the post and go after the lair: its nests first, then
the boss.

## Settled (2026-11-24, not provisional)

- **When:** from the tick the lair rises (G0048) until nothing it raised is left alive.
- **Who:** a trooper with no order (G0017) and nothing inside his all-round range, and no nest he keeps (G0085) to shoot.
- **Where:** to **his gun's reach** of the nearest living **nest the lair raised**, measured from him; with none left, of
  the **boss**. Not closer: at reach he fights as he does today (`TrooperCombatSystem`, `TrooperFight`).
- **What outranks it:** an order, self-defence inside his all-round range, a kept nest, aid, ice in his way (G0117) - all
  as they outrank the post's ring today. A nest is never always first (G0085's rejection stands).
- **The redoubt is unchanged**: its men hold the post's ring (G0061). Before the lair rises the squad patrols the way in
  (G0094).
- A target is named by its `Handle` and found with `Handles.resolve` (ADR 0016); a nest raised by the lair is known as
  one without a branch on what it is (G0008, G0080) - a field the lair sets, or the lair's own list.
- Fill in G0122's *Enforced by*; add or amend the invariant row in `squad-and-objectives.md` beside G0061's.

## Open for the implementer

- Which system owns the goal: `TrooperObjective` (where the ring and the way in already live) is the obvious home;
  say why if not.
- "Nearest" by route or crow-flies: use what `TrooperObjective` already has (`FlowField`).
- Reach for a gun with no reach (G0032: reach zero): a tunable stand-off in `assets/sim/squad.properties`.

## Read first

- `CLAUDE.md`; `docs/simulation-order.md`, `docs/performance.md` (per-entity loop).
- `docs/design/invariants/squad-and-objectives.md`, `aliens.md` (lair, nests).
- G0017, G0048, G0061, G0080, G0085, G0094, G0117, G0122.
- `TrooperObjective`, `TrooperMindSystem`, `TrooperCombatSystem`, `Lair`, `TrooperAdvanceTest`, `TrooperCombatTest`,
  `LairTest`.

## Done when

- `TrooperAdvanceTest` (or a test beside it) holds: no assault before the lair rises; the nearest nest first, from reach;
  the boss once the nests are dead; an order and a grunt in his all-round range still outrank it; the redoubt unchanged.
  `verify.sh` green.
- `## Answer` gives a keep mission code, or a hidden scenario that starts at the keep with a squad.

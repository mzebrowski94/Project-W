# The boss faces away from the players

Type: task
Status: needs-triage

## What

Reported by Mateusz, 2026-10-04: the boss grunt in Adventure 2's keep stands with his back to the players. He
should face west, the way they come in, and turn to whatever he is shooting at.

## Likely cause (read, not run)

- `Spawner` puts every unit down with `Facing.face(1f, 0f)`, east.
- `FacingSystem` turns a creature by its `Intent` aim, an aiming `Ranged`, or its velocity while it steers. A
  rooted boss (speed zero, G0048) never moves, and `BarrageSystem` aims each salvo from `Perception` without
  publishing an aim anywhere `FacingSystem` reads. So nothing ever turns him from east.

## Fix, roughly

Tests first, smallest reproduction: a boss risen from a lair with a player west of him faces west.

- He faces what his barrage shoots at: the target, or the place he was provoked from. That is the same
  `Perception` read `BarrageSystem` makes, published where `FacingSystem` reads it, never a branch on "boss".
- Before he has a target he keeps the facing he rose with, and a lair arrival should rise facing the way the
  players come in. Missions ticket 04 places the keep's arrivals as data with a north-south flip, and west stays
  west under that flip.

`docs/design/invariants/aliens.md` (G0048 row) and `AdventureTwoScenarioTest` are the nearest tests.

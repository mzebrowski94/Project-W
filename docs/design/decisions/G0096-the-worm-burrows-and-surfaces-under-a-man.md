# G0096: The worm burrows, and surfaces under a man

**Status.** accepted

**Trigger.** A creature that cannot be hurt for part of its life; one that moves under the ground or beneath
scenery; an attack shown as a ring closing on a place; a strain hunting across its whole biome; touching what a worm
does while burrowed, surfaced or resting.

**Enforced by.** `WormTest` (the rows in `invariants/aliens.md`); `MissionSweepTest` (the desert's
roster fits its room on every tier); `AlienTargetingTest` (the switch margin, kept to a biome).

**Asked for** by Mateusz, 2026-11-16, grilled the same day: the desert's second strain after the snake (G0095). Not
yet played.

**Amends G0097** - its scope: the worm comes for its man through `Drivers.nearestLivingTarget` and the switch margin,
kept to its own biome by a parameter of that one place.

**Constraint.**

- **One strain, as data** (G0008): a `sim.unit.worm.*` block and a burrow definition on every strain, off at zero.
  Body radius 3.5, health 900, `loose worm 1 2` in the desert alone.
- **It comes for the nearest man on his feet in its own biome**, from the tick one enters it - always aggressive, no
  detection range - and keeps him by the switch margin (G0097). **Its biome is its range**, so a scenario that puts every
  strain down unaware - a mission, an Adventure - still sends it after a man who enters (found in play, 2026-11-16). It
  goes after him **only once its rest is out**: with nobody in its biome, or while it rests, it roams - bends about
  its home as a snake does, on the snake's bend and roam numbers and its own `RngStream`, at `idleSpeed` 7, turning
  only at its biome's edge (asked for 2026-11-16).
- **Burrowed it is always on the move** and slowed by nothing (`unslowed`, as G0095): at 14 - faster than the
  commander's 13, so nobody walks away from it; the maw is the dodge - turning at `huntTurnDegrees` 240 a second, so
  it swings round onto a man rather than flicking back and forth through his feet. It passes beneath
  bodies, scenery, rock and shut doors, never round them. Nothing lands on it, shock included, and no trooper fires at
  it. Its **shadow**, a see-through black circle its size, is always drawn (pillar 4), fainter while it rests
  (pillar 5: whether it can come up is legible).
- **It surfaces** once within `surfaceReach` 2 of its man, off its rest, with open room for its body - centred where
  he stood that tick, never steered after. No room - a stone, a doorway's edge, the biome's border - and it stays
  under him. The **maw** opens at three times its body and shrinks onto it over a **tell** of 0.9 s: a commander
  walking (11.7) or a trooper walking (9) steps clear of the 3.5 body; one standing or shooting does not.
- **Hittable from the tell's first tick, solid only from the snap.** The **snap** wounds every man on his feet within
  its body and his, once, through `Damage.land` with the harm `wound`; the swarm is spared. Then it stands 1.5 s in its
  hole, immovable as a rooted strain, the solver putting whoever was in the hole out of it - **looking at its man**,
  turning onto him at its hunting turn, or about it with nobody, so it is never frozen (asked for 2026-11-16); it
  looks the same way while its maw closes. Then it dives and rests,
  drawn 15-20 s from the dive, before it may surface again. Its first surfacing after it takes a man has no wait.
- **Only a declared stun holds it** (`Stunned.immune`, as the snake): no stagger, kick or clinch. A declared stun
  landed in the tell breaks the snap off and holds it up, hittable and not solid, for the stun; then it dives and
  rests as after a snap (decided in the build, for review).
- **Its maw has 12 teeth**, its health shared out between them rounded up - the snake's rule - so the last tooth goes
  with the last point. Teeth show only while it is up; the shadow says nothing of how hurt it is.

**Why always moving.** A shadow that stops is a thing you can wait out or forget; one always coming is the desert's
pressure, and the snake's slither already reads that way.

**Why a long rest.** Up 2.4 s in every 17-22: the worm is a recurring moment in the crossing, not the whole of it.
About three surfacings of a squad's fire kill it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A worm that routes round rock or scenery.* It goes beneath them; a route is the swarm's.

**No:** *A worm hurt, shocked or fired at while burrowed.* The only time it can be hurt is the point of it.

**No:** *A worm solid during its tell.* The solver would push its man out of the maw before the snap could land.

**No:** *A worm that surfaces where there is no room, or out of its biome.*

**No:** *A worm slower than the commander.* He could outwalk it forever, and it would only ever take troopers.

**No:** *Its own nearest-man scan.* One place decides who an alien comes for (G0026, G0097).

**Revisit when** playing shows the rest too long to feel or the 0.9 s tell too tight for a commander mid-fight, or a
worm roaming through its rest reads as one that has lost him.

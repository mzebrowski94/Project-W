# G0051: A trooper throws only where the blast is the enemy's

**Status.** accepted

**Trigger.** Letting a trooper throw a grenade; changing when one does; anything that would put a
blast of the squad's making near the player's side.

**Enforced by.** `TrooperGrenadeTest` - with the chance at zero no trooper ever throws and nothing
is dealt; a trooper throws only with a cluster at the landing point; no throw when the commander or a
casualty stands within the blast plus the margin; no throw with scenery across the path; at most one
throw per squad gap; a trooper spawned after Explosives throws like one already on the field. Rock
across the path is the same check routing uses (`TileMap.isClearPath`).

**Constraint.** Every trooper carries the throw from spawn, off at zero: the chance
(`AiChance.THROW_A_GRENADE`) has a base of 0 and only an upgrade turns it up. The throw is the
commander's throw (G0019) - along a bearing, a fixed range, passing through the living - with its own
*how often* in `sim.trooper.grenade.*`. A throw is an **opportunity**, and only then is the chance
spent (G0039), while all five hold, checked cheapest first:

1. he is fighting something, nothing holds him, and his hands are free;
2. the landing point, along his bearing to the target at the throw's range, has at least
   `minCluster` aliens standing within the blast;
3. **nobody on the player's side** - commander, trooper, casualty or crew - stands within the blast
   radius plus `safetyMargin` of that point;
4. the path is clear of rock and of scenery that never moves, and stays inside the arena;
5. his own throw is ready, and the squad's gap has run out: at most one trooper grenade per
   `squadGapSeconds`, held as level state.

The throw's range equals a trooper's sight, so troopers grenade packs as they come into view.

**Scope.** Troopers. The commander throws where he aims (G0019), and nothing here narrows that. The
blast itself is unchanged - one circle, on nobody's side (G0018): these rules make friendly fire
impossible at the moment of the throw, and `safetyMargin` covers the second the grenade flies.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Trooper grenades on a timer.* A throw that ignores what is in front of him is a blast
waiting to land on his own men.

**No:** *Thrown at the aim point.* G0019: a grenade is a fixed throw, for everybody.

**No:** *An ammunition economy.* A count the player tracks and the game must show (pillar 5), for a
rate the cooldown and the squad gap already limit.

**Revisit when** a playtest finds trooper grenades landing near the squad despite the margin, or an
alien that shelters behind the player's men.

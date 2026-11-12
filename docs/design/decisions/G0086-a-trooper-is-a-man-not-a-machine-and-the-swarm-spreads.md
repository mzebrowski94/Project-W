# G0086: A trooper is a man, not a machine, and the swarm spreads through its biome

**Status.** accepted

**Trigger.** How fast a trooper turns or how true he shoots; a trooper noticing something behind him past his
all-round range; a man answering a leap with a strike; a turret as a target; how far an idle creature strays from
where it was put; what a taken point lays besides grenade caches; how many men stand on one point being taken; a
leech going off by itself.

**Enforced by.** Named in the invariant rows that cite G0086 (`squad-and-objectives.md`, `aliens.md`,
`pickups.md`).

**Settled with Mateusz, 2026-11-12**, from the playtest of 0a6a390.

**Amends G0040** (the capture crowd). **Amends G0078** (a taken point may lay a medkit). **Amends G0080** (noticed from
behind on a chance). **Amends G0083** (the leech ships with no fuse). And the `aliens.md` row that an alien comes for a
turret as readily as for a man (manned once).

**Constraint.**

- **A trooper turns at a speed** (`sim.trooper.turnDegreesPerSecond`), every turn and not only from behind, and
  fires only once his facing is within `sim.trooper.fireArcDegrees` of where he aims. A snap reads as a machine.
- **A trooper's aim errs** by up to `sim.trooper.aimErrorDegrees` either side, drawn per shot from the squad's
  stream. On the man, not the gun: the commander's aim is his own to get wrong, and a turret has its own.
- **Noticed from behind, on a chance keyed per pair** (G0076's shape): each trooper and alien is a fixed yes or no
  at `sim.trooper.noticeBehindPercent`; a yes sees that alien behind him from his full detection range, a no only
  inside his all-round range. Still never through rock.
- **A deflect** is a trooper meeting a leap that comes at him inside his arc with his hand strike as it lands, so it
  never takes hold of him. A chance the strain's leap declares (`leap.deflectChance`, zero means off - only the ant's
  is above it), keyed per leap and man. Only a leap whose flight meets his body, and only with his strike in hand -
  ready, or already swung at that leap: no strike, no deflect. Not a dodge (G0012 is a creature stepping off a shot's line), and not the
  commander's parry.
- **A turret is a target once it has been manned**: until somebody has crewed it, the aliens pay it no heed; from
  then on, for the rest of the level, whether crewed or not. A blast still hurts it either way.
- **A patch drifts**: each time an idle unit reaches the spot it walked to, its home moves `wander.driftPerStop`
  towards it, never out of its own biome. Over minutes a biome's creatures fill it. Zero for what is rooted.
- **A taken point may lay a medkit**: one, at `sim.controlPoint.medkitChance` per cent, keyed on the point, in the
  ring its grenade caches lie in. The existing medkit kind; one per point, not per slot, because a trooper takes it
  too.
- **The capture crowd**: once `sim.trooper.captureCrowd` men are settled on a point being taken (securing it, or
  walking onto it), a trooper who arrives goes on to the next point the squad would take, and holds there - never past
  it, keeping to that point though another comes nearer on the way. Men who settle together past a full crowd are its
  spares and go on the same way, so a clump never empties a point. With none left, he wanders round this
  one. What G0040 refused, the squad running the level on its own, is still refused: one point ahead, no further.
- **The leech ships with no fuse** (`fuse.seconds` 0): one on nobody waits, whole, for a man to pass. The fuse stays a
  definition every strain carries.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Spread on the sidearm.* The commander carries it too; a trooper's error is the trooper's.

**No:** *Noticing from behind as a chance per second.* "One in four" would become "on average after four seconds",
which every alien behind him eventually is.

**No:** *A deflect from behind.* He has to be facing it; that is what his turn speed is for.

**No:** *Sending the crowd's spare men further than the next point.* G0040's line.

**Revisit when** a turret nobody has touched turns out to be a safe place to stand (the man by it is still a man), or
the drift empties the places a level wants defended.

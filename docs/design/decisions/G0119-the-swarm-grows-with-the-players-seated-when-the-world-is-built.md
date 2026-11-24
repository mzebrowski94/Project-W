# G0119: The swarm grows with the players seated when the world is built

**Status.** accepted

**Trigger.** Putting a player count on a number of the swarm; changing when the players are counted; what a daily records
of a game played in company.

**Enforced by.** `MissionPlayersTest`, written with the code.

**Asked for** by Mateusz, 2026-11-24: more players make an easier playthrough. Four commanders met the swarm one meets,
with one shared squad between them; G0118's adaptations grow with the players, but a pool of eighteen ranks cannot carry
the difference alone.

**Amends G0063 and G0106.** G0063's tier and G0106's strength are what move the swarm's numbers; the players seated are a
third, folded with them.

**Constraint.**

- **Two whole percentages per player past the first**: `sim.mission.players.healthPercent` (10) on every strain's and
  nest's health, the lair's boss included, and `sim.mission.players.countPercent` (10) on every count G0106's strength
  reaches - a loose line, a nest's line, a wave's mouth. The lair is placed, not counted, and keeps its count. Four players
  meet 130 % and 130 %, eight 170 % and 170 %.
- **One fold, one rounding.** They meet the tier's, the ramp's and the strength's where those meet (`MissionConfig.apply`,
  `Pressure`), rounded once in `Whole` as those are.
- **Counted when the world is built**: the slots seated on its first tick - online, those the host's START seats;
  offline, the pads plugged in when the mission starts. A slot joining, leaving or away afterwards changes nothing.
- **Shown in words** beside the strength, on the pause page and the top bar, when it is not one player (pillar 5).
- **A daily keeps its record whatever the count.** A best time is a best time (Mateusz, 2026-11-24).
- **No relational guarantee is held past one player**, as none is past a hundred (G0106): a hard grunt in a pair has
  316 against a fist of 300.

**Scope.** Missions. The proving ground and the alpha scenarios are not scaled. The player's side is the same at every
count (G0063).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Counting whoever is seated now.* Every creature is on the field from the first tick, so a join could reach only
what comes later, and a leaver would ease a fight off under the men still in it.

**No:** *A daily's record by player count.* Kept simple (Mateusz, 2026-11-24).

**No:** *Count alone, to keep every breakpoint.* Chosen against (Mateusz, 2026-11-24): the swarm grows tougher as well as
more numerous.

**Revisit when** a game of five or more has been played, or a group that fills in after START plays a mission much
easier than one that sat down for it.

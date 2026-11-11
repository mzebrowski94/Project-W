# G0076: A man heeds ground on a chance the ground declares

**Status.** accepted

**Trigger.** Making a unit go round ruined ground less than always, or giving anything a chance taken once per pair
of things rather than per second; giving a creature something it leaves where it dies; proposing that a man choose
per circle, or per tick, whether to go round.

**Enforced by.** `ToxicBloodTest` - `.leavesASpatterOfVenomOnItsOwnSideWhereAnAlienFell`,
`.leavesNothingWhereAnAlienFellUntilTheSwarmHasToxicBlood`, `.leavesABiggerSpatterAtTheSecondRank`,
`.leavesAFrogsSpatterAsItWasWhetherOrNotTheSwarmHasToxicBlood`, `.isWalkedStraightThroughByAManWhoDoesNotHeedIt`,
`.isGoneRoundWholeByAManWhoHeedsIt`, `.isGoneRoundByAboutAsManyMenAsItsChanceAndTheSameOnesOnAReplay`.

**Settled with Mateusz, 2026-11-11**, from the playtest list (ticket 03): *enemies leave venom when they die - a small
puddle; troopers should roll whether to avoid it, high chance.*

**Amends G0046.** Ground is still given room by whoever it can catch - but a piece of ground may declare that a man
gives it room only on a **chance**. Frog venom, glue and fire declare one, always, so nothing that was gone round
before is walked into now.

**Amends G0039**, whose scope was the per-second chances alone: a chance taken once per pair of things is keyed, not
dealt (*Why keyed, not dealt*, below).

**Constraint.**

- **Toxic blood** is an adaptation: every alien leaves a small spatter of its own venom where it dies, however it
  dies (G0018's one death reaction). It is on the alien side, so aliens wade through it.
- **How many circles is the dier's**, read as it dies: its strain's own `deathSlicks` (zero for all of them) plus what
  the swarm's `death_slicks` adds, through `Modifiers`. An alien on the field before the swarm adapted leaves it as one
  born after does. The shape is one shared block, `sim.blood`, as fire's is.
- **The chance is on the ground**, as `heeded` on the slick's definition and the collider, and avoidance reads it.
  It never asks what the ground is (G0046's Do-not-build holds).
- **A man heeds a whole spatter or none of it**: one draw per man per spatter, keyed from the world seed, the spatter
  and the man, so it is the same answer for every circle, on every tick and on every machine (pillars 6, 7). Every
  steered man rolls; the commander is not steered, and the aliens wade their own side's ground anyway.
- A man who does not heed a spatter walks his own line into it, and it catches him as any venom does.

**Why keyed, not dealt.** G0039 deals a chance per second from a due so luck cannot run in streaks. This chance is
taken once for a pair and never again, so there is no streak for one man to have - and a draw that is a function of
the pair needs no state to carry and nothing a snapshot must hold. Whether a chance taken again and again per attempt
(a hit, a drop) wants an even deal is still open.

**Why on the ground and not on the man.** "This spatter is litter, that venom is the weapon" is a fact about the
ground; a careless man would be a trait nobody can see (pillar 5) and would wade the frog's spatter too.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A draw per circle, or per tick.* A man going round half a spatter, or flickering between round and through,
reads as broken steering rather than as a man who did not notice.

**No:** *Careless men - a trait on the trooper.* Invisible, and it would make a man walk into everything.

**No:** *Deciding per spatter for everyone, at the spawn.* It would be one roll for the whole squad: every man through
it, or none.

**No:** *A per-strain spatter shape.* One block until a strain needs its own; the leech that reuses this (ticket 12)
declares circles, not a shape.

**Revisit when** a chance is taken per attempt again and again (a hit, a drop), or a strain needs a spatter of its own
shape.

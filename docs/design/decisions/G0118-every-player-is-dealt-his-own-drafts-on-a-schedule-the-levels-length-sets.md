# G0118: Every player is dealt his own drafts, on a schedule the level's length sets, and the swarm adapts on one too

**Status.** accepted

**Trigger.** Anything that decides what earns a draft or an adaptation, how many a level deals, who a draft goes to, or
when a tier of cards opens; a draft rotation; an adaptation on a timer or a count of cards.

**Enforced by.** `DraftScheduleTest`, `CrossingTest` and `AdaptationScheduleTest`, written with the code in its stages.

**Asked for** by Mateusz, 2026-11-24, after a census of 60 seeds a length: a solo 6-biome mission dealt about sixteen
drafts and sixteen crates in its ten to twelve minutes - a choice in the top bar about every twenty seconds - while four
players sharing the rotation would wait to about the thirteenth of sixteen sites for the fourth one's first card. Most
of the sixteen were never meant: `BarrageSystem` counted a boss down for every strain with a barrage, the sludgers,
jaguars and giants among them, against the glossary's boss. And the swarm, on a timer and on the cards taken, held the
whole pool - seven adaptations, eighteen ranks, one possible end state - by the finale of every long run.

**Amends G0049, G0052 and G0063.** G0049's draft went round the present slots and was earned by a point, a hold wave or
a boss: here every slot is dealt his own, by sites and crossings, at a pace the level's length sets. G0052's away slot
passed its drafts on: here it keeps them. G0063's adaptation pace was two numbers a tier on time and cards: here it is
a count and a share of the pool, on the same schedule as the drafts.

**Constraint.**

- **Every slot is dealt his own drafts, the same way solo and in company.** No rotation: nobody's card waits on
  anybody else's. A draft is still one offer of `sim.upgrade.draft.options` cards to one chooser, held until he takes
  one; a solo game still pauses while it is open (G0049).
- **Two sources, and nothing else earns one.** A **site taken** - a control point, a worked site, a relic, the
  finale's post - counts for every seated slot. A **crossing** counts for one slot: his own commander entering the
  pass out of a biome, the ridge before the finale included, so a mission has as many as it has biomes. A crossing
  counts once a biome a slot and catches up by position - a commander who comes on east of a ridge has crossed every
  ridge west of him - and never waits on the biome's sites. Hold waves and bosses earn nothing.
- **The schedule is fixed when the level is built.** Its sources are weighed - `siteWeight` a site, `crossingWeight` a
  crossing - and a slot is planned `drafts x (sources / sourcesRef) ^ (curvePercent / 100)` drafts, the power taken once
  with `StrictMath`. The first source earns at once (`firstAtOnce`); the rest are spread evenly over what remains, with
  the remainder carried in whole numbers. Every slot has the same thresholds, and his own count of what he has passed:
  the sites the side has taken and his own crossings.
- **Nothing is passed on.** An away slot keeps earning and holds his drafts until he is back; a slot seated mid-level
  is owed every threshold his count passes, the sites already taken included.
- **A tier opens by share of the run.** `tierFrom` is a percentage of a slot's planned drafts, counted per slot.
- **A shared card adds a full rank whoever takes it**, and its ranks cap it: a big group reaches the shared ceiling
  sooner, never past it.
- **The swarm adapts on the same schedule.** A level is planned `adaptations x (sources / sourcesRef) ^
  (adaptation curvePercent / 100)` adaptations, moved by up to `spread` either way from the level's own stream, and
  never more than `mostPercent` of the pool's ranks. Its count of what it has passed is the side's: sites taken and the
  furthest crossing any commander has made. Each source is worth `perPlayerPercent` more per seated slot past the first
  when it is passed, and the share rises by the same factor, but never past `ceilingPercent`, so no run ends on the
  whole pool. Nobody chooses; `answerWeight` still leans the deal towards what the players hold.

**Scope.** Every level that earns drafts, a mission or not, by the one rule over its sources - a level without biomes
has sites alone. The proving ground earns nothing (G0060). Crates are pickups, and how often one is left is G0110's.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A draft that goes round the players.* The last in line waits for most of a mission, and four players share what
one is dealt.

**No:** *A draft for a hold wave cleared or a boss killed.* A boss's arrives as the mission ends, and a redoubt's twelve
waves would make one finale richer than the other.

**No:** *A crossing that waits on the biome's sites.* It is the floor a run always reaches; gated, it is a site by
another name.

**No:** *A count of drafts that grows with the length one for one.* A long run takes most of the catalogue, every run's
kit converges, and it grows easier against a ramp spread over the march (G0102).

**No:** *An adaptation every so many seconds or every so many cards.* Time empties the pool on a long hard run, and cards
multiplied by players empty it in company; a swarm holding the whole pool meets every finale the same.

**Revisit when** a playtest has judged the pace - `drafts`, `curvePercent` and the swarm's `adaptations` are the dials -
or the crates still crowd the top bar once the drafts thin out, or the pool of adaptations grows enough for the ceiling
to move.

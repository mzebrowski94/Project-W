# G0102: A mission's length is chosen and held in its code, and the ramp spans the march

**Status.** accepted

**Trigger.** Changing how many biomes a mission marches through, how they are dealt or how the ramp rises over them;
what a mission code holds.

**Enforced by.** Nothing yet - not built; the tests are named here when it is.

**Asked for** by Mateusz, 2026-11-19, with the briefing (G0101): the number of biomes a mission has, chosen before it
starts, from three to fifteen, a biome coming again when the pool runs short.

**Amends G0063 and G0065.** G0065's six characters of mission seed become the mission's length and five of seed;
G0063's ramp rises over the whole march, whatever its length, rather than by a fixed step a position.

**Constraint.**

- **A mission's length is how many biomes it marches through before its finale**: from `sim.mission.biomesMin` (3)
  to `sim.mission.biomesMax` (15). `sim.mission.biomes` (6) is the default length - a daily's, and the row's first.
- **It is chosen where the mission is**: a choice row on DAILY MISSIONS, offline and on the briefing alike (G0101),
  `< 6 BIOMES >`, writing into the code line. RANDOM draws at the length the row shows. A daily is always the default
  length: choosing one puts the row there, and changing the length with a daily in the line makes it a custom
  mission.
- **The code holds the length.** Still eight characters, shown `HK3X-Q7RA`: the tier's letter, then the length, then
  five of mission seed (about 9.8 million a tier and length), then the check. A code names one mission. The length is
  said in words beside the code wherever the code is shown - `HARD  4 BIOMES` - since no character of the alphabet
  reads as the number. Online it travels with the tier in `SessionSetup`'s scenario, beside the seed (G0065).
- **Past the pool, biomes come again.** The biomes are dealt from a bag: none comes a second time before every biome
  of the pool has come once, and two of one base are never side by side. Up to the pool's size a mission is dealt as
  before. A biome that comes again is laid and placed afresh where it stands - the same country, never a copy.
- **The ramp spans the march.** The first biome stands at x1.0 and the last before the finale at
  `sim.mission.rampTopPercent` (150), whatever the length, those between evenly spaced; `sim.mission.rampPercent`
  goes. The finale is still not ramped (G0063). The tier says how hard a mission is, the length how long.

**Why the length is in the code.** The code is the mission (G0065). A length chosen beside it would let one code play
two marches on two machines, and "beat my time on this" would compare three biomes with nine. A seed character is
cheap to give up: 9.8 million missions a tier and length is far more than anybody will play. Codes change meaning
with it, and they never survived a build change anyway.

**Why the ramp spans the march.** At ten per cent a position, fifteen biomes end at x2.4 - x3.0 on hard - so the
length would be a second difficulty dial nobody labelled, and blocks sized to fit placement on hard at x1.875 (G0070)
would stop fitting. Spread over the march, the last biome is as hard at every length.

**Scope.** A mission's march. Not its finale, which is one and unramped; not the campaign's levels or the proving
ground; not the tier (G0063).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *The length outside the code*, as an option of the session. One code would name several missions.

**No:** *A ninth character* for the length. A second shape of code to read out and type, for what one seed character
holds.

**No:** *A ramp that keeps rising by a step a position.* The length becomes a difficulty dial.

**No:** *A daily of any length but the default.* The day's three dailies are the same three missions for everybody.

**No:** *A length capped at the pool, so no biome comes twice.* The pool grows only by porting; the length a group
wants is not the number of biomes somebody has ported so far.

**Revisit when** a long mission has been played: whether a swarm that has spent its whole adaptation pool by the
middle - about sixteen minutes on hard - leaves the second half flat; whether a biome met again reads as variety or as
repetition; whether a strip of fifteen biomes holds the frame rate and the sweeps' time.

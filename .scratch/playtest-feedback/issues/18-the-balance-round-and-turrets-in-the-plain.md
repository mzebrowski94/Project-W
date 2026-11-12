# The balance round, and more turrets in the plain

Type: grilling
Status: resolved
Blocked by: -
Section: Balance

## Question

1. Mateusz retuned 37 keys by hand after playing (uncommitted on `main`, 2026-11-12). Take them in: which of the
   guarantees they break does he keep (and what other number moves to keep it), which does he drop (and where is
   that written down)?
2. The **plain** lays more **turrets** than 09's 1-2. How many, and does placement still find room for them?

Graduates the map's *Balance after the round* fog.

## From Mateusz (translated)

> Incorporate the balance changes I tweaked in the properties (they sit uncommitted on main).
> Add more turrets to the plain biome.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-biome` (*Roster and furniture*), the diff itself
(`git --no-optional-locks diff assets/sim/`), the PACE block in `commander.properties`,
`docs/design/invariants/simulation.md` (*every gameplay speed is retuned together*), `SimConfigTest` - where the
relational guarantees live - and the G-records each failing guarantee names. 09's answer for the plain's turrets.

## His numbers (the diff, as found)

- **Pace**: commander 19 → 13, trooper 13.5 → 10, forward factor 1.6 → 1.4; grunt 8.25 → 10, runner 9.5 → 12, brute
  4.75 → 7, spitter 6 → 8, ant 7.5 → 9, sludger 7 → 8, slinger 4.75 → 6, searer and thumper 3.5 → 6.
- **Health**: grunt 150 → 200, runner 60 → 30, brute 250 → 450, spitter 70 → 200, frog 60 → 150, sludger 140 → 200,
  searer and thumper 1000 → 1300, turret 360 → 1200.
- **Grunt** `perception.alwaysAggressive` false → true.
- **Guns**: sidearm cooldown 0.30 → 0.40, speed 42 → 60, radius 0.6 → 0.3; bazooka range 55 → 80; disc gun speed
  55 → 85, bounces 4 → 8; spit cooldown 1.1 → 1.0, speed 30 → 40.
- **Rosters**: sludger, searer, thumper and leech slinger maxima up in brake, cut, fen; anthill slinger 1 → 1-2.
- **The strip**: `sim.mission.biomes` 4 → 6 (the pool holds 7).

## What it breaks (`verify.sh` on that tree, 2026-11-12: 1846 tests, 15 failed)

Each is a guarantee somebody wrote down, not a stale literal - settle each with Mateusz, don't just re-pin:

- `SimConfigTest.deletesAnOrdinaryEnemyWithOneSwingAndNotABrute` (3 tiers): the hand strike's 220 no longer kills a
  200 grunt on medium/hard tiers (250, 288).
- `.stunsWithThePulseRifleBeforeItKills` (4 tiers): a 30 runner dies before the pulse rifle's shock fills.
- `.makesEachGiantABruteGrownHalfAsBigAgainAndFourTimesAsHard`: 4 × 450 = 1800, the giants were set to 1300.
- `.slingsRunnersFromAsFarAsItCanNoticeAndLandsThemBehindHim`: the slinger (300) is no longer tougher than a brute
  (450).
- `.reloadsTheShotgunFromEmptyWithALightKickAndNoShake` (3 tiers): a full shotgun blast (216) no longer kills a
  grunt (250 off a mission, 200 × the swarm's factor).
- `AvoidanceSystemTest.walksPastEveryShippedSpatterWithoutSettingFootInIt`: a trooper sent 5 off the fire of an 11
  blast (grenade or barrel) sets foot in it for one tick. Likely, not confirmed: at 10 he reaches it later, when the
  fire has swelled past where he steered.

**Already failing at HEAD** (checked in a throwaway worktree, 2026-11-12) - not his numbers, the leech commit
(7a03a13): `SimConfigTest.slingsLeechesOntoHimThatClingAndGoOffSoonerOnTheGroundThanOnHim` and
`LeechTest.leapsOntoAManWalkingPastCloseBy`. `sim.unit.leech.leap.triggerRange=15` is past its
`perception.detectionRange=10` (12's answer says trigger 5). A one-line fix whichever way he wants it; take it here.

And what passes but now lies (judgement rows and comments - no test catches them):

- The **PACE** ratios: a grunt now walks at a trooper's speed, a runner outpaces a trooper and nearly the commander
  (12 vs 13); the simulation row says ratios survive a retune. Keep the row and amend the PACE block, or record a new
  pace.
- `alwaysAggressive` on the grunt leaves the swarm's Bloodhound adaptation nothing to work on (the comment says so).
- Comments counting rounds ("two hits kill a grunt", a spitter "two sidearm rounds", a sludger "twice a spitter's
  health", the turret's "fourth blast takes it", the ant "slower than a grunt").
- Six biomes a strip: the sweeps' run time, and whether "never two of one base side by side" still deals from 7
  (8 once 16 adds the lava biome).

## Open

- Each broken guarantee above: keep (move which number?) or drop (which record?).
- The plain's turret count, and whether `pointReach` placement holds them on every seed; if more turrets need a
  new placement rule rather than numbers, that part moves to 19.

## Done when

His numbers in, every guarantee kept or dropped on the record, comments true, the plain's turrets raised with the
sweeps green, `verify.sh` green, nothing committed. **Mateusz commits before 10, 15 or 19 start** - every later ticket's
sweeps run on these numbers, and a worktree session would start without them.

## Settled in grilling (2026-11-12)

All agreed by Mateusz. His diff stands except where a line below moves a number. Aim of the round, in his words:
harder, aliens faster and more aggressive, and **the squad killing things closer in without the game slowing down**
(a medium squad shot everything dead long before it arrived).

**Guarantees broken by his numbers**

1. Hand strike `sim.melee.damage` 220 -> **300**: kills a hard-tier grunt (288), not an easy brute (478). G0013 kept.
2. Shotgun `sim.weapon.shotgun.damage` 36 -> **48**: a full blast (288) kills a hard grunt. Kept.
3. Pulse rifle stuns before it kills: `sim.weapon.pulse.stunAfterHits` 4 -> **3**, `damage` 12 -> **10** (an easy
   runner at 32 takes 4 bolts and is stunned on the third). Runner stays 30. Kept; the gun's comment rewritten.
4. Giants **1800** (four times a 450 brute). G0071 kept as written; numbers follow.
5. Slinger **540** (1.2 times a brute, as before). G0074 kept.
6. PACE: grunt = trooper (10) stands. "A trooper outruns a grunt" is **dropped**; the PACE block rewritten as the new
   order - commander 13 > runner 12 > trooper 10 = grunt 10 > ant 9 > spitter, sludger 8 > brute 7 > giants,
   slinger 6 - with the runner as the one you shoot because you cannot run from it. The simulation row stays: it
   binds the next retune.
7. Grunt `alwaysAggressive=true` stands; Bloodhound `sim.upgrade.bloodhound.weight` 10 -> **5**, comment says it now
   works on the spitter, frog and leech only.
8. Leech (failing at HEAD): `sim.unit.leech.perception.detectionRange` 10 -> **15**, keeping his trigger 15
   (lose range at least as far).
9. Turret 1200 stands; comment: "the twelfth blast takes it".
10. Spitter 200, sludger 200 stand; the "fragile shooter" and "twice a spitter" reasons rewritten - shooters are
    mid-weight now.
11. Plain `furniture.turretsMin=4`, `turretsMax=6`; the mission sweep proves room on every seed. If it cannot be
    placed by `pointReach`, stop and raise it (19 takes placement).
12. Avoidance (`AvoidanceSystemTest`, caused by trooper speed 10): the code fix - in `AvoidanceSystem.weigh` a
    direction whose ray passes within the two radii of a blob takes full danger, so a seam narrower than his body is
    closed. Measured by angular width, not centre-alignment alone. Movement invariant row.

**Closing the distance** - kill distance is roughly *where the squad opens fire - closing speed x time to kill*:
shorten where they open fire, close faster, arrive together. Never by cutting the squad's damage (pace).

13. Trooper `perception.detectionRange` 34 -> **24**, `loseRange` 40 -> **32**. Arc, all-round 12, the commander's gun
    unchanged.
14. Nests: `sim.nest.standsOut` 20 -> **30** (still picked out at 54, held to 62). **A nest counts as a threat** in a
    trooper's choice (no `threatBonus` against it), and a man who has picked a nest out **keeps it** until it dies or
    something is inside his all-round range. Amends G0080 ("reach only, never threat").
15. The charge is **the leap from farther out** (no new system - a second way to close in would be a defect):
    grunt trigger 16 -> 24, sequence 2 -> 3; runner 14 -> 22, 2 -> 3; brute 22 -> 28, stays 3; ant 13 -> 18, stays 1;
    their leap cooldowns x0.75 (grunt 1.2, runner 0.9, brute 1.8, ant 2.25). The coil stays the tell.
16. Amble more: every strain's `wander.radius` x2, `pauseMin/MaxSeconds` x0.5, `speedFactor` +0.1. The sweeps prove the
    start stays clear.
17. Notice from farther: every strain's `perception.detectionRange` and `loseRange` x1.3 (rounded), except the runner
    (60) and the boss (58). Lose never inside detection, at every adaptation rank.
18. A point being taken: `sim.controlPoint.aggroRadius` 48 -> **72**, `sim.nest.rouseRadius` 48 -> **72**,
    `sim.controlPoint.captureSeconds` 5 -> **7**. If 72 reaches nests from points, raise
    `sim.mission.placement.pointKeepOff` past it.

**Fouled line** (new word, written in the glossary) - built here:

19. A mate on his feet, trooper or commander, fouls a trooper's line when the shot's path to the target passes closer
    to him than his radius plus the shot's. A path clear of his edge is open ("past his peers, along the edges").
    Men down, mates beyond the target and thrown grenades foul nothing. Every gun a trooper fires, the bazooka too.
    No friendly fire: a shot that reaches a mate still passes through - fire discipline, not physics. A man on a
    **turret** is exempt (a mount fires where it points).
20. A fouled trooper: (1) **aims gently** - across the target's own width, centre outwards, firing on the first line
    clear of every mate; (2) else **another target** he can see on a clear line; (3) else **holds** this tick. No
    sidestep (the approach offset spreads them). Rejected: firing anyway with a miss chance - dice the player cannot
    see.
21. The commander's own shots are never fouled. No new look for a held trooper (no muzzle flash already shows it).
22. Mates found through `SpatialGrid` along the segment, never a loop over every man; no allocation per shot.

**Records**

- New **G0084** *A trooper fires past his mates, never through them* (19-21; rejected alternatives: friendly fire,
  a miss chance, sidestepping for a line).
- **G0080** amended (14) - Mateusz kept 14 over G0080's Do-not-build (2026-11-12): "a threat weight on nests" leaves
  the column, "a branch on `Nest` in the trooper's choice" stays. So the threat and the keeping are a field every
  target already carries (beside `Enemy.standsOut`, zero = off), never a check on what the target is.
  G0071, G0074, G0013 unchanged - the numbers follow them.
- The PACE block rewritten (6). Invariant rows: squad and objectives (13, 14, 19-21), movement (12).
- `new-biome` amend step for the plain's turrets (11).

## Answer

Built (2026-11-12), `verify.sh` green, nothing committed. Mateusz's diff stands except where the points above moved a
number. Changed on the way, each agreed with him in session:

- **Trooper sight 27/34**, not 24/32: the frog's gun reaches 26, and sight was raised to 34 on 2026-09-24 because frogs
  outranged a squad; `sim.nest.standsOut` 27 keeps nests picked out at 54. New guarantee
  `SimConfigTest.seesFurtherThanAnyAlienStandsOffToShootFrom`.
- **Leech leap trigger 6** (detection 10, lose 26), not 15: aimed once (G0012), a leap from 15 cannot catch a walking man
  whatever its flight. The leech tests failing at HEAD pass.
- **Point rouse and nest rouse 58, `pointKeepOff` 60**, not 72/74: past 60 nests no longer fit on every seed (66: 6
  seeds, 74: 15). A capture still rouses no nest.
- **Bloodhound weight stays 10**: point 7's premise was wrong - a mission (and every long level) switches
  `alwaysAggressive` off for every strain, so the grunt's `true` only reaches arenas and Bloodhound works on everyone.
- **Ant leap flight 0.57 s** (32 x 0.57 = 18.2 past its new 18 trigger) - one leap must cover its trigger.
- **Spitter and frog patches 8 and 9**, not doubled: their patch must stay inside closeRange-range (9 and 10).
- **The avoidance fix** scores a ground way by whether its ray crosses the circle (full danger) or misses it (none),
  not by cosine: width alone left the way round looking barely better than straight on.
- **Records**: the G0080 amendment is a new record, **G0085**, by the repo's convention; the fouled line is **G0084**.
- **`Life.onTheField()`** is now the one definition of the men on the field (moved from `ReinforcementSystem`, which the
  squad package may not reach), used by the fouled line, the reinforcements, the nests and `Simulation`.
- **Net `SessionTest`**: its drafting world (`TestWorlds`) now has aliens that notice nobody and bite for nothing - two
  draft tests had depended on who survived Thicket's fight, which any balance change moves.

Found in review and fixed: a kept nest was lost for good after one fouled tick (now kept until it dies or something he
could shoot is inside his all-round range); a mate behind him fouled his line; mates gathered once per candidate rather
than once per aim; stale comments (ant, ramp of six biomes).

Not his, noted: `sim.unit.leech_slinger.health` went 300 -> 450 in the working tree mid-session (not by this session);
left as found. Its speed stays 4.75, outside the PACE order's "slinger 6".

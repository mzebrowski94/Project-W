# Wounds - scoped work

**Status: planned 2026-09-29; H0-H5 all done 2026-09-29 - the work is built. What is left is the playtest: the numbers in §7 and §5.7.** Read this first in every session of the
wounds work, and tick the session in §0 when it lands. Runs **before** upgrades S6
(`upgrades-scope.md`); it depends only on upgrades S3-S5, which are done. **This doc expires when
H4 ships.** As each session lands, what it settles moves where it belongs - invariants into
`README.md`, words into `glossary.md`, records into `decisions/` - and what is left here is the record
of why the build order was what it was.

Written from Mateusz's brief of 2026-09-29 (twelve points, §2), a check of it against `main` at
`a84bd92`, the records it touches (G0006, G0018, G0020, G0023, G0026) and four questions he answered
the same afternoon (D1-D4).

## 0. Where it stands

| # | Session | Size | Stops with | Status |
|---|---|---|---|---|
| H0 | Records and words | S | docs only | done 2026-09-29 |
| H1 | Wounds core: `Vitality`, harm classes, every source mapped | L | the game plays on wounds, headless; the wound shown on the field and the panel | done 2026-09-29 |
| H2 | Conditions: shield, stun, what a wound costs | M | respawn shield, a stun any source can declare, the wounded slower | done 2026-09-29 |
| H3 | The swarm's health | S | every strain and nest scaled by one factor, the hand strike re-anchored | done 2026-09-29 |
| H4 | Medkits and the two upgrades | M | medkits on both Adventures, troopers going for them, *self-treatment* and *thick skin* in the catalogue | done 2026-09-29 |
| H5 | Hit points and damage as whole numbers (G0054) | M | every health and damage an `int`, one rounding rule, an ArchUnit rule holding it | done 2026-09-29 |

**Order rules.** H0 before H1: the rows H1's tests enforce are written first, marked `deferred`. H1
before H2 and H4: both read the wound count. H3 after H2, so the factor is set against men who are
both tougher (wounds) and weaker when hurt (the wound's cost); it is a starting value, and the
playtest gate tunes it. H4 is independent of H2 and H3. Upgrades S6 after H4, so Firepower is judged
on the health model the game will ship with.

## 1. The shape in one screen

- **A man has no health.** Commanders and troopers carry a wound count against a maximum, and a state.
  Aliens, turrets, nests, barrels, mines and rabbits keep `Health` exactly as they are. Nothing on the
  player's side has a hidden number underneath the wounds.
- **Every blow declares two things**: an `amount`, which is all a thing with `Health` reads, and a
  **harm** - `WOUND`, `DOWN` or `KILL` - which is all a man reads. G0020 finished: lethality belongs
  to the source, and for a man the amount is now not even consulted.
- **Two machines, not one.** *Vitality* is the one state machine: `STANDING -> DOWN -> (Dying) ->
  (reaper) -> Respawning ticket`. **Wounded is not a state** - it is STANDING with wounds above zero,
  because a wounded man does everything a standing one does. *Conditions* are timers that compose
  freely on top: shield, grace, stun, slow. One machine that enumerated them would need a state for
  every combination (standing-shielded-stunned-in-grace...).
- **One transition function.** Every change to a man's vitality goes through `Vitality`'s own
  methods, called from `DamageSystem` (harm), `Aid` (revive), `PickupSystem` (medkit) and one mend
  clock. The harm table in §5.2 is the whole of the rule and is tested as a table.
- **Maximum wounds is folded at the read.** The base is on the unit's definition; an upgrade moves it
  through `Modifiers` like every other stat. This closes the open question "health upgrades need
  health stored as damage taken" - a wound count *is* damage taken.

## 2. Decisions taken (2026-09-29)

From the brief:

| # | Point | Decision |
|---|---|---|
| B1 | 1, 1.a | Commanders and troopers: wounds and states, no HP underneath. A state is an enum field on a component (G0010); no FSM object, no component per state. |
| B2 | 1.b | A wounded man is slower and fires slower. Two factors, each read in one place (§5.4). The hand strike and the grenade are not slowed: the sweep is the answer to a leap, and a wounded man who cannot answer one is a death spiral. |
| B3 | 2, 4 | Every source declares a harm class. Mapping in §5.3. |
| B4 | 3 | Enemies, map objects and turrets keep hit points. |
| B5 | 4 | Fire and venom wound and **shove** him away from the circle, then grant grace, so ground cannot fill every wound in one step. |
| B6 | 5, 6 | A **shield** takes one hit of any class - a stun and an ant's grab included - and shatters. It does not stack. Upgrades may later change that. |
| B7 | 5.a | A commander comes back with a **respawn shield** that also runs out after a few seconds, drawn. It replaces `respawnInvulnerableSeconds`. |
| B8 | 5.b | Maximum wounds is a stat (`MAX_WOUNDS`); the upgrade that raises it is *thick skin* (H4). |
| B9 | 7 | The interface shows wounds, shield and stun (§5.6). |
| B10 | 8, 8.a | A **medkit** is a pickup worth something only to a wounded man, and clears every wound. A few on each Adventure level. |
| B11 | 9 | A man who is down takes no `WOUND`. See D2 for the rest. |
| B12 | 10 | *Self-treatment*: a commander's wounds clear on their own some seconds after the **last** one. A stat, `MEND_SECONDS`, zero meaning off. |
| B13 | 11 | Being revived clears every wound. |
| B14 | 12 | **Stun** is the existing `Stunned`, with a duration a source may declare. Nothing declares one yet; the enemy that does comes later. The duration is a stat. |
| B15 | 14:09 follow-up | **The swarm's health rises with the men's**, by one factor on every strain and the nest (§5.7, H3). |

Answered by Mateusz:

| # | Question | Answer | What it means here |
|---|---|---|---|
| D1 | What does a lethal hit do to a standing man? | **Split: DOWN vs KILL** | Three classes, not two. Explosions put him down, where he can be revived; claws, a leap, an ant's last bite and a fall into a pit kill outright. G0020's asymmetry - distance is worth keeping - survives. |
| D2 | How immune is a man who is down? | **To `WOUND` only** | Stray spit, fire and a grenade cannot finish him; a barrel, a mine or claws still can. G0020's "rule that costs nothing" is narrowed rather than deleted: a man left in the open is still at risk from the things that would have killed him standing. |
| D3 | Do troopers get the whole model? | **Yes, and they go for medkits** | Same classes, same base maximum. A wounded trooper with nothing to fight walks to a medkit within reach. |
| D4 | Names? | **`WOUND` / `DOWN` / `KILL`** | A class is named for what it does to a man with no wounds. States: `STANDING` (wounds as pips) and `DOWN`; dying and dead are the existing throes and reaper. |

Taken by Claude, to be overturned if wrong:

| # | Decision | Why |
|---|---|---|
| C1 | Several `WOUND` hits on one tick are **one** wound. | `Damage` already folds a tick's hits into one record, worst class winning. A shotgun spray is one wound, not three. |
| C2 | Grace lasts 0.5 s after a wound, and turns away **`WOUND` only**. `DOWN` and `KILL` get through. | A pellet must never buy protection from a leap. |
| C3 | Grace replaces the per-source `victimInvulnerableSeconds` **on men**. Aliens keep theirs. | Protection after a wound is a fact about the man (the rule `Aid` already follows), not about what hit him. |
| C4 | A shield turns the harm, the stun, the slow and a grab, and **not the shove**. | The hit is still felt; only the cost is taken away. |
| C5 | Revive keeps its existing full protection (`reviveInvulnerableSeconds`). | Unchanged behaviour; `Invulnerable` stays for that, for aliens' recovery and for god mode. |
| C6 | Wounds belong to a life (G0044): a respawned commander starts at zero. Upgrades still belong to the slot. | Same line G0044 and G0049 already draw. |
| C7 | The mend clock restarts on every new wound. | "Ten seconds out of trouble", not "ten seconds after the first". |
| C8 | A medkit is scenery to the aliens and to weapons: they cannot eat it, and it carries no health. | A medkit that could be denied at range is a second rabbit, and a decision nobody has asked for. |
| C9 | An escaped ant grab is a `WOUND` on release, with a shove away from the ant. | Point 4. The shove is what makes the escape read as one. |

## 3. Invariant check (`feature` skill, step 2)

### Consistent

| Rule | Why it holds |
|---|---|
| Pillar 1: arcade first | Discrete, readable steps; nothing to compute. Hits, not amounts. |
| Pillar 2: the commander can be killed, no invulnerability | A shield takes one hit and is gone; the respawn shield also runs out. Being down protects from nothing that could kill him standing (D2). |
| Pillar 5: state is visible | Wounds, shield, stun and grace are all drawn (§5.6). The panel still has no health bar. |
| Pillars 6 and 7 | Integers and tick counts only; everything lives on components the snapshot carries; no dice. |
| G0010: a mind is a field | Vitality is an enum field, conditions are timers on permanent components. |
| `onlyTheSpawnerChangesWhatAnEntityIsMadeOf` | `Vitality` and `Shield` are carried from spawn by every man and switched by their fields. |
| G0023: one revive path | `Aid` stays the single writer; it now clears wounds instead of restoring a fraction of health. |
| G0026: aliens do not finish a man who is down | Unchanged: nothing hunts him. D2 only covers what reaches him by accident. |
| G0049: an upgrade belongs to the slot | `MAX_WOUNDS` and `MEND_SECONDS` are ordinary stats, read through `Modifiers`. |

### Amended - H0 rewrites each one openly

| Rule | Amendment |
|---|---|
| G0020 and its rows: "whether a blow kills outright or leaves a casualty is declared by the source" | Now three classes. `ContactDamage.fatal` and `.wounding()` become `harm`. The rows keep their meaning: nothing about a man's fate is derived from an amount. |
| G0020: "a casualty is at zero health, so anything that reaches him finishes him" (`CasualtyTest.finishesACasualtyWithTheNextHitThatReachesHimWhateverItIs`) | **Narrowed by D2**: anything that could put down or kill a standing man finishes a man who is down; a `WOUND` does nothing to him. The test is replaced by two. |
| "Gunfire and blasts leave a casualty; claws, a sweep and a leap kill" | Gunfire **wounds**; blasts put down, **except a grenade's, which wounds**; claws, a leap and an ant's last bite kill. |
| G0018: "a blast is uniformly decisive inside one circle; everything standing in it goes down" | Still one circle with one meaning inside it, and still decisive for everything with `Health`. For a man, the meaning is the **blast kind's declared harm**. This is the answer G0018's own revisit clause asked for ("a weapon meant to wound ... the answer then is a second kind of blast rather than a curve"). A grenade is still not a free button: it costs its thrower a wound, and a second one puts him down. |
| G0006: "a side with nothing on offer leaves it standing" | Also: **a pickup worth nothing to this taker is not claimed.** Decided at the claim from the taker's state (a medkit and a man with no wounds), exactly where the barrel's bite is already decided - never a flag on the pickup. |
| Fire and slick rows: "puts a man standing in it on the ground rather than killing him" | "... wounds a man standing in it and shoves him off it." |
| Glossary *casualty*, *decisive*, *fire*, *recovery* | Reworded in H0; *wounding* today means "puts him down" and is retired to avoid a clash with `WOUND`. |
| Open question "Health upgrades need health stored as damage taken" | Answered for men. Still open for aliens, where it is an adaptation question. |

### New - the design did not say, and now does

Written as `deferred` rows in H0, promoted by the session named.

- A man's harm is one of three classes declared by the source; one past his maximum wounds puts him down (H1).
- Several wounds on one tick are one wound (H1).
- Grace turns away a wound and nothing else (H1).
- A man who is down takes no wound; a hit that could have put him down or killed him standing finishes him (H1).
- Revive, a medkit and self-treatment all leave him with no wounds, through `Vitality`'s one method (H1, H4).
- A wounded man is drawn as one, on the field and on his panel (H1).
- Wounds are slowed in one place each: speed in `Pace`, fire in `Guns.spend` (H2).
- A shield takes exactly one hit of any class and is gone (H2).
- A stun from a source is the existing `Stunned`, and a stunned man is drawn as one (H2).
- A medkit is taken only by a wounded man, and is never consumed by anyone else (H4).

## 4. Words (H0, in `docs/glossary.md`)

| Word | Meaning |
|---|---|
| **harm** | What a blow does to a man, declared by its source: `WOUND`, `DOWN` or `KILL`. Aliens and objects read the amount instead. |
| **wound** | One step of hurt on a man. He carries up to his maximum and fights on, slower; one more puts him down. |
| **wounded** | Standing with at least one wound. A description of a standing man, not a state of its own. |
| **down** | Kept: the state of a **casualty**, bleeding out. Now reached by a `DOWN` hit or by one wound too many. |
| **grace** | The half second after a wound during which another wound cannot land. Not a **recovery**: that is an alien's, and turns everything. |
| **shield** | Something that takes the next hit of any class instead of him, and breaks. |
| **medkit** | A pickup that clears every wound, worth nothing to a man without one. |
| **mend** | Wounds clearing on their own after a spell out of trouble. What *self-treatment* buys. |

## 5. Architecture

### 5.1 Components

| Component | Holds | Replaces |
|---|---|---|
| `Vitality` (commanders and troopers, permanent) | `state` (`STANDING`, `DOWN`); `wounds`; `graceTicks`; `mendTicks`; the bleed-out and stabilise fields `Casualty` has today | `Casualty`, and `Health` on men |
| `Shield` (commanders and troopers, permanent) | `up`; `remainingTicks` (0 = until hit) | the respawn half of `Invulnerable` |
| `ContactDamage` | `harm` instead of `fatal`; `stunTicks` (0 = not mine) | `fatal`, `.wounding()` |
| `Damage` | `harm` (worst of the tick: KILL > DOWN > WOUND), `stunTicks` (longest) | `fatal` |
| `BlastsOnDeath` / blast kind | the kind's `harm` | - |

`Casualty` is renamed and grown rather than joined by a second component, so "down" and "wounds"
cannot disagree - two flags on two components is how a man would end up down and standing at once.
`Dying` stays shared with everything that dies. Families that mean "can be hurt" become
`one(Health, Vitality)` (`PerceptionSystem`, `NavigationSystem`, `BlastSystem`, `DamageSystem`).
`Health` appears in 18 files across `sim` and `core` at `a84bd92`; the men's half is the spawner, `Aid`
and those families.

### 5.2 The harm table - `DamageSystem`, for anything with `Vitality`

Checked in this order, once per tick, on the tick's folded `Damage`:

| He is | Shield up | Harm | Result |
|---|---|---|---|
| dying | - | any | nothing |
| any | yes | any | shield breaks; no harm, stun, slow or grab; the shove still lands |
| DOWN | no | WOUND | nothing |
| DOWN | no | DOWN, KILL | dies (`Deaths.kill`) |
| STANDING | no | WOUND, in grace | nothing; the shove still lands |
| STANDING | no | WOUND | `wounds + 1`; above his maximum, DOWN; otherwise grace starts and the shove lands |
| STANDING | no | DOWN | DOWN, whatever his wounds |
| STANDING | no | KILL | dies |

Stun and slow apply only to a man left standing. `Invulnerable` (revive protection, god mode) still
turns everything, before the table.

### 5.3 Sources

| Source | Harm | Notes |
|---|---|---|
| Spit, spew, boss barrage | WOUND | Ranged. |
| Fire, venom slick | WOUND + shove | Shove away from the circle's centre. |
| Grenade blast, a commander's or a trooper's | WOUND | Kills every alien it catches as before - their `amount` is unchanged. |
| Barrel, mine, charged corpse, charged spit, a turret going up with its crew on it | DOWN | |
| Claws on contact (grunt, brute, runner, frog...) | KILL | Not in the brief; unchanged from today. |
| Leap | KILL | |
| Ant's grab, carried to the end | KILL | The ant's own last bite. |
| Ant's grab, escaped (the ant dies holding him) | WOUND + shove | On release (C9). |
| Lava, pit, downhill (future) | KILL | An upgrade may soften it; nothing built now. |

The table becomes `harm=` keys beside each source's `damage=` in `assets/sim/*.properties`.
`SimConfigTest` refuses a source with a damage and no harm.

### 5.4 Where each number is read

| Number | Read in | Notes |
|---|---|---|
| maximum wounds | `DamageSystem`, through `Modifiers` (`MAX_WOUNDS`, COUNT, UNIT) | Base from `sim.commander.maxWounds` / `sim.trooper.maxWounds`. |
| wounded speed | `Pace.of` | Already the one place speed is multiplied; the commander goes through it too. |
| wounded fire cooldown | `Guns.spend` | Already the one place a pull is charged. |
| grace | `DamageSystem` | |
| mend | a clock beside the bleed-out in `CasualtySystem` (renamed `VitalitySystem`), through `Modifiers` (`MEND_SECONDS`, COUNT, UNIT, zero off) | |
| stun duration | `DamageSystem`, through `Modifiers` (`STUN_DURATION`, SCALE, UNIT) | Nothing declares a stun until the enemy that does. |

### 5.5 Medkit

A `PickupKind.MEDKIT`, a `PickupDef` and a `Grant.Mend` (heals every wound). The claim in
`CollisionSystem` asks whether the grant is worth anything to *this* taker - `Mend` only to a man with
wounds - through one helper, beside the side check already there (G0006 amendment). A trooper goes for
one the way he goes for a gun: ranked **fight, order, casualty, medkit, gun, objective**, only within
`sim.trooper.medkitReach`, and he drops the walk the moment it stops being worth it (somebody else took
it, or he stopped being wounded). No roll: a wounded man with nothing better to do always goes.
Placement: scattered beside control points on both Adventures, a count per level in
`level.properties`, never hand-placed.

### 5.6 What is drawn

- **Field**: a wounded man drawn as one (a pip per wound at his feet; troopers too). A shield is a
  ring round him, with the respawn one visibly running down. A stun has a look of its own, distinct
  from a stagger. Grace is too short to draw.
- **Player panel**: wound pips after his label. Status gains `STUNNED`; down, KIA and aboard stay.
- **Terminal**: a commander down is already a dispatch; a wound is not worth one.

### 5.7 The swarm's health (H3)

Wounds make a man roughly twice as hard to put down **by gunfire** and no harder to kill **by claws or
a leap**, and a wounded man is slower. The swarm is mostly claws, so the correction is modest and
uniform: one factor, `sim.swarm.healthPercent` (125, see H5), on every strain's health - the boss's included - and on
the nest's. Not on barrels, mines and rabbits, which are triggers at one health, and not on turrets,
which are the players'.

**One factor, not edited numbers**, for the reason the PACE block retunes every speed together: the
ratios between strains survive, and the playtest turns one key. It is folded **where `SimConfig`
binds the definitions**, so a unit spawned, a unit evolved (it arrives at the new strain's full
health) and `SimConfigTest`'s relational checks all see the same number - one place reads it
(`CLAUDE.md`, *Small patch, right shape*). It is config, not a stat: nothing moves it during a level,
so the open question about health adaptations stays closed for it.

At 125 % - shots to kill with the sidearm (50), pulls with the shotgun at point blank (6 x 22), and what
the hand strike does:

| | Health now -> 125 % | Sidearm | Shotgun | Hand strike (190) | Railgun (300) |
|---|---|---|---|---|---|
| grunt | 150 -> 188 | 3 -> 4 | 2 -> 2 | deletes | 1 -> 1 |
| runner | 60 -> 75 | 2 -> 2 | 1 -> 1 | deletes | 1 -> 1 |
| frog | 60 -> 75 | 2 -> 2 | 1 -> 1 | deletes | 1 -> 1 |
| spitter | 70 -> 88 | 2 -> 2 | 1 -> 1 | deletes | 1 -> 1 |
| brute | 250 -> 313 | 5 -> 7 | 2 -> 3 | staggers | 1 -> 2 |
| ant (flank, back) | 450 -> 563 | 9 -> 12 | 4 -> 5 | staggers | 2 -> 2 |
| boss | 7500 -> 9375 | 150 -> 188 | - | - | 25 -> 32 |
| nest | 600 -> 750 | 12 -> 15 | 5 -> 6 | - | 2 -> 3 |

**What that does.** The light strains keep their breakpoints - still two shots - so the pressure
lands on the grunt, the brute, the ant and the structures, which are what a squad that goes down less
often now has time to grind. A blast (1000) is still decisive on everything but the boss, as it is
today. The hand strike must rise with the grunt or `SimConfigTest.deletesAnOrdinaryEnemyWithOneSwingAndNotABrute`
fails the build, which is the check doing its job. If the playtest wants the light strains tougher
too, that is a breakpoint decision per strain (61+ health for a third sidearm shot), not a bigger
factor.

## 6. Sessions

### H0 - Records and words (S, docs only)
G0053 *A man is hurt in wounds, not health* (below). Amendments to G0006, G0018, G0020 and G0023 as in
§3. `deferred` rows for everything in §3's *New*. Glossary per §4. A pointer to this doc in
`CLAUDE.md`'s lookup table.

**G0053 do not build**: HP hidden under the wounds; a WOUNDED state separate from the count; one state
machine enumerating conditions; per-source protection on men; a harm derived from an amount; a medkit
with a `canTake` flag; grace that turns a `DOWN` or `KILL`; a man who is down immune to everything
(D2); adding or removing `Vitality` or `Shield` mid-tick.

### H1 - Wounds core (L)
`Casualty` becomes `Vitality`; `Health` comes off men; `harm` replaces `fatal` on `ContactDamage` and
`Damage`; the harm table in `DamageSystem`; grace; `Aid` clears wounds; every source in §5.3 declared.
The wound drawn on the field and as pips on the panel. `WorldCodec` carries the new fields.
**Tests first**: the harm table as one table test, then a test per source. **Stops with** the game
playable on wounds, the same determinism hashes run to run, and `Health`'s old rows either passing or
rewritten openly.

**What H1 settled that the plan above did not say** (2026-09-29):

- **Harm is declared by category where the blow is spawned, not by a key per source.** Gunfire and
  ruined ground wound, claws and a sweep kill - exactly how `fatal`/`.wounding()` were declared before,
  in `Spawner`. Only the blast kinds differ within their category, so only they are keys:
  `sim.blast.harm=down` and `sim.grenade.blastHarm=wound`. The `harm=` key beside every `damage=` and
  the `SimConfigTest` check for it (§5.3) were not built: no second value exists for any of the other
  categories, so a key would be a number nobody can turn. Revisit with the first heavy shot (G0053's
  revisit).
- **`Casualty` became `Vitality`**; `CasualtySystem` kept its name - it is still the bleed-out's clock.
  Grace is an ordinary countdown and is `CooldownSystem`'s.
- **The throw off ruined ground** is `ContactDamage.shoving` and `Damage.shove`: a knockback that holds
  nobody, applied only to a man left on his feet, taking his control (`Stunned`) for
  `sim.wound.shoveSeconds` and bleeding off over exactly that long. The ant's escape uses the same path.
- **A blow a man survives no longer exists** - claws kill him. Three combat tests about contact
  cooldown and one blow a tick moved to a turret, which has health; the two stun tests about a survived
  blow moved to the hand strike on a brute; the held-commander tests put the hold on him directly, as a
  clinch, a shove or jaws now do.
- **Protection after a hit, on a man, is only grace.** `victimInvulnerableSeconds` is not granted to him;
  `Invulnerable` still covers revive, respawn and god mode.

### H2 - Conditions (M)
`Shield` and the respawn shield; `stunTicks` on sources and `STUN_DURATION`; the wounded factors in
`Pace` and `Guns.spend`; the shield and stun looks. A test scenario or dev key that stuns, since no
enemy does yet.

**What H2 settled** (2026-09-29):

- **A stun is declared on a gun** - `WeaponDef.stunSeconds`, zero on all ten - rather than a dev key: it is
  the shape the enemy that stuns will use, and setting `sim.weapon.spit.stunSeconds` tries it in play.
  `Stunned.dazed` is what tells a stun from a stagger; `AppearanceSystem` publishes `STUNNED`.
- **A shield breaks in two places**: `DamageSystem` for any hit, `CollisionSystem` for a grab, which is not a
  hit. The lock a blow puts on its victim at contact is skipped for a shielded man, so a shield turns
  that hold as well as the harm.
- **`STUN_DURATION` is a stat with no upgrade on it yet** (range -75..+100 %), read in `DamageSystem`, which
  now takes `Modifiers`.
- The panel says SHIELD and STUNNED after his wound squares; there is no new posture, so nothing that
  asks whether he is standing had to learn one.

### H3 - The swarm's health (S)
One key, `sim.swarm.healthPercent` (was drafted as `sim.unit.healthFactor`), folded where `SimConfig` binds every strain's and the nest's health
(§5.7). `sim.melee.damage` re-anchored so the hand strike still deletes a grunt and staggers a brute.
**Tests first**: `SimConfigTest`'s relational checks run on the scaled numbers already, since they read
the bound config; add one that a barrel, a mine, a rabbit and a turret are not scaled. **Stops with**
the same game at 100 %, and the numbers in §5.7 at 125 %.

**What H3 settled** (2026-09-29): the key is `sim.swarm.healthFactor`, not `sim.unit.healthFactor` - every
key under `sim.unit.` is a strain's. It is read through `SimConfig.healthOf(kind)` and `.nestHealth()`,
the one place, rather than folded into the definitions when they are bound: a config copied with
`withUnit` would have been scaled twice.

### H4 - Medkits and upgrades (M)
Medkit pickup, the claim amendment, trooper medkit drive, placement on both Adventures.
`MAX_WOUNDS` and `MEND_SECONDS` read; upgrades *thick skin* (+1 wound per rank, team) and
*self-treatment* (commander personal, 10 s) in `upgrades.properties`, with generated card text.

## 7. Keys this work adds or moves (proposed values)

| Key | Value | Session | Feel |
|---|---|---|---|
| `sim.commander.maxWounds` | 1 | H1 | Wounds he carries on his feet; the next one puts him down. |
| `sim.trooper.maxWounds` | 1 | H1 | |
| `sim.wound.graceSeconds` | 0.5 | H1 | How long one wound protects from the next. |
| `sim.fire.knockbackSpeed`, `sim.weapon.venom.slicks.knockbackSpeed` | set in H1 | H1 | Enough to clear a circle's edge. |
| `sim.unit.ant.grip.escapeKnockbackSpeed` | set in H1 | H1 | |
| `<source>.harm` | per §5.3 | H1 | One beside every `damage=`. |
| `sim.casualty.reviveHealthFraction` | removed | H1 | Revive clears every wound. |
| `sim.fire.victimInvulnerableSeconds`, `sim.weapon.venom.slicks.victimInvulnerableSeconds` | kept for aliens only | H1 | Grace does this for men. |
| `sim.wound.speedFactor` | 0.85 | H2 | A wounded man's pace. |
| `sim.wound.cooldownFactor` | 1.25 | H2 | A wounded man's time between pulls. |
| `sim.commander.respawnShieldSeconds` | 3 | H2 | Replaces `sim.commander.respawnInvulnerableSeconds=2`. |
| `sim.swarm.healthPercent` | 125 | H3, H5 | Every strain's and the nest's health, rounded to whole hit points. 100 is today's game. |
| `sim.melee.damage` | 150 -> 190 | H3 | Still at least a scaled grunt's health (188), still below a scaled brute's (313). |
| `sim.medkit.*` | radius, shine | H4 | |
| `sim.level.adventure.medkits` | 3 | H4 | Per Adventure level. |
| `sim.trooper.medkitReach` | set in H4 | H4 | How far a wounded trooper will walk for one. |
| `sim.upgrade.self_treatment.*` | `count mend_seconds personal 10` | H4 | |
| `sim.upgrade.thick_skin.*` | `count max_wounds team 1`, ranks I-II | H4 | |

## 8. Risks and what to watch

- **Tougher than today.** A spit puts a man down now; with one wound allowed it takes two. Watch the
  spitter and frog lines in playtest before touching `maxWounds`.
- **The death spiral.** Slower and slower-firing is exactly when a man most needs to get away. The
  factors are mild on purpose, and the sweep and grenade are untouched (B2).
- **Grenade spam.** A grenade now costs its thrower a wound rather than a down. If players start
  throwing at their own feet, raise the grenade's harm for its thrower's side before adding a rule.
- **Troopers leaving the line for medkits.** The drive ranks below a fight and an order; if a squad
  still dissolves, shrink `medkitReach`.

## 9. Not in scope

- A shield pickup or any shield upgrade (B6 leaves them possible).
- The enemy that stuns.
- Map hazards (lava, pits, slopes) and the upgrade that softens them.
- Wounds on aliens, and health adaptations.

## Appendix. Precedents

- Two independent machines rather than one: the *concurrent state machines* section of Nystrom's
  [State](https://gameprogrammingpatterns.com/state.html) chapter.
- A shield that takes one hit whatever its size: [Enter the Gungeon armour](https://enterthegungeon.wiki.gg/wiki/Armor)
  ("each piece of armor can absorb a single hit").
- Wounds that slow you and are cleared all at once: Helldivers 2 injuries and the stim
  ([summary](https://n4g.com/articles/helldivers-2-what-are-injuries)).
- Protection after a hit as an upgradable number: Hollow Knight's
  [Stalwart Shell](https://hollowknight.fandom.com/wiki/Stalwart_Shell).

## After H4 (2026-09-29)

What H4 settled that the plan did not say:

- **`MAX_WOUNDS` is folded every tick in `DamageSystem`** onto `Vitality.carries`, before any hit is
  weighed - one reader, and the pips are drawn from the same number the next hit meets. `maxWounds`
  stays the immutable base.
- **`MEND_SECONDS` is a count of seconds with a zero base** (`sim.wound.mendSeconds=0`), read by
  `CasualtySystem` beside the bleed-out; a new wound restarts it. A count reaches a unit now:
  `Modifiers.amount(stat, who)` checks the target, where the side-wide counts never needed to.
- **Medkits are placed by `Scenario.deployMedkits`**, `sim.medkit.perLevel` of them, one beside each of a
  share of the level's control points, on a stream of their own (`RngStream.MEDKIT`) so placing them
  reshuffles nothing else. Only a level that overrides `medkits()` gets any - both Adventures do.
- **The trooper's walk to a medkit is `TrooperMend`**, `Trooper.Mode.MEND`, between aid and crew in the
  cascade, with no roll: the reach (`sim.trooper.medkitReach`) is what keeps the line together.
- Thick Skin is tier 2 and team-wide; Self-Treatment is tier 1 and personal (`chooser`).

## H5 - Hit points are whole numbers (2026-09-29)

Asked by Mateusz after H4, when the swarm's factor gave a grunt 187.5. Settled in G0054: not for speed or
online play - Java's floats are strict IEEE everywhere (JEP 306) and the simulation is already
deterministic - but for the breakpoint, which a percentage on a float moves by an ulp. Every health and
damage is an `int`; `Whole.percentOf` is the one rounding rule (nearest, halves up, never to zero), used
by `SimConfig.healthOf`/`.nestHealth` and `Fold.scaleWhole` (the `GUN_DAMAGE` read).
`sim.swarm.healthFactor=1.25` became `sim.swarm.healthPercent=125`, so the factor is exact too. At 125:
grunt 188, brute 313, ant 563, spitter 88, runner and frog 75, nest 750, boss 9375; the hand strike's 190
still deletes a grunt and staggers a brute. Two ArchUnit rules hold it by name - a field or a method
result named for health or damage, an `amount` a blow owes, anything in `Health` - and were seen to fail
on a deliberate `float` before they were trusted.

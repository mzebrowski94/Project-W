# G0129: Engineer sets down sentries for grenades

**Status.** accepted

**Trigger.** A shooter on the players' side nobody crews; an upgrade that changes what a key does; the grenade slot.

**Enforced by.** `SentryTest` - `.firesAtAnAlienInItsReach`, `.holdsItsFireAtAnAlienBeyondItsReach`,
`.turnsOntoTheNearerOfTwoAliens`, `.sparesAManInTheLineOfItsFire`, `.drawsTheAliensFromTheMomentItStands`,
`.goesOffAtTheEndOfItsLife`, `.goesOffTheSameWayWhenItIsDestroyed`, `.woundsAManCaughtInTheBlastItGoesOffIn`,
`.stunsTheAliensRoundItAsItGoesOffAndNeverAMan`, `.stunsNoAlienBeyondItsStunsReach`,
`.showsHowMuchOfItsLifeIsLeftOnItsCharge`; `SystemOrderTest`'s rows for `SentrySystem` (after `DamageSystem`, before
`BlastSystem` and `UpgradeSystem`).

**Settled with Mateusz, 2026-11-24** (grilling). Not yet built, not yet played.

**Constraint.**

- **The sentry** is a small light-grey standing gun nobody crews, on the players' side: it turns fast onto the nearest
  alien in medium reach and fires a light rapid gun at it. Nobody gets on it, nobody hauls it. Not a **turret** (G0024)
  and not an **emplacement** (G0113).
- **The aliens hunt it from the moment it stands**: a target, as a man is.
- **It lives about sixty seconds**, its time left shown (pillar 5), and then goes off.
- **Going off** - destroyed or out of time - is a **small ordinary blast** (nobody's side, wounds a man, small damage)
  and a brief **stun** on the aliens round it, never our men, as the dash's surge stuns (G0108).
- **Engineer** is a **personal** upgrade, two ranks, tier 1. While held, the commander's grenade key **sets a sentry
  down at his feet** instead of throwing a grenade. **It spends a grenade**: the same count and most, refilled by a
  grenade cache, its wait shortened by Grenadier, raised by Bandolier. Nothing else caps how many stand.
- **Rank two: its gun fires faster.**
- **The grenade slot** shows a sentry where it showed a grenade, while he holds Engineer.
- The squad's grenades (G0051) are untouched.
- A new kind of thing, per G0008: a constant, a definition and a block in `assets/sim/`, with whatever component and
  system its standing fire genuinely needs.

**Why a new word.** A turret is a place a man gets on, and G0024 rules out a turret as a kit; an emplacement is the
aliens' standing shooter that is never a target. The sentry is neither, and borrowing either word would make its rule a
lie.

**Why grenades pay for it.** One count, one cache, one slot: Engineer changes what the key makes, not what the player
tracks (pillar 5).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A sentry a man can get on or haul.* That is a turret.

**No:** *A count or recharge of its own.*

**No:** *Thrown along the aim.* It is set down at his feet.

**No:** *A cap on standing sentries* until play shows a carpet of them.

**Revisit when** play shows sentries carrying a level, or never living long enough to matter.

## Amended by

Ticket 03 of the AFK run (2026-11-24), building the sentry - provisional, for review:

- **Its aim is a turret's turn, not a trooper's**: `sim.sentry.turnRate` as the fraction of the angle left it covers a
  second, slower while it fires (`firingTurnFactor`), and a crewed turret's judgement (`TurretSystem.ON_TARGET`) before it
  fires. Faster than a turret's (8 against 2.5; 0.6 against 0.3) so it "turns fast", and its coming round stays visible
  (pillar 5). *Turned down:* a trooper's look and fire arc, which carries his aim error drawn from the squad's stream -
  a sentry is a gun on a mount, not a man.
- **Its fire spares our men by the shot's side**, not by G0084's fouled line: its shots are on the players' side, so the
  collision rule passes them through a man in their line. *Turned down:* the fouled line, which would make it hold its
  fire behind the squad it is set down to cover.
- **It goes off by dying** (G0018): out of time, `SentrySystem` marks it dead; destroyed, `DamageSystem` has. Either way
  `SentrySystem` stuns the aliens round it through `Surges.stunRound` - the dash surge's harmless stunning blow, the one
  way - while its body is on the field, and its blast is its `BlastsOnDeath`, credited to the slot that set it down.
  *Turned down:* a stun in the blast, which G0018 keeps on nobody's side.
- **`Spawner.spawnSentry(x, y, slot)`**, beside `spawnTurret`: the sentry is a new thing with a component and a system of
  its own, not a member of an existing table, so there is no kind's spawn for it to go through. *Turned down:* a branch in
  `spawnTurret` (G0008) or the emplacements' table (theirs, G0113).
- **`SentrySystem` sits in the death window** (43, after `RespawnSystem`, before `ProjectileBoundsSystem`), with its
  reason in `docs/simulation-order.md`.
- **Time left on the charge channel**: the body's core shrinks as its life runs out, as a casualty's clock does.
- **Starting values**: `sim.sentry.radius=1.4` (a turret's 2.2), `health=300` (a turret's 1200), `lifeSeconds=60`,
  `range=30` (a trooper's sight 27, a turret's 55), `turnRate=8`, `firingTurnFactor=0.6`, `blastRadius=4`,
  `blastDamage=150`, `blastHarm=wound`, `stunRadius=10`, `stunSeconds=1`; `sim.weapon.sentry_gun` - `cooldownSeconds=0.2`
  (`turret_gun`'s 0.16), `damage=14` (its 30), `projectileSpeed=150`, `projectileRange=34`.

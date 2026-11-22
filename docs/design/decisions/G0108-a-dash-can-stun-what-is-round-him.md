# G0108: A dash can stun what is round him

**Status.** accepted

**Trigger.** An upgrade on the dash; anything that stuns, slows or shoves everything round a point without harming it;
a second kind of radial effect beside the blast; Rocketman or Pantera; making a dash faster or longer through an
upgrade.

**Enforced by.** `SurgeTest` - `.stunsAnAlienInsideItsReachOnTheTickTheDashLaunches`, `.sparesAnAlienOutsideItsReach`,
`.reachesFartherAtRankTwo`, `.sparesAnAlienBehindRock`, `.neverStunsATrooperOrAnotherCommander`,
`.setsNothingOffOnAPressWithNoDashLeft`, `.setsNothingOffWithoutTheUpgrade`, `.setsNothingOffForAPlayerWhoDoesNotHoldIt`,
`.breaksAnAliensShield`, `.leavesARingAsWideAsItsReachWhereItWentOff`; `UpgradeTest.givesTheChooserOneMoreDashAtRankOne`,
`.givesTheChooserOneMoreDashARankAndASoonerRecharge`; `DashYardScenarioTest`; `GraphicConfigTest.readsASurgeGrowingOutToItsReach`.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 1, ticket 03); the points under
*Provisional* are the implementer's, taken in an AFK run.

**Amends G0018**: a second radial effect that is **not a blast** - non-lethal, on one side, and no explosion.

**Constraint.**

- **Rocketman** (`UpgradeKind.ROCKETMAN`, personal, two ranks): on the tick a dash **launches**, every **alien** whose
  middle is within his reach, along a line rock and a shut door leave clear (`Sight.withinRange`, `Sight.clearLine`,
  as a blast checks), is **stunned** - a declared stun of `sim.commander.dash.stunSeconds` (1), through the one
  stun-duration fold. That is his **surge**. A press refused, or one with no dash left, sets nothing off; so does a
  press while a burst still runs, which is no launch (G0107).
- **Reach** is a stat, `SURGE_REACH`, a count of world units whose base is none: **5 a rank, 5 and then 10**. The fold is
  linear per rank, and a base that gave 6 at rank one would set a surge off for everybody who does not hold it, so the
  6 / 10 asked for is 5 / 10.
- **It lands as a blow**, through `Damage` (`Damage.landStunning`), so it behaves as every other blow does (G0057): a
  harmless blow that lands, plus the stun. A shield it meets breaks and takes the stun; the alien is provoked towards
  where he stood. A burrowed worm or a slung rider turns it away as it turns away every blow.
- **Aliens only.** Never a trooper, never a commander - his or another player's - never anything neutral (a barrel, a
  slick), never a door. Its neighbours come from a `SpatialGrid` filed once a tick, on the first surge of it.
- **The boss is not exempt.** A declared stun already lands on it; a surge does too. (It takes no *shock*, G0059 -
  a different thing.)
- **Pantera** (`UpgradeKind.PANTERA`, personal, two ranks): each rank `count dash_most chooser 1; scale dash_recharge
  chooser -13` - 3 and then 4 dashes, the recharge 13 % and then 26 % shorter. A dash's **speed and distance are not
  upgraded**.
- **Shown**: a blue-violet ring (`graphic.color.rocketman`) where the surge went off, on the slam's circle (G0071),
  growing out to its reach as it fades; the aliens it stunned show their own daze marks.

**Why at the start of the dash.** It is an escape, not an engage. The moment to dash is when something has leapt at him
or a pack has closed; the surge holds what is round him *where he was*, and the dash carries him clear of it. A surge at
the end would make the dash a charge into the pack - the opposite of what a dash is for.

**Why aliens only.** It is a perk he chose, on a move he makes often. If it stunned his own men, every dash beside his
squad would cost him their guns for a second - an upgrade that punishes using it next to the thing pillar 3 says he is
fighting to keep. A blast is on nobody's side (G0018) because it is a weapon with a cost; this is a defence with none
but the dash it rides on.

**Why linear ranks.** Every upgrade folds as `Modifiers.sum` times ranks (ADR 0017); a curve per rank is a new fold
form for two cards. So Rocketman's 6 / 10 is 5 / 10, and Pantera's "+1, then +1 and 25 % faster" is a quarter split
over two ranks - rank one shortens the recharge slightly too.

**Provisional, for review (AFK run).**

- **The surge goes off inside `CommanderControlSystem`'s launch**, through a helper, `Surges`, it is handed as it is
  handed `Dashes`. *Rejected:* a `SurgeSystem` after it - it would need a "launched this tick" flag on `Dash` only
  the control system may write, and the launch is already the one place that knows a press became a dash.
- **The entry on `Damage` is `landStunning(ticks, slot)`**, with `cameFrom` the place he stood. Credited to his slot,
  though a blow that owes nothing never kills. *Rejected:* making `stun` public - a writer setting part of a blow
  without landing it is what G0057 rules out.
- **Pantera's recharge is 13 % a rank**, 26 % at rank two, since a percentage is whole. *Rejected:* 12 (24 %) - a
  quarter read either way, and 13 also sits nearer "25 % faster" read as a rate (20 % off the time).
- **Reach 5 / 10**, as the ticket's fallback says.
- **Weight 10, tier 1**, as Bandolier's and every other personal upgrade's.
- **Where it measures from**: where he and the aliens stood at the start of the tick he pressed, before anything moved -
  the same place the dash's puff is left. *Rejected:* measuring after the solver - the surge would go off a tick of
  movement away from the puff that marks it.
- **The ring is a puff with a reach** (`Puff.reach`, look `ROCKETMAN`, `Spawner.spawnSurge`), left where it went off
  for `sim.commander.dash.surgeSeconds` (0.35), drawn from `graphic.dash.surge.grownFrom` (0.4) of its reach out to all
  of it, with the slam's fill and outline alpha. *Rejected:* a ring on his own appearance - it would travel with him,
  away from the aliens it stunned, and need his reach written onto `Dash`, a second reader of the stat.
- **Dash Yard Upgraded** holds both upgrades for every slot up to the game's capacity, not only the seated ones -
  seats are taken after a scenario is populated (`UpgradeSystem.hold` gained a slot). *Rejected:* slot zero only - a
  second player joining would play the plain yard.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *The surge as a blast* (a `Blast` that stuns). A blast is on nobody's side and fatal by G0018; one that only
stunned, and only aliens, would be a second meaning on the one circle the player has learnt reads as death.

**No:** *A surge that stuns his own side* - the troopers, or another player. See *Why aliens only*.

**No:** *A surge at the end of the dash*, or one on a dry or refused press. See *Why at the start*; and a press that
moved him nowhere is not a dash.

**No:** *Pantera making a dash faster or longer.* Distance is what keeps a dash a step out of trouble rather than a way
out of the fight (G0107); more of them, sooner, is the upgrade.

**Revisit when** a third radial non-lethal effect appears - a concussion grenade, a stun mine - which is when the
surge's shape (a reach, a side, a stun, a ring) should become a kind of its own rather than a second helper.

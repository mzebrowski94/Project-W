# G0077: The commander carries his grenades

**Status.** accepted

**Trigger.** Counting, limiting or refilling anything a man throws; a card that gives grenades; an upgrade on how
many he carries; proposing that a crate refill him by being walked to, that a trooper's throws be counted, or that a
grenade go off against a nest.

**Enforced by.** `GrenadeCountTest` - `.startsTheLevelCarryingHisMost`, `.spendsOneAThrow`,
`.throwsNothingOnceHeHasNoneLeft`, `.comesBackFullAfterHeIsKilled`, `.keepsWhatHeHasLeftWhileHeIsDown`;
`CrateTest.fillsHisGrenadesWhenHeTakesAGrenadeCacheFromIt`,
`.turnsDownAGrenadeCacheForAManCarryingHisMostAndLeavesTheCrateOpen`;
`UpgradeTest.raisesTheChoosersMostAndHandsHimTheGrenadesItAdds`; `TrooperGrenadeTest.spendsNothingWhenHeThrows`;
`ReadoutWriterTest.publishesTheGrenadesHeCarriesAndHisMost`;
`GraphicConfigTest.readsOneShadowForEverythingOffTheGround`.

**Settled with Mateusz, 2026-11-11**, from the playtest list (ticket 04): *limit the number of grenades, an indicator
on the bottom bar, grenades in crates; 3 by default, and that is the maximum* - and, in the session, *a new upgrade
raising the grenade limit for a single commander, +1, +2*.

**Amends G0019**, whose scope called unlimited grenades alpha scaffolding and a count "one more number": this is that
number. The throw itself is unchanged.

**Constraint.**

- **A commander carries his grenades as a count**, at most his *most*: `sim.grenade.most`, 3. He starts a level full
  and comes back from the reserve full (G0055), to his most as his slot's ranks make it. Going down keeps what he has.
- **A throw spends one.** With none left the throw key does nothing. The wait between throws (`sim.grenade.cooldownSeconds`,
  Grenadier) still holds on top.
- **A grenade cache** is a card in the armoury deck, dealt into crates like a weapons cache or a medkit (G0056).
  Taking it fills him to his most. While he is full it is turned down and the crate stays open - the medkit's rule
  for a man with no wound (G0053), and the crate bar dims it the same way.
- **Bandolier** - personal, two ranks, tier 1 - raises the chooser's most by one a rank (4, then 5) through the count
  stat `grenade_most`, floor 1, and hands him the grenade it adds when he takes it.
- **His grenades are shown** in his player panel as pips at the end of his gun's row: filled for each he carries,
  hollow up to his most (pillar 5), dim while he is down. Zero is a row of hollow pips and nothing else. The heat bar
  shortens for them, and OVERHEAT is cut to the room they leave.
- **A trooper's throws are not counted.** G0051 stands: the chance, the cooldown and the squad gap are their limit.
- **A thrown grenade casts the slung rider's shadow** (G0074) - one shadow for whatever is in the air, one set of
  keys, `graphic.shadow.*`.

**Why a card and not a crate walked to.** "Recharge on approaching a crate" was the ask. A crate offers nothing to
whoever walks onto it (G0056), and re-opening that to refill grenades would make walking past a crate a decision
the player cannot see the outcome of. Refilling by touch belongs to a pickup, which is what ticket 05's crate at a
taken point is; here the crate deals a card.

**Why only grenades.** The ask said "grenades and resources". Heat is a rate limit and not a supply (G0025), guns are
found and lost, wounds have the medkit. Nothing else is counted.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A crate that refills grenades on being walked to, or on being opened, with no card taken.* G0056's crate
offers nothing to a passer-by and gives one thing per opening; either version is a second, invisible grant on top.

**No:** *Counting a trooper's grenades.* G0051's *an ammunition economy*: a count nobody is shown, for a rate the
cooldown and the squad gap already limit.

**No:** *A grenade that goes off against a nest.* Raised as "grenades as the counter to nests" and withdrawn by
Mateusz in the session: a blast already kills a nest outright, and a grenade comes off one as off any scenery - so it
is thrown from its full reach, banked like any other throw. Contact detonation is G0019's refusal, unchanged.

**No:** *A tell at zero beyond the hollow pips* - a click, a flash, a toast. The panel already shows it.

**Revisit when** a second throwable exists (G0019's revisit) - whether it shares this count or carries its own is the
question then.

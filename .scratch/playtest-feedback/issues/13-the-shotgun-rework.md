# The shotgun rework

Type: grilling
Status: resolved
Blocked by: -
Section: Weapons and the hand

## Question

The **shotgun** gets a **heat** bar worth four pulls fired close together, after which it must cool all the way
down; more damage per **pellet**; and a light **kick**, with no **shake**.

## From Mateusz (translated)

> Shotgun rework: we add a heat bar covering 4 shots that can be fired in quick succession, after which the heat
> bar must fall to zero. The shotgun also gets more damage, and a slight recoil (no screen shake).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind`. G0025 (heat - Do not build *locking until empty*), G0047, G0058 (the bazooka's reload
is its heat bar; its kick), G0032, `docs/glossary.md` *shotgun* ("it stays cold; what it costs is the wait"),
*heat*, *overheat*, *kick*, the `sim.weapon.shotgun.*` block.

## Conflict to settle

"Must fall to zero" is what G0025 rules out. Either the shotgun's **overheat** release is set near zero (a
number, not a new rule), or G0025 is amended for it - decide with Mateusz and record it. The glossary row
changes either way.

## Done when

Numbers in `assets/sim/`, the glossary row, the record, `verify.sh` green. Keys and values in the final message.

## Answer

Settled as numbers on the existing heat rule, recorded as G0079 (amends G0025 and G0058): a bar whose stop
holds until it is empty (`heat.resumeBelow=0`, as the bazooka and disc gun already had) is a reload, however
many pulls fill it; the beam's row asks only of guns that do not reload. No new field, no magazine.

`sim.weapon.shotgun.*`:

- `heat.perShot=0.395`, `heat.coolPerSecond=0.35`, `heat.resumeBelow=0` - the fourth pull fired as soon as it
  is ready stops it, the third never does (also at Weapon Handling's shortest cadence); reload from full a
  little under 3 s; pulls a second or more apart never fill it. Narrow band (~0.38-0.41): retune the three together.
- `cooldownSeconds=0.5` (was 0.75) - so four pulls are close together (1.5 s for the four).
- `damage=36` (was 22, +64%) - a whole pull (216) kills a grunt as it arrives on every tier (hard 216); 30 or
  32 fell short of shipped/hard grunts. Sustained output a little above before, burst over twice.
- `recoilSpeed=14`, `recoilSeconds=0.08` (were 0) - a light kick, about half a unit a pull (disc gun ~1.2).
- No shake block (zero shake).

Glossary *shotgun* row, `WeaponKind.SHOTGUN` Javadoc, combat invariant rows and the nest comment in
`level.properties` (now four pulls, one bar) follow. Tests: `SimConfigTest.stopsTheShotgunOnTheFourthPullFiredCloseTogether`,
`.reloadsTheShotgunFromEmptyWithALightKickAndNoShake`.

# The shotgun rework

Type: grilling
Status: open
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

# A grenade count

Type: grilling
Status: resolved
Blocked by: -
Section: Grenades and crates

## Question

Give the commander's **grenade** a count - 3 to start, 3 at most - shown in his **player panel** in the
**bottom bar**; a grenade **card** in the **armoury deck** so a **crate** can deal one; the count topped up
when he comes near a crate; a shadow under a thrown grenade like a **slung** rider's; and grenades as the
answer to a **nest**. What exactly does each mean?

## From Mateusz (translated)

> Limit the number of grenades, an indicator on the UI (bottom bar), grenades in crates, grenades as the
> counter to nests.
> Automatic recovery/recharge of grenades/resources on approaching a crate. A shadow under the grenade (like
> the Slinger's throws).
> Show the number of grenades the player holds somewhere in his GUI (3 by default, and that is the maximum).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-system`. G0019 (the grenade), G0051 (*an ammunition economy* - troopers), G0056 (the
crate), pillar 3, `docs/glossary.md` *Words we do not use* ("grenades are the only thing a count is still
open for"), `docs/ui/menu_layout_agent.md` (player panel), the slinger's shadow drawing.

## Open

- "Counter to nests": more damage to a nest, a nest only a blast kills, or simply enough count to afford one?
- "Recharge on approaching a crate": every crate, once per crate, only while it holds cards? Approaching (no
  key) beside G0056's opened-by-the-action-key.
- Does the count survive his death and return (G0055)? Online: per **slot**, sized from capacity.
- Is "resources" anything besides grenades? (G0025: heat is not a supply.)

## Done when

Built, the panel shows the count, input-map row and invariants updated, `verify.sh` green.

## Settled in the grilling (2026-11-11)

Recorded as G0077 (`docs/design/decisions/G0077-the-commander-carries-his-grenades.md`); glossary rows *grenade*,
*grenade cache*, *crate*, *armoury deck*, *player panel* and the *ammo* line written.

- Count: `sim.grenade.most=3`, start full, full again on return, kept while down; none left throws nothing; troopers
  uncounted (G0051 stands).
- Counter to nests: **withdrawn** by Mateusz - nothing changes; written as a Do-not-build in G0077.
- Crates: a **grenade cache** card in the armoury deck fills him to his most, turned down while full (medkit's rule).
  No walk-over refill on a crate - that is 05's small crate.
- Resources: grenades only.
- Added in the session: **Bandolier** - personal, 2 ranks, tier 1, weight 10, `count grenade_most chooser 1`, floor 1,
  hands him the grenade it adds.
- Panel: pips beside the gun, filled / hollow up to his most; no other tell at zero.
- Shadow: one shadow for the slung rider and the grenade, keys renamed `graphic.sling.shadow*` -> `graphic.shadow.*`.

## Answer

Built (G0077). The commander carries `sim.grenade.most=3`, kept on `Thrower` as what he has spent, so a new body is
full and Bandolier's raise is in his hand at once; the most is folded in one place, `Throws.most`. A throw spends one;
none left throws nothing; troopers are uncounted. A grenade cache (`ARMOURY_GRENADES`, `playerGrant=grenades`, 3 cards)
fills him from a crate and is turned down while he is full (`GRENADES FULL`). Bandolier: personal, 2 ranks, +1 most a
rank. Pips at the right end of the gun row (dim while down); the heat bar shortens for them and OVERHEAT is cut to the
room left. One shadow, `graphic.shadow.*`, under a slung rider and a thrown grenade. Counter to nests withdrawn.
Invariant rows in `combat.md` and `pickups.md`, input-map row, alpha-phase scaffolding row removed.

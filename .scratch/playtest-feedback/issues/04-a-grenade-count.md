# A grenade count

Type: grilling
Status: open
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

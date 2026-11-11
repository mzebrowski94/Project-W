# A crate where a point was taken

Type: grilling
Status: resolved
Blocked by: 04
Section: Grenades and crates

## Question

When a **control point** is taken, a small crate appears by it that refills grenades (and whatever else 04
decided is refilled), with a simple animation of the take. What is it - a new **pickup** kind with a
**grant**, or a **crate** variant - and what is it called?

## From Mateusz (translated)

> Small crates should spawn after taking a control point (ones that refund grenades and resources) + a simple
> animation of gaining the resources.

## Added by Mateusz (2026-11-11)

> Also in "Proving Grounds" - add option to "others" to add crates.

The proving ground's `others` entry (`docs/ui/menu_layout_agent.md`, `UI:main/proving-ground`; G0060) today offers
none, 3 turrets, 3 mines, 3 barrels. Add crates to it - open: which crates (the ordinary **crate**, this ticket's new
small one, or both), how many, and where they stand.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind`. 04's answer. G0006 (claimed on contact), G0030 / G0055 (what a taken point
already earns), G0049 (a draft per point), `Grants`, `docs/glossary.md` *Growth and pickups*. The animation is
a spawned feedback entity (G0004), never an event.

## Open

The name (not *mini crate*, which collides with **crate**); worth anything to the aliens or walked over; one
per point or one per player; what it does when he is already full.

## Done when

Built as data (G0008), glossary row, `verify.sh` green.

## Settled in grilling (Mateusz, 2026-11-11)

1. **No new kind.** What a taken point lays is the **grenade cache** (`ARMOURY_GRENADES`) lying on the field - as a
   weapons cache is both a card and a pickup. Glossary row widened.
2. `alienGrant=none`: the aliens walk over it.
3. **One per seated slot** at the tick of the capture - down or **away** included, fixed from then; a tunable per
   commander (proposed `sim.point.grenadeCaches=1`).
4. A full man walks over it (`Grants.worthTo`, already); it lies there till the level ends.
5. Laid just outside the point's ring, as G0030's reinforcements are, through `Scatter`, never on the point.
6. Every point taken, a finale's that holds afterwards included.
7. **The pop**: a short feedback entity (G0004) at the taker - the taken pickup's look rising off him and fading,
   about half a second, keys in `graphic.properties` - for **every pickup the player's side takes by touch**
   (commander or trooper: grenade cache, medkit, weapons cache, supply drop). Never for the aliens' meals. Not at
   the caches' appearance. Glossary row *pop* added.
8. **Proving ground**: two new `others` choices - `3 crates` (ordinary G0056 crates) and `3 grenade caches` - in the
   band the others already use.
9. Furniture: never scaled by tier or ramp.

Touches: pillars 3 and 5; pickups invariants (worth to this taker, G0053); G0030 (visible, so not its rejected
invisible capture reward); G0056 (not a crate, which offers a walker nothing); G0060 (others list). Consistent with
all. To write at the build: a decision record (rejected - a new kind, one per point, a crate that refills by touch),
invariant rows in `pickups.md` (laid per slot at a take; pop for the player's side only).

## Answer

Built (G0078). No new kind: a taken point lays `sim.controlPoint.grenadeCaches=1` grenade cache (`ARMOURY_GRENADES`)
for every seated slot - counted by `Seats`, shared with the draft rotation - once, on the capture tick, outside every
point and off every pickup lying there, in the reinforcements' ring, on its own stream (`RngStream.CACHE`); every
point, a hold-afterwards one too. A take by touch on the player's side pops: a `Pop` entity in the taken look at the
taker, `sim.pop.seconds=0.5`, counted down by `CooldownSystem`, ended by `PickupSystem`, published on
`Appearance.pop`, drawn rising `graphic.pop.rise=3` radii and fading from `graphic.pop.alpha=0.85`. Proving ground
others gain `3 CRATES` and `3 GRENADE CACHES` (amends G0060). Invariant rows in `pickups.md`; glossary *pop*,
*grenade cache* and *setup* rows. The key is `sim.controlPoint.*`, not `sim.point.*`, to match its neighbours; the
pop's duration is a sim key because the sim owns the entity's life, only its look is in `graphic.properties`.

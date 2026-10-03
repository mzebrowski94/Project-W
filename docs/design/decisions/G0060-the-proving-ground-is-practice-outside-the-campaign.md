# G0060: The proving ground is practice, outside the campaign

**Status.** accepted

**Trigger.** Letting the player choose a gun, an upgrade or a squad anywhere but the proving ground, or
carrying anything into or out of it.

**Enforced by.** `ProvingGroundTest` (what a **setup** builds); judgement for the rest.

**Constraint.** **An exception to pillar 3, on the terms pillar 1 already grants its two.** The proving
ground (`UI:main/proving-ground`) lets the player choose a **setup** - one strain, how many or a nest of
it, the gun he spawns and comes back with, whether the squad comes, the upgrades and adaptations at their
ranks, and turrets, mines or barrels - and fight it on a round patch of sand with a few seeded rocks on it, ringed by thicket, his side
on the left and theirs on the right. Nothing is carried in or out: no tally, no upgrade, no gun leaves it,
and nothing in the campaign reads what was chosen there. It earns no drafts - it has no objectives - and
it is offline only.

The starting gun is a number every level has (`sim.commander.weapon`, the sidearm); the proving ground
sets it as a scenario sets any number, through `SimConfig`, never by editing `assets/sim/`. Chosen ranks
go into the ledger on the first tick, through `UpgradeSystem`, the ledger's one writer.

**Why.** Mateusz wants a place to try a combination, judge a change and practise against one strain. A
scenario per combination would be dozens of classes; a page of choices is one.

**Accepted cost.** A page that looks like a loadout screen exists in the game. What keeps it from becoming
one is that the campaign never sees it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Choosing a gun, an upgrade or a squad before a campaign level.* That is the loadout screen
pillar 3 rules out.

**No:** *Carrying anything out of the proving ground* - a record, an unlock, a best time. It would turn
practice into progression.

**Revisit when** the proving ground is wanted online, or a campaign level wants to start with something
other than the sidearm.

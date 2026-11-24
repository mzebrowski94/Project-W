# G0056: A crate is opened and chosen from, dealt on its first opening, by one commander at a time

**Status.** amended by G0110: a taken site leaves crates by the seated count, and no crate holds two cards of one kind; amended by G0127: Firepower stocks no flamer

**Trigger.** Anything about what a crate holds or when that is decided; opening one; the crate bar in the
top bar; an upgrade that changes what crates hold; proposing that a crate show its contents from a
distance, or that a trooper open one; anything that keeps two crates or a crate and a draft on one screen.

**Enforced by.** `CrateTest` (offered within arm's length, never taken by walking onto it, dealt on the
first opening and not before, keeps what it was dealt less what was taken, gone once empty, one opener at
a time and the lower slot on a tie, shut on a wound, on going down, out of reach, on a dropped link and on
request, a medkit refused to a man with no wound, the deck and the size both from a bag),
`UpgradeTest.stocksEveryCrateWithTheCardAStockNames`, `.fillsEveryCrateUnderFullCrates`,
`.shufflesTheArmouryDeckAfreshWhenADealStacksIt`, `CrateBarTest` (one bar a machine, a second local crate
named for shutting, only its seat works it, nothing re-shown before the world has answered),
`CrateKeysTest`, `ReadoutWriterTest.listsTheCardsOfTheCrateHeHasOpenAndWhichHeWouldBeTurnedDownFor`,
`BundleTest.carriesCrateEventsAndHandsThemToAWorld`, `SystemOrderTest`.

**Supersedes G0050** - the sealed cache, its reveal radius and its DO NOT BUILD *a gun dealt when the cache
is taken*, because what G0050 feared - walking over a crate without
knowing whether you want it - cannot happen when nothing is taken by walking over it. **Amends G0021 and
pillar 1**: the action key still decides what it does from what is in reach, and opening a crate is one
more thing on that list; what he takes from it is a choice, in the top bar, and is the second named
exception to pillar 1 beside the upgrade draft (G0049). **Amends G0038** for the leash: see *Beside it*.

**Constraint.**

- A **crate** lies on the field and offers nothing to whoever walks onto it. A commander on his feet opens
  one within arm's length with the action key - nearest first, a tie going to the gun, then the crate,
  then the casualty.
- **What it holds is dealt the first time anybody opens it, never before**: how many from a bag over
  `sim.crate.fewest`..`sim.crate.most` (two or three), which from the level's armoury deck - every gun's
  cache and the medkit are cards in it - a bag of its own. Until then the world holds nothing about its
  contents: nothing to lay out, nothing in a snapshot, and an upgrade taken on the way there counts.
- **Once dealt it keeps what it holds.** What is taken is gone from it; whoever opens it next sees the
  rest. It is gone from the field the tick it is empty. At most four, the cards a top bar shows.
- **One commander has a crate open at a time**, a field on the crate naming his slot. Every machine steps
  the same presses in the same order, so every machine agrees who got there first without asking anybody;
  two presses on one tick go to the lower slot.
- **He takes one thing per opening**, and taking it shuts the crate. A medkit is turned down for a man with
  no wound and leaves the crate open (G0053).
- **It shuts on him** the tick he takes a wound or goes down, steps out of arm's length, drops his link, or
  shuts it himself. While he has one open the action key offers him nothing else.
- **One crate or one draft in a machine's top bar at a time.** A second local seat's press on a crate is not
  sent while the bar is taken, and a crate that opens anyway - two presses in one frame - is shut. Seats
  on other machines open their own crates freely. Opening one pauses a solo game as a draft does (G0049).
- **Upgrades**: Full Crates raises the fewest a crate is dealt to the four it can hold; a stock puts its card
  into every crate dealt after the pick, swapped for what the deck dealt from the last card back. (Firepower's
  flamer was the one stock; G0127 took it off.)

**Beside it.** **The leash holds together the commanders at one machine,
and nobody else.** A slot's machine arrives with its join event and is carried by the snapshot, and only
the leash reads it: players sharing a screen share a camera that must frame them, while a player at a
machine of his own has a camera of his own. G0038's "which device drives it is not the simulation's
business" still holds for everything else.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Dealing a crate's contents when the level is laid.* Every crate's contents would be state on the
field from the first tick - in every snapshot, for crates nobody reaches - and an upgrade taken on the way
could not change what the next crate holds.

**No:** *Re-dealing a crate that has been opened.* A crate that changes between openings is a slot machine;
what one player left is what the next finds.

**No:** *Letting two commanders share an open crate, or a crate bar showing somebody else's crate.* Two
choosers over one set of cards is a race for the card, and the first to press wins by latency.

**No:** *A second crate bar, or a crate beside a draft, on one screen.* The top bar is one strip; two
choices at once is a menu again (pillar 1).

**Revisit when** a trooper should fetch from a crate, or a crate should hold something other than a gun's
cache or a medkit - the deck is any pickup with cards, so the second needs only a number.

## Amended by G0127

**No stock.** Firepower no longer puts a flamer in every crate: a crate opened under it is dealt as any other, the
flamer at the armoury deck's baseline (`UpgradeTest.dealsEveryCrateUnderFirepowerAsWithoutIt`). The `stock` effect
form stays, with no shipped user, enforced by `UpgradeTest.stocksEveryCrateWithTheCardAStockNames`.

# G0049: An upgrade is earned on the field and drafted in turn

**Trigger.** Anything that grants the player's side an upgrade, decides what earns one, who chooses
it, how long it lasts or whether choosing pauses; a reroll, a shop, a timer on a choice; proposing
that upgrades carry between levels.

**Enforced by.** `DraftTest` - a point taken earns a draft for slot 0 in single-player; drafts go
round the present slots in order; a pick applies one rank from the next tick; a pick for a draft that
is gone, from the wrong slot or of a kind not offered changes nothing; drafts arrive at the configured
rate per source; a commander who respawns keeps his slot's personal ranks. `DraftPauseTest` for the
solo pause, `DraftBarTest` for one draft per machine's top bar.

**Amends pillar 1** with one named exception: choosing an upgrade makes the player stop and weigh. He
picks the moment, it shows three cards at most with exact numbers, and it pauses only a solo game.
**Clarifies pillar 3**, without weakening it: upgrades are earned on the field, never bought, and
last the level.

**Constraint.**

- **Objectives earn drafts, and nothing else does.** Capturing a control point, clearing a hold wave
  and killing a boss, each at a configured rate: the first of each source earns at once, then every
  N-th (`sim.upgrade.draft.pointsPerDraft`, `.wavesPerDraft`, `.bossesPerDraft`; zero switches a
  source off). Destroying a nest does not.
- **A draft goes round the players.** Each one goes to the next present slot after the last chooser,
  in ascending slot order - keyed by slot and sized from capacity (pillar 7). Its cards are dealt from
  a stream of their own when it is earned, so what he is offered does not depend on when he looks.
- **Each effect names whom it reaches**: the squad, the commanders, the chooser's commander, the team
  or the side. An upgrade whose every effect reaches the chooser is **personal**, and its ranks
  belong to his slot - so co-op commanders grow distinct kits.
- **A pick is an input event** naming the draft and the upgrade, never a card's position, so a card
  re-dealt between the press and the tick cannot grant something he never saw. It is validated by
  the simulation on every machine alike, and takes effect from the next tick.
- **Nothing waits.** A draft is held until its chooser takes a card; it never times out and nothing
  picks for him. If his slot goes away (G0052), his pending drafts pass to the next present slot.
- **Opening a draft pauses only a solo game** - one present player. With two or more, local or
  online, the game runs on, and the chooser keeps moving and shooting; only his squad-order inputs
  are borrowed by the draft while it is open.
- **One draft per machine's top bar.** While it is open the whole top bar is the draft; the bottom
  bar and every panel in it stay. A second local player who presses the key is told whose draft it
  is.
- **An upgrade lasts the level.** It belongs to the slot or the side, never to a life: a commander
  who respawns keeps what his slot chose - the opposite of G0044's tally, and stated so nobody
  "fixes" it.

**Scope.** The player's side. The aliens' adaptations share the catalogue and the ledger (ADR 0017)
but have no chooser and no draft. Guns from crates are pickups, not upgrades (G0050). Carrying
upgrades between levels is open, blocked on the level lifecycle.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A currency, a shop or a paid reroll.* Pillar 3, and G0044: a tally is shown, never spent. A
reroll, if one is ever wanted, is an input event limited by a count.

**No:** *A draft that times out, or picks for him.* A choice made for the player is one he did not
make, and a timer turns a choice into the stop-and-compute pillar 1 is against.

**No:** *Pausing a game with two players present.* One player's choice would stop everybody else's
fight.

**No:** *A draft earned from a tally or a kill count.* That turns the tally into a currency and
rewards farming over the level's objectives.

**No:** *A fallback that grants men.* G0030 rules out a reinforcement granted by a capture itself;
an empty pool earns nothing and says so.

**No:** *An effect that hands out a gun.* A gun is found and lost, never owned.

**Revisit when** the level lifecycle is decided (carrying upgrades on), or playtests show the draft
cadence or the solo pause intruding on the fight.

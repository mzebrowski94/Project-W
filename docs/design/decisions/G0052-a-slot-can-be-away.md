# G0052: A slot can be away

**Status.** amended by G0118: an away slot keeps his drafts

**Trigger.** Anything that needs to know whether a player is still at the game - the draft rotation,
the HUD's OFFLINE label, a pause that should end; a dropped link or a returning machine; counting
"solo".

**Enforced by.** `DraftTest.keepsTheDraftsOfAnAwaySlotAndGoesOnDealingHim` - an away slot keeps his drafts
(G0118). `SessionTest.keepsADroppedPlayersDraftsAndTakesHimBackWhenSomebodySitsInHisSlot` - a dropped link is
sealed as away on every machine and his slot is dealt its own all the same; somebody seated in the slot again is
sealed as back. `SessionTest.liftsADraftPauseWhenASecondPlayerSitsDown` - a solo draft's pause
ends when a second player sits down. `SessionTest.keepsAMachineWhoseGameStoppedAndTakesItBackWhenItRunsAgain`
and `SessionTest.restartsWithAStoppedMachinesCommanderAway` - a dormant machine's slots are away, in a
restarted world too, and back when it wakes.

**Amends G0038**: it replaces "a dropped connection is an unplugged pad; the commanders stay
standing and read as idle". That stays true, and now the slot is also marked **away** until it
returns.

**Constraint.**

- **Presence is input.** *Away* and *back* are input events on a slot, applied by `Simulation.step`
  after joins; `Presence`, on the level entity, holds the slots that are away. Every machine applies
  them on the same tick, so presence is part of the same game everywhere (pillar 7).
- **Online, the sequencer seals them**: *away* in the next bundle when a peer's link drops or the
  peer goes dormant, *back* when a returning machine is re-seated or a dormant one wakes. A peer never
  sends its own.
- **A stopped machine is kept, not let go** (2026-10-01). A peer whose game stops - its window held by
  its title bar - goes on answering its link; once it is sent a world and has not built it before the
  history moves past it, it goes dormant and its slots away, rather than being disconnected. It costs
  nobody else anything, and comes back by itself. A machine is let go only for hurting the others.
- **Offline, nothing sends them.** Unplugging a pad pauses the game and is not leaving (G0028).
- **Away changes little.** The commander stays standing and idle, exactly as G0038 says. The draft
  rotation skips the slot and its pending drafts pass to the next present slot, and the HUD shows
  OFFLINE - the source the UI spec's "remote disconnection is not published yet" was missing.
- **"Solo" means one present player**, which is what decides whether opening a draft pauses (G0049).

**Scope.** Slots whose machine's link dropped or whose machine went dormant. It says nothing about a
player who stops pressing anything - he is present - and nothing about removing a commander, which still
never happens.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Each machine deciding for itself who is away.* Presence decides who gets a draft, so a
machine that guessed differently would deal a different game - a desync.

**No:** *Removing the commander of an away slot.* G0038's reason holds: a machine that comes back is
put back in the slots it left.

**Revisit when** a slot away for a long time should hand on more than its drafts - its personal ranks,
its place in the leash.

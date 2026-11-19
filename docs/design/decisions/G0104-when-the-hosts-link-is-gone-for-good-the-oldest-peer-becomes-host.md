# G0104: When the host's link is gone for good, the oldest peer in the room becomes host

**Status.** accepted

**Trigger.** What happens to a session with a room when its host's machine is gone; who may start a mission, restart
or turn on invulnerability; which peer the others are checked against and sent a snapshot by; anything that says "the
host" and means a machine.

**Enforced by.** Nothing yet - not built; the tests are named here when it is (`.scratch/online-relay/spec.md`, stage 8).

**Provisional, for review (AFK run).** Decided by the orchestrator while Mateusz was away; each choice below names the
alternative turned down. Nothing in it breaks a pillar.

**Amends G0038** - its "until then the host leaving ends the session" holds only for a session without a room.

**Constraint.**

- **A session with a room outlives its host's machine.** The sequencer is on the relay (ADR 0023), so nothing sealed
  moves. A session without a room still ends with its host: its sequencer goes with him.
- **A host who ends it ends it.** His Back from the briefing, through the leave confirm (G0101), closes the room for
  everybody, as now. Only a host whose link is gone - closed window, crash, cable - hands over. *Alternative: any
  host leaving hands over.* Turned down: G0101 has the host's Back end the session, and a host who meant to end it
  would find his friends playing on without him.
- **The hand-over comes when the window ends**, not when the link drops: the dropped host has the window of G0105
  to come back as host, and nothing host-only happens during it (no start, no restart). *Alternative: at once.* Turned
  down: a blip would move the host to somebody who then has to give it back, or the returning host would find himself
  a peer for nothing.
- **The new host is the peer that has been in the room longest** among those still linked - the lowest peer number
  the relay handed out. The sequencer decides, and seals it as an event in the tick stream, so every machine learns it
  at one tick. *Alternatives: the best link; a vote; the lowest slot.* Turned down: the best link flaps and needs
  measuring; a vote is a screen nobody wants mid-fight (pillar 1); a lowest slot is whoever happened to drop in first
  after a seat was freed, which is not who has been there.
- **What moves to the new host**: the right to start a mission and to restart or turn on invulnerability (G0038), the
  briefing's choices (G0101), and the world every other peer is checked against and the snapshot a joiner or a
  returning machine is sent from. His world is the same world as the old host's was - lockstep - so nothing is copied.
- **What stays**: the room, its code and its password (G0103); the session config, which the relay holds from the
  moment the room opened (ADR 0018, 0023); the last START's setup, which the relay holds as bytes to welcome a joiner
  with. A new host's own config is the one he was sent, so a mission he starts is the same game.
- **Slot zero and the old host's commanders.** Nothing is special about slot zero after a hand-over: the old host's
  slots are away (G0052) and his commanders stand idle, as for any dropped machine (G0038). Slot zero is not given
  to the new host; he plays the slots he has.
- **Everybody is told**: a banner on every machine, `P3 IS THE HOST` in the commander's colour, and the briefing and
  the pause page read out the new host. Pillar 5: nothing about who may start a mission is a number to track.
- **The old host coming back** is a peer (G0105), into the slots he left, and does not take the host back.
- **The last peer leaving closes the room**, host or not.

**Why.** The relay holds the sequencer, so a host leaving costs only the powers that were his. Choosing the longest
standing peer needs no measurement and no vote, and the sequencer already knows the order machines arrived in.

**Scope.** A session with a room whose host's link is gone. Not how a dropped machine proves who it is (G0105), not
where the sequencer runs (ADR 0023), not a session on one network.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A vote, or a host chosen on a screen.* Pillar 1, and a screen between the player and the fight.

**No:** *The best link becoming host.* A choice that flaps with the link and needs a probe the game does not have.

**No:** *The returning host taking the session back.* Two hosts in one tick's history, and a banner every blip.

**No:** *A new host copying the old host's world or a sealed history.* The world is the same on every peer and the
relay holds the history.

**Revisit when** a group has lost its host mid-mission: whether the longest standing peer is the one they would have
picked, and whether nothing host-only during the window reads as a stall.

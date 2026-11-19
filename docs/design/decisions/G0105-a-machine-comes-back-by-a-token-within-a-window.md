# G0105: A machine whose link dropped comes back by a token the relay gave it, within a window, and is put back in its slots

**Status.** accepted

**Trigger.** Reconnecting after a dropped link; how a machine proves it is the one that left; how long a place is kept;
whether a crashed game counts as coming back; what the room's password asks again.

**Enforced by.** Nothing yet - not built; the tests are named here when it is (`.scratch/online-relay/spec.md`, stage 7).

**Provisional, for review (AFK run).** Decided by the orchestrator while Mateusz was away; each choice names the
alternative turned down. Nothing in it breaks a pillar.

**Amends G0038**, whose "a machine that comes back is put back in the slots it left" this makes concrete.

**Constraint.**

- **Identity is a token.** When the sequencer welcomes a machine it hands it sixteen random bytes, once, over the
  link that welcomed it. The machine keeps them **in memory** and shows them to nobody; the sequencer never
  sends one to another peer. The machine id (`MachineId`) names a machine to the others and is no proof: it is
  public. A link that shows a token the sequencer holds is that machine. *Alternative: the machine id as proof.*
  Turned down: other peers hear it.
- **A crash is not coming back.** A game that was closed and started again has no token: it joins as a new machine,
  into a free slot, and gives the password again. *Alternative: the token kept in preferences.* Turned down: two
  copies on one computer share their preferences (G0067), a token written to disk goes stale, and a restarted game
  has lost its world anyway - it would come back to a snapshot as a joiner does.
- **The window is `net.rejoin.windowSeconds` (30)**, kept by the sequencer. Within it the seats are kept: the
  commanders stand and read as idle, and the slots are away (G0052). When it ends the slots count as free: a machine
  that joins takes the lowest free slot and finds its commander standing, as a pad plugged in does (G0028), and a
  returning token finds nothing and is turned away as a stranger, told to join again.
- **The password is not asked again**; the token is the proof. The room's door never runs for a token the sequencer
  holds (G0103).
- **What crosses on return is a snapshot**, by the drop-in path (ADR 0012), from the host's peer, with the same load and
  catch-up bounds as a join. The machine's own world is thrown away: it is stale by however long it was gone. It is
  put back in the slots it left, not the lowest free ones, on the tick the sequencer seals the return.
- **Who notices**: the sequencer, which is on the relay for a session with a room and on the host's machine for one
  without. It is the same sequencer, so a session on one network gets the same window and the same token.
  The client's side is a retry for the window's length, and the connecting state it shows; that is not an event in
  the simulation.
- **A second link with a held token replaces the first**: the old one is closed. One machine, one link.
- **The host coming back** after a hand-over (G0104) is a peer.
- **A machine that left on purpose has no window**: its seats are freed at once, as the leave it is.

**Why.** A dropped link is almost always followed by it coming back (G0038), and a token only the machine and the
sequencer know is the smallest thing that cannot be copied from what the other peers can see.

**Scope.** A dropped link and the machine that comes back through it. Not a game that was restarted, not who is host.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A token kept on disk.* Shared preferences on one machine, stale tokens, and a world it has lost anyway.

**No:** *Asking the password again to come back.* The token is the proof; a password typed on a pad mid-fight is a
screen in the fight.

**No:** *Holding a machine's slots until the session ends.* A host who walks away would hold them for good.

**No:** *The machine id as the proof of identity.* Every peer hears it.

**Revisit when** players come back after minutes, not seconds (a longer window), or a crashed game is asked to come
back (a token kept across runs needs a story for the two-copies case first).

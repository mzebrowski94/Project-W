# G0038: An online player is a slot the host hands out, and joining over the network is plugging a pad in

**Status.** amended by G0052: a dropped link also marks the slot away; amended by G0056: the leash holds together only the commanders at one machine; amended by G0101: a hosted session opens on a briefing, and the host starts its first tick; amended by G0103: a session reached over the internet is a room on the relay; amended by G0104: a session with a room outlives a host whose link is gone; amended by G0105: a machine comes back by a token within a window

**Trigger.** Anything that seats, identifies or counts players across machines; a host or join screen;
the player capacity; anything that assumes a machine has one player on it, or that a game has four.

**Enforced by.** `CoopTest.seatsAndHearsEveryPlayerOfAGameLargerThanTheCeiling` - nothing is sized by
eight; `.putsAPlayerOnTheFieldOnTheTickHisJoinArrives`, `.leavesASlotThatAlreadyHasACommanderAloneWhenAJoinArrivesForIt`
- joining is an input event. `SessionTest.dropsAPlayerIntoAGameInProgress` and
`.holdsEightPlayersFromFourMachines` - drop-in over the network, and slots handed out by the sequencer;
`OnlineSessionTest` - a joining machine plays a slot of its own and frames only its own players.
`JoinChoicesTest` - a typed room code joins the session heard with it, anything else is an address, and a
session of another build is refused before connecting; `LanDiscoveryTest`, `InvitationTest` - a host is
heard once however often it announces, and forgotten when it goes quiet.

**Amends G0028.** Everything it says about a pad stands - joining is plugging one in, unplugging is not
leaving, a slot is a number and never a reference, no lobby. Two things change: *slot zero is the
keyboard* becomes **seat zero is the keyboard**, and *four players* becomes a ceiling of eight, of which
any one machine seats four.

**Constraint.**

- **Three words, never mixed.** A **peer** is a machine in the session; the **host** is the peer that
  started it - its machine runs the **sequencer** and its config is the session's (ADR 0012). A **seat**
  is a device's place on one machine: the keyboard's is zero and a pad takes the lowest free one. A
  **slot** is a player in the simulation. The simulation sees slots and nothing else; the host hands them
  out, and the map from a peer's seat to a slot lives in the session. Offline a seat and a slot are the
  same number. **Slot zero is the host's keyboard**, held for it: it is the commander a scenario starts
  with, so a joiner whose hello arrives first still takes the lowest free slot above it.
- **Capacity is a ceiling, and nothing is sized by a literal.** `Players.MAX` is eight and bounds the
  simulation and the wire; a machine seats at most four - one keyboard and three pads. Every array, loop
  and message is sized from a capacity value handed to it, and the tests run at a capacity other than
  eight to prove it. Raising the ceiling is one constant and a colour per new player.
- **Joining over the network is drop-in.** The host is playing from the first tick. Another machine enters
  a code or an address and its keyboard appears mid-fight, on whoever is still standing, exactly as a pad
  does - `Simulation.joinCommander` is the one place that answers where. Every pad on that machine takes a
  slot the same way, then or later. The only new screen is Join; hosting is choosing a scenario, and the
  host's overlay reads out the room code and addresses to give a friend.
- **A session is found by a room code or an address.** A host announces itself once a second to every
  network interface's broadcast address - Hamachi's included, which 255.255.255.255 never reaches - and a
  joiner lists what it hears. A room code is four characters with nothing that reads as anything else (no
  0/O, 1/I/L, 2/Z, 5/S, 8/B); typed on the join screen it joins the session heard with it. It names a
  session and grants nothing. An address reaches a host the network cannot hear.
- **The world a joiner needs arrives as a snapshot** (ADR 0012), and then he is in the input stream like
  everyone else. He appears on the tick the host seats him, not the tick his machine finished loading.
- **A dropped connection is an unplugged pad.** The seats are kept, the commanders stay standing and read
  as idle, and a machine that comes back is put back in the slots it left.
- **Any player may pause the session; only the host restarts it or turns on the harness's
  invulnerability.** A pause is asked for by whoever presses the key and applied by the sequencer at a
  tick (ADR 0012). Single-stepping is offline only.
- **A full game turns away the surplus, not the machine.** A peer with more devices than free slots gets
  one per free slot, lowest seat first, and the rest are turned away as a fifth pad is.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**Reversed by G0101:** *A lobby, a ready screen or a start button on the host.* A hosted session opens on a briefing and
the host starts its first tick; a ready button and a kick stay rejected there, and drop-in is unchanged after it.

**No:** *A joining player choosing his slot, colour or character.* A slot is where he was put, not a thing
he owns; a choice is a screen, and pillar 3 has no screens between the player and the fight.

**No:** *A `Peer` or `Player` component in the simulation.* A peer is a session concept - the simulation
must step identically whichever machine a slot's input came from.

**No:** *Capacity as a key in `game.properties`.* A number every peer must hold identically is a refused
handshake waiting to happen, and a key that could say twelve with seven colours configured is the config
key that lies `Players` already warns about.

**No:** *Removing a commander when his machine disconnects.* G0028's reason: a dropped link is
indistinguishable from a blip and is almost always followed by it coming back.

**No:** *A squad per machine.* G0028's reason.

**Reversed by G0056:** *A leash per machine, or none between machines.* Players at a machine of their own
have a camera of their own; only players sharing a screen are held together.

Not rejected, deferred: the host choosing a capacity below the ceiling for one session; a new host taking
over when the host leaves; reconnecting after a dropped link, with the relay. Until then the host leaving
ends the session: every other machine is told why and goes back to the menu - as does a machine whose link
drops, or that the host refuses.

**Revisit when** eight players have actually sat down with it: whether one shared squad still reads as
co-operation with eight people shouting at it, and whether eight colours are enough to find yourself.

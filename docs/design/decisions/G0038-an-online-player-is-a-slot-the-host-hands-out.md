# G0038: An online player is a slot the host hands out, and joining over the network is plugging a pad in

**Trigger.** Anything that seats, identifies or counts players across machines; a host or join screen;
the player capacity; anything that assumes a machine has one player on it, or that a game has four.

**Enforced by.** `CoopTest.seatsAndHearsEveryPlayerOfAGameLargerThanTheCeiling` - nothing is sized by
eight; `.putsAPlayerOnTheFieldOnTheTickHisJoinArrives`, `.leavesASlotThatAlreadyHasACommanderAloneWhenAJoinArrivesForIt`
- joining is an input event. The network drop-in tests arrive with the join screen (stage 6 of the online
plan).

**Amends G0028.** Everything it says about a pad stands - joining is plugging one in, unplugging is not
leaving, a slot is a number and never a reference, no lobby. Two things change: *slot zero is the
keyboard* becomes **seat zero is the keyboard**, and *four players* becomes a ceiling of eight, of which
any one machine seats four.

**Constraint.**

- **Three words, never mixed.** A **peer** is a machine in the session; the **host** is the peer that
  started it - its machine runs the **sequencer** and its config is the session's (ADR 0012). A **seat** is a device's place on one machine: the keyboard's
  is zero and a pad takes the lowest free one. A **slot** is a player in the simulation. The simulation
  sees slots and nothing else; the host hands them out, and the map from a peer's seat to a slot lives in
  the session. Offline a seat and a slot are the same number, which is why nobody had to tell them apart.
- **Capacity is a ceiling, and nothing is sized by a literal.** `Players.MAX` is eight and bounds the
  simulation and the wire; a machine seats at most four - one keyboard and three pads. Every array, loop
  and message is sized from a capacity value handed to it, and the tests run at a capacity other than
  eight to prove it. Raising the ceiling is one constant and a colour per new player.
- **Joining over the network is drop-in.** The host is playing from the first tick. Another machine enters
  a code or an address and its keyboard appears mid-fight, on whoever is still standing, exactly as a pad
  does - `Simulation.joinCommander` is still the one place that answers where. Every pad plugged into
  that machine takes a slot the same way, then or later. The only new screen is Host and Join.
- **The world a joiner needs arrives as a snapshot** (ADR 0012), and then he is in the input stream like
  everyone else. He appears on the tick the host seats him, not on the tick his machine finished loading.
- **A dropped connection is an unplugged pad.** The seats are kept, the commanders stay standing and read
  as idle, and a machine that comes back is put back in the slots it left.
- **Any player may pause the session; only the host restarts it or turns on the harness's
  invulnerability.** A pause is asked for by whoever presses the key and applied by the sequencer at a
  tick, like every other session command (ADR 0012). Single-stepping is offline only.
- **The leash is one leash round every commander in the session** (pillar 4). Each machine's camera
  frames its own players inside it; nobody's camera frames all eight.
- **A full game turns away the surplus, not the machine.** A peer with more devices than there are free
  slots gets one per free slot, lowest seat first, and the rest are turned away as a fifth pad is today.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A lobby, a ready screen or a start button on the host.* G0028's reason holds unchanged, and drop-in
already answers what a lobby is for: nobody has to wait for anybody.

**No:** *A joining player choosing his slot, colour or character.* A slot is where he was put, not a thing
he owns; a choice is a screen, and pillar 3 has no screens between the player and the fight.

**No:** *A `Peer` or `Player` component in the simulation.* A peer is a session concept - the simulation
must step identically whichever machine a slot's input came from.

**No:** *Capacity as a key in `game.properties`.* A number every peer must hold identically is a refused
handshake waiting to happen, and a key that could say twelve with seven colours configured is the config
key that lies `Players` already warns about.

**No:** *Removing a commander when his machine disconnects.* G0028's reason, unchanged: a dropped link is
indistinguishable from a blip and is almost always followed by it coming back.

**No:** *A squad per machine.* G0028's reason, unchanged.

**No:** *A leash per machine, or none online.* Two machines' players walking off in different directions
is a second front, which pillar 4 rules out.

Two things are deferred rather than rejected: the host choosing a capacity below the ceiling for one
session, and a new host taking over when the host leaves. Until then the host leaving ends the session.

**Revisit when** eight players have actually sat down with it. Worth watching: whether one shared squad
still reads as co-operation with eight people shouting at it; whether the leash, drawn round the middle
of every commander, still makes sense when four of them are watching a different screen; and whether
eight colours are enough to find yourself.

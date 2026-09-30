# 0018: A config key has one scope

**Status.** accepted

**Trigger.** Adding a key to `network.properties` or `assets/sim/*.properties`; anything a peer reads from
its own files during a session; changing what a welcome carries.

**Enforced by.** `SessionTest.comparesAJoinerAtTheHostsChecksumTicksWhateverItsOwnFileSays` - a joiner
whose own file says another `checksumTicks` is still compared at the host's ticks and caught when it
diverges. `WireTest.readsBackTheSessionConfigAWelcomeCarries` - the session config a welcome carries reads
back whole. `ScenarioWorldsTest.shipsTheTextTheLoadedConfigWasBoundFrom`. Which scope a new key belongs
to is caught by reading.

**Constraint.** **Amends ADR 0012**, whose "Peers run the same thing" bullet said only that the host ships
its config. Every key has exactly one of three scopes (glossary):

- **World config** decides what a tick does - `assets/sim/*.properties`, bound to `SimConfig`. Identical on
  every peer or the worlds part, so the host ships it: the text its `SimConfig` was bound from, from the
  one read at start (`SimConfigLoader.loaded`), comments left out - never a later read of a file edited
  since. Each peer parses it with its own loader, which the build id says is the same.
- **Session config** is what more than one peer must agree on about how the session runs rather than what
  happens in it - `NetConfig.Session`, keyed `net.session.*`. The host's holds for everyone: the sequencer
  writes it into each welcome, and a joiner plays by that, never by its own file. Not part of a world, so
  not in a snapshot or a dump.
- **Local config** is everything else - `graphic.properties`, `controls.properties` and the rest of
  `network.properties`, pacing (`net.pacing.*`) among it. Read by one instance for itself, never sent. Two
  peers may differ in it and play the same game.

A key read only where the sequencer runs (`historyTicks`, `redundantTicks`) is the host's local config:
nobody else reads it, so nothing has to agree. A limit a joiner needs before anything from the host has
arrived cannot be session config - the largest reliable message is a constant of the wire format,
`Transport.MAX_MESSAGE_BYTES`, like the protocol version.

**Why.** A key read from each peer's own file during a session is a silent disagreement waiting for a
friend with an old copy. `checksumTicks` was one: a joiner with another value was compared at ticks the
host never checksummed, so its desync went unseen. Naming the scope makes "should this be sent?" a
question with one answer per key, asked when the key is added rather than found in a dump.

**Scope.** What a config key means across machines. Not how a file is read or bound - issue 03's front
door. Not the build id or the protocol version, which are refused rather than shipped.

**DO NOT BUILD**

**No:** *Sending every file to every peer.* Graphics and controls are a player's own, and a pacing lead
fitted to the host's link is wrong for a joiner's.

**No:** *Session config as text parsed by the joiner's loader.* It is a few numbers the welcome can carry
as numbers, checked on the way in like every other field.

**No:** *Requiring session config to match at the handshake.* It is tuning, and ADR 0012 already rejects
refusing a friend over a file.

**Revisit when** a second machine-to-machine limit turns up that a joiner needs before the welcome, or a
third local key turns up in `assets/sim/*.properties` (`docs/backlog.md`).

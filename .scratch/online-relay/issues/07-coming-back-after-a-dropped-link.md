# Coming back after a dropped link

Type: grilling
Status: resolved
Blocked by: 04, 05

## Question

G0038 says "a machine that comes back is put back in the slots it left", and deferred reconnecting "with the
relay". How does a machine prove it is the one that left, and what does coming back look like?

- Identity: a token the server or the host hands a machine when it joins (stored where, for how long), or a
  `MachineId` (read `net/.../session/MachineId.java`). Must a reconnecting machine give the room's password
  again (ticket 04)?
- The window: how long a dropped machine's place is kept before its slots count as free. Its commanders stay
  standing meanwhile (G0038).
- What crosses on return: a fresh snapshot by the drop-in path (ADR 0012), with the same load and catch-up
  bounds as a join.
- Who notices the drop and who restores the seats: the sequencer, which runs on the relay for a session with a room
  ("Where the sequencer runs", ADR 0023 draft) - and on the host's machine for one without.
- Whether a game that crashed and was restarted counts as coming back, or only a link that blipped.
- The host coming back after a migration (ticket 06): is he a host again, or a peer?

Amends G0038. Draft the words.

## Answer

**Provisional, for review (AFK run, 2026-11-19).** Written as [G0105](../../../docs/design/decisions/G0105-a-machine-comes-back-by-a-token-within-a-window.md).
Sixteen random bytes handed over the welcoming link, kept in memory only; a restarted game is a new machine; the window is
`net.rejoin.windowSeconds` (30); no password again; a snapshot by the drop-in path and the slots it left; the sequencer notices (so a LAN
session has it too); a second link with a token replaces the first; a returning host is a peer. Turned down: the machine id as proof; a token
on disk (two copies share preferences, G0067); holding slots until the session ends.

# A new host when the host leaves

Type: grilling
Status: open
Blocked by: 05

## Question

G0038 deferred "a new host taking over when the host leaves". Today the host leaving ends the session for
everyone. With a server, how does the session carry on?

- Who becomes host: the oldest peer, the one with the best link, or the lowest slot? Who decides: the
  server, or the sequencer if it lives there?
- What "host" still means once he is not the sequencer's machine: the session config's owner (ADR 0018), the
  reference world for the desync check (ADR 0012), the one who may restart or turn on invulnerability
  (G0038), the source of a drop-in snapshot. Which of these move to the new host, and which go to the server?
- Slot zero is "the host's keyboard, held for it" (G0038). What happens to it, and to the old host's
  commanders (G0038: a dropped link is an unplugged pad and the commanders stay)?
- ~~If the sequencer stays on a peer, how the sealed history and the pending events move.~~ Moot: "Where the sequencer
  runs" put it on the relay for a session with a room (ADR 0023 draft), so nothing sealed moves; a session without a
  room still ends with its host.
- What every machine is told and shown when the host changes, and what the old host sees if he comes back
  (ticket 07).

Amends G0038. Draft the words.

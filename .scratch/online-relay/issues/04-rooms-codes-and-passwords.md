# Rooms, codes and passwords

Type: grilling
Status: open

## Question

What a **room** is, in words that sit beside G0038's peer / host / seat / slot and the glossary's
**room code**, and how a player gets into one.

- Is a *room* the server's record of one session, or the session itself? Does it outlive its host (host
  migration is in scope)? Is the server called the *relay* whatever the topology ticket decides? Settle
  the words and write them to `docs/glossary.md`.
- Who issues a room code: the server, which knows every code in use, or the host, as `RoomCode.of(session)`
  does on the LAN today? Can a LAN code and an online code ever collide on one Join screen, and which wins?
- The password: who sets it (the host, when hosting) and when (only at the start, or changeable later), what
  characters it allows and how long it may be, how the server stores it (hashed, never logged), what a wrong
  one tells the joiner, and whether a machine that reconnects must give it again.
- Settled already (map Notes): reached by code only, with no list; the password is separate from the code;
  the code grants nothing. This amends G0038 - draft the amendment's words.
- What the server refuses before it forwards anything: a wrong password, a full room, a room that does not
  exist, a build that does not match. What each refusal says (G0038: the machine is told why and goes back
  to the menu).

Read: `docs/design/decisions/G0038-*.md`, `net/.../lan/RoomCode.java`, `docs/glossary.md` (the online rows),
`core/.../client/play/OnlineSession.java`'s refusal handling.

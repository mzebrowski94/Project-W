# Rooms, codes and passwords

Type: grilling
Status: resolved

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

## Answer

Grilled with Mateusz, 2026-11-19. Every recommendation accepted. Glossary rows written: **room**, **relay**,
**password** *(of a room)*, the **room code** row amended, and *server* for the relay added to the avoid-list.

**Words.** A **room** is the relay's record of one session - its code, its password if it has one, the build
it runs and its links. It lasts as long as the session (so it outlives the host exactly when the session does,
which is "A new host when the host leaves"'s to settle) and closes when the last peer leaves. The **session**
stays the game. The server is the **relay** whatever "Where the sequencer runs" picks: never a peer, no seats,
no world. If the sequencer moves onto it, the **host** row changes, not the relay's name.

**Codes.** The relay hands out the code when a host opens a room, drawn at random from the free ones, with no
quarantine for recently closed codes (a stale code lands on a new room at odds of live rooms / 390k, and the
password still stands). A session with a room has one code: its LAN announcement carries the relay's. On the
Join screen a code heard on the network wins, otherwise it goes to the relay. Still four characters online;
keeping strangers out is the password's job, and throttling code guesses goes to abuse limits.

**Opening a room.** The host's choice; hosting without asking stays LAN-only with `RoomCode.of(session)`. A host
who asks waits for the relay's code before the first tick; if the relay cannot be reached or refuses, hosting
fails and says why - no quiet fallback to LAN. How the Host page offers it is the client side's.

**Password.** Four characters of the room-code alphabet, read the same forgiving way (case, spaces, dashes),
entered like the code on keyboard or pad, shown beside the code. The host types it when hosting, blank is none,
remembered in local preferences; fixed for the room's life, kept through a change of host. Asked only at the
relay's door - a LAN join skips it. Asked once per machine: coming back is proven by whatever "Coming back
after a dropped link" settles, never by retyping it. Held in the relay's memory only, never on disk, never
logged (`NetLog` writes `password=yes|no`), not hashed, sent in the clear - a game password, not a credential.

**Refusals**, before the relay forwards anything, as `refused by the relay: <reason>`, then back to the menu:
`no room ACDE` / `room ACDE has a password` / `wrong password for room ACDE` /
`room ACDE runs 2026.11.13-b412b68, this copy is 2026.11.10-a1b2c3d` / `room ACDE is full` (at `Players.MAX`
machines). The host states its **build version** beside its **build id** when it opens a room, so the build
refusal names something a person reads. Full slots with machines to spare stay the sequencer's (surplus turned
away, G0038); the sequencer's build check stays as the second line.

**The G0038 amendment, as agreed** (written into `docs/design/decisions/` at the write-up stage):

> **Amends G0038 - a session reached over the internet is a room on the relay.**
>
> - **A room is the relay's record of one session**: its code, its password if it has one, the build it runs
>   and its links. It lasts as long as the session and closes when the last peer leaves. The relay is never a
>   peer: it holds no seats and plays no world.
> - **A room is the host's choice.** Hosting without asking for one stays on the host's own network, with a
>   code the host makes up. A host who asks for a room waits for the relay's code before the session's first
>   tick; if the relay cannot be reached or refuses, hosting fails and says why - no quiet fallback.
> - **The relay hands out the code, and a session has one** - drawn at random from the free codes, no
>   quarantine. A session with a room announces the relay's code to its own network too; on the Join screen a
>   code heard on the network wins, otherwise it goes to the relay. Still four characters, still naming and
>   granting nothing: there is no list of rooms, so a room that cares has a password.
> - **The password lets a machine in; the code only says which room.** Four characters of the code's
>   alphabet, read the same forgiving way, set by the host when hosting and kept for the room's life, through a
>   change of host. Asked only at the relay's door - a machine on the host's network joins without it - and
>   once per machine: coming back is proven otherwise. A game password, not a credential: held in the relay's
>   memory only, never on disk, never logged, not hashed, sent in the clear.
> - **The relay refuses before it forwards anything**: no room, a missing or a wrong password, another build
>   (named by build version), a room already holding `Players.MAX` machines. The machine is told why and goes
>   back to the menu. Full slots in a room with machines to spare are still the sequencer's: the surplus is
>   turned away, not the machine.
>
> **No:** *A list of rooms.* **No:** *Changing the password mid-session* - without a way to remove someone it
> buys nothing. **No:** *A free-form password* - voice, pads and reused real passwords. **No:** *Hosting online
> quietly falling back to the LAN.*

**Raised at the close, not settled here:** Mateusz asked for a page before the first tick listing the players
and the game's options (the number of biomes), with the host starting the game, drop-in still allowed
mid-game. That reverses G0028's and G0038's **Do not build** (a lobby, a ready screen, a host start button) and
touches G0065 (`sim.mission.biomes` is world config, and a mission code names tier + seed only). See the map's
**Not yet specified**.

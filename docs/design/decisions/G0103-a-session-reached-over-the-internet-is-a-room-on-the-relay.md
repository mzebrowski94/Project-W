# G0103: A session reached over the internet is a room on the relay

**Status.** accepted

**Trigger.** Hosting or joining a session over the internet; a room code or a password; what the relay refuses a
machine before it forwards anything; a session's code on a network that also hears it announced.

**Enforced by.** Nothing yet - not built; the tests are named here when it is (`.scratch/online-relay/spec.md`, stages 3 and 6).

**Asked for** by Mateusz, 2026-11-19, in the relay's rooms ticket: rooms reached by code only, an optional password
the host sets, separate from the code. The text below is the agreed text, amended by "Where the sequencer runs"
(ADR 0023): a machine on the host's network joins a session with a room through the relay too, so the announcement carries
the password and that machine "joins without typing it".

**Amends G0038.**

**A session reached over the internet is a room on the relay.**

- **A room is the relay's record of one session**: its code, its password if it has one, the build it runs
  and its links. It lasts as long as the session and closes when the last peer leaves. The relay is never a
  peer: it holds no seats and plays no world.
- **A room is the host's choice.** Hosting without asking for one stays on the host's own network, with a
  code the host makes up. A host who asks for a room waits for the relay's code before the session's first
  tick; if the relay cannot be reached or refuses, hosting fails and says why - no quiet fallback.
- **The relay hands out the code, and a session has one** - drawn at random from the free codes, no
  quarantine. A session with a room announces the relay's code and its password to its own network too; on the Join screen a
  code heard on the network wins, otherwise it goes to the relay. Still four characters, still naming and
  granting nothing: there is no list of rooms, so a room that cares has a password.
- **The password lets a machine in; the code only says which room.** Four characters of the code's
  alphabet, read the same forgiving way, set by the host when hosting and kept for the room's life, through a
  change of host. Asked only at the relay's door - a machine on the host's network joins without typing it, handed it by the announcement - and
  once per machine: coming back is proven otherwise. A game password, not a credential: held in the relay's
  memory only, never on disk, never logged, not hashed, sent in the clear.
- **The relay refuses before it forwards anything**: no room, a missing or a wrong password, another build
  (named by build version), a room already holding `Players.MAX` machines. The machine is told why and goes
  back to the menu. Full slots in a room with machines to spare are still the sequencer's: the surplus is
  turned away, not the machine.

**Scope.** What a room is, how it is opened, named, entered and refused. Not where the sequencer runs (ADR 0023), not
who is host when the host leaves (G0104), not how a dropped machine comes back (G0105), not what the briefing shows
(G0101), and not which provider runs the relay.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A list of rooms.* Rooms are reached by code only.

**No:** *Changing the password mid-session* - without a way to remove someone it buys nothing.

**No:** *A free-form password* - voice, pads and reused real passwords.

**No:** *Hosting online quietly falling back to the LAN.* The host asked for the internet and is told why not.

**Revisit when** strangers find rooms by guessing codes (the abuse limits in `.scratch/online-relay/spec.md` are the first answer), or a group asks to keep people out more firmly than a four-character password does - which needs an identity the game does not have (G0101).

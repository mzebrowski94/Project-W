# The recall

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: 01 (a green `main`)
Spec: [spec.md](../spec.md) answer 8
G-record: **G0121** *With the reserve empty, a trooper is recalled to bring a commander back* (exists; **amends G0055**)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system`

## What

A level no longer fails on an empty reserve while troopers are still on their feet: a waiting commander is paid for by
**recalling** a trooper. G0121 is the design; build it as written.

## Settled (2026-11-24, not provisional)

- **The reserve pays first**, exactly as today (`RespawnSystem`, `ReserveTest`), and a crate fetched while he waits still
  pays for him first.
- **With nobody in reserve**, the tick the reserve would have paid: the trooper **on his feet** nearest the **rally
  point** (`RallyPoint`) leaves the field and pays for him. A down trooper is never recalled. Ties by handle order, never
  `Rng`. Distance crow-flies unless the implementer finds route distance already at hand (then say which).
- The commander comes back **at the rally point**, with the usual delay and shield. Nothing else of his return changes.
- **Co-op:** one recall per waiting commander, lowest slot first, each from the troopers still standing after the last.
- **Not a death:** no corpse, no throes, no kill, no tally (G0044), nothing for a nest or a swarm to feed on. What he
  carried or hauled is let go where he stood, as letting go leaves it.
- **Shown** (pillar 5): the recalled trooper fades out with an effect of its own - never a death's - and a commander
  waiting on a recall shows `RECALL` where he showed `NO RESERVE` (`ui.hud.noReserve` in `assets/i18n/ui.properties`;
  `ReadoutWriter`). `NO RESERVE` stays for a commander with neither reserve nor trooper.
- **The loss** (`G0055`, `OutcomeTest`): every commander out, somebody waiting, the reserve empty **and no trooper on his
  feet**. Latched, as before.
- **Every level**, scenarios included.
- Promote the deferred row in `docs/design/invariants/players.md` ("comes back while any trooper still lives") to
  `enforced` with its test, and update `squad-and-objectives.md`'s reserve and loss rows; fill in G0121's *Enforced by*.

## Open for the implementer

- Whether the recall waits for the commander's throes or lands the tick he is gone (G0055's "the tick after he is gone"
  is the default: keep it unless something forces otherwise).
- The fade's look and length: tunables in `assets/graphic.properties` (e.g. `graphic.recall.*`); a puff-like effect
  entity as the dash's puff is, if that is the existing way (G0004: feedback is a spawned entity).
- Whether the recall needs a snapshot field (pillar 7: anything held across ticks lives where a snapshot carries it -
  `WorldCodecTest`).

## Read first

- `CLAUDE.md`.
- `docs/design/invariants/players.md` (coming back), `squad-and-objectives.md` (reserve, loss, outcome rows).
- G0055, G0061, G0081, G0109, G0121.
- `RespawnSystem`, `RallyPoint`, `ReserveTest`, `OutcomeTest`, `ReadoutWriter`.

## Done when

- `ReserveTest` (or a test beside it) holds: a recall on an empty reserve; nearest the rally point; never a down man;
  one per waiting commander by slot; the crate-first rule; the new loss; no corpse or kill. `verify.sh` green.
- A hidden scenario (`--scenario=<name>`) starting on an empty reserve with a few troopers, for Mateusz to die in.

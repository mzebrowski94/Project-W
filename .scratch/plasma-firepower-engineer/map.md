# Plasma cannon, Firepower, Engineer - map

Label: wayfinder:map
Charted: 2026-11-24

## Destination

G0127, G0128 and G0129 built and green on `pfe-integration`, each change written down where this repository records it:
the records' *Enforced by*, invariant rows, glossary rows; every ticket leaving a hidden scenario to judge it by.

## Notes

- **Agreed design is in [spec.md](spec.md)** and the three records, and is not provisional. Each ticket restates what it
  needs, so it can be read on its own.
- **Precondition.** The records, glossary rows (*sentry*, *plasma cannon*, *orb*, *power*) and this folder were written
  in the grilling session and must be on `main` before the run is cut from it. If
  `docs/design/decisions/G0127-*.md` is missing on `main`, stop: nothing to build.
- **Tickets are build tickets**, `Type: task`, `Label: ready-for-agent`. Each runs `feature` steps 1-3, then
  `/mattpocock-skills:implement` with the project skill it names.
- **Serial, in number order.** 01 and 02 share `weapons.properties`, `upgrades.properties`, `pickups.properties` and the
  crate and upgrade tests; 03 and 04 are one thing in two stages. Parallel branches would spend the run on merges.
- **Numbers allocated to this run**: G0127-G0129 exist and are written as agreed - fill in *Enforced by*, add
  `## Amended by` only where a ticket genuinely changes them. **G0130-G0131 spare**, lowest first. **ADR 0027 spare.**
  (G0123-G0126 and ADR 0026 are the finale-variety run's; never take them.)
- **Judging:** every ticket leaves a hidden scenario (`--scenario=<name>`) Mateusz can play: the proving ground's setup
  (`ProvingSetup`) with the gun or upgrade, if that is the existing way.
- Ticket file: `Type:`, `Status:` (`open` / `claimed` / `resolved`), `Blocked by:`. Claim by setting `Status: claimed`
  before any work. Resolve by appending `## Answer` (what was built, every provisional decision with its alternative,
  the keys), setting `Status: resolved`, and adding a line under *Decisions so far* below.

## Tickets

| # | Ticket | Record | Blocked by |
|---|---|---|---|
| 01 | [Firepower: heavy damage, no flamer stock](issues/01-firepower-heavy-damage-no-stock.md) | G0127 | - |
| 02 | [The plasma cannon](issues/02-the-plasma-cannon.md) | G0128 | 01 (files) |
| 03 | [The sentry](issues/03-the-sentry.md) | G0129 | - |
| 04 | [Engineer and the grenade slot](issues/04-engineer-and-the-grenade-slot.md) | G0129 | 03 |

## Decisions so far

- 2026-11-24: the whole of [spec.md](spec.md), grilled with Mateusz.
- 2026-11-24, 01 (provisional): a blast takes the damage its source declares (G0130, amends G0018), scaled for a gun's
  blast in the one `gun_damage` read; the stock form stays; Firepower's three gun lines fold into one on its card.
- 2026-11-24, 02 (provisional): the orb is on nobody's side and its beam shots on the thrower's; only its ball collides;
  power drained from `Damage.land`'s result; every gun names its `blastDamage` (the orb's 300).
- 2026-11-24, 03 (provisional): the sentry aims with a turret's turn, spares our men by its shots' side, goes off by dying
  with its stun through `Surges.stunRound` beside its blast; `Spawner.spawnSentry`; `SentrySystem` in the death window (43).
- 2026-11-24, 04 (provisional): Engineer switches the key by a `sets_sentries` stat read in `Throws`; rank two is a
  sentry-gun cooldown cut of 20 % a rank on a 0.25 s base; `chooser` alone reaches a sentry, through its slot.

## Not yet specified

- The numbers - orb power, blast damage, sentry health, reach and gun - are starting values; tuning is Mateusz's after
  play, not this map's.

## Out of scope

- A by-type upgrade effect form (G0127: no second user).
- Troopers picking up or using the plasma cannon beyond what any found gun already allows.

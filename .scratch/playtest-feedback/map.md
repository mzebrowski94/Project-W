# Playtest feedback - wayfinder map

Label: wayfinder:map
Charted: 2026-11-11

## Destination

Every item of Mateusz's playtest list of 2026-11-11 either **built and green** or **ruled out** with a line
saying why - bugs fixed, mechanics designed and built, each gameplay change written down where this
repository records it (G-record, invariant row, glossary row, input-map row).

## Notes

- **This map carries execution** (Mateusz, 2026-11-11): a ticket is one session that settles its open points
  and then builds - not a decision to hand off. The `mattpocock-skills` drive each session and the project's
  skills add the context (CLAUDE.md, *Agent skills*): a grilling ticket runs `grill-with-docs` with `feature`,
  then `implement` with `new-system`, `new-kind` or `new-biome`; a bug ticket runs `diagnosing-bugs`,
  smallest reproducing test first. Each ends at the CLAUDE.md checklist, `verify.sh` green, nothing committed on the
  desktop; then **ask** whether Mateusz wants a scenario to judge it by feel (Phase, CLAUDE.md).
- **Skills per ticket** are the first line of its *Read first*: `/mattpocock-skills:grill-with-docs` where
  open points need exploring (03-16), `/mattpocock-skills:implement` where a feature is built (03-17). The bug
  tickets (01, 02) take neither.
- **Starting a ticket is Mateusz's**: `wayfinder` cannot be called by an agent, so the session's message
  begins with the command itself - `/mattpocock-skills:wayfinder .scratch/playtest-feedback/map.md ticket NN`
  (no ticket: the first on the frontier). `grill-with-docs` and `implement` are his to start too: at either,
  the session stops and gives the exact message to send next - the command first, then a brief.
- **A bug ticket's *Done when* states the symptom**, as the player saw it ("an alien fires nothing at the point
  or nest that roused it"), never a strain or a setup: those are the session's guess to make and test. 01's
  named a spitter, which was never the shooter.
- **Bundled by shared code**: items that touch the same systems share a ticket, sized to one session. A
  ticket that turns out bigger stops after a stage and leaves the rest as a new ticket here.
- **Order**: 01 first (Mateusz). The rest by number when nothing blocks; 17 last by request.
- **Vocabulary** is `docs/glossary.md`'s; a new word goes there in the ticket that coins it, and only that
  word afterwards. Words this list needs and does not have yet: *leech* (12), *geyser* (15), the small crate a
  taken point leaves (05), the two trooper profiles (08). "Splitters" in the list meant **spitters**.
- **Flag, never override**: each ticket names the G-record whose Do-not-build column its request touches. The
  session raises it with Mateusz and writes the amendment as a decision record if he keeps the request.
- Ticket file: `Type:`, `Status:` (`open` / `claimed` / `resolved`), `Blocked by:`. Claim by setting
  `Status: claimed` before any work; resolve by appending `## Answer`, `Status: resolved`, and a line under
  *Decisions so far* below.

## Sections and sessions

| Section | Tickets |
|---|---|
| Alien behaviour - bugs | [01 Aliens shoot at control points and nests](issues/01-aliens-shoot-at-points-and-nests.md), [02 Aliens stuck at a shut house](issues/02-aliens-stuck-at-a-shut-house.md), [03 Ground worth going round, and venom on death](issues/03-ground-avoidance-and-venom-on-death.md) |
| Grenades and crates | [04 A grenade count](issues/04-a-grenade-count.md), [05 A crate where a point was taken](issues/05-a-crate-where-a-point-was-taken.md) |
| The squad | [06 Troopers who turn round, and go for nests](issues/06-troopers-turn-round-and-go-for-nests.md), [07 Nests left behind, and commanders on the field](issues/07-nests-left-behind-and-commanders-in-the-field-max.md), [08 Two trooper profiles: SWAT and RETRO](issues/08-two-trooper-profiles.md) |
| Turrets and the finale | [09 Turrets that shrug off blasts, and turrets in the plain](issues/09-turrets-blast-resistance-and-the-plain.md), [10 A harder, varied redoubt](issues/10-a-harder-varied-redoubt.md) |
| Enemies | [11 A grunt that has eaten a man becomes a brute](issues/11-grunt-to-brute-on-a-meal.md), [12 The leech and the slinger that throws it](issues/12-the-leech-and-its-slinger.md) |
| Weapons and the hand | [13 The shotgun rework](issues/13-the-shotgun-rework.md), [14 The leap counter's lock, and the strike's look](issues/14-the-leap-counter-and-the-strike-look.md) |
| Biomes | [15 Lava geysers in the cut](issues/15-lava-geysers-in-the-cut.md), [16 A lava biome with upgraded spitters](issues/16-a-lava-biome.md), [19 A signature strain in every biome](issues/19-a-signature-strain-in-every-biome.md) |
| Balance | [18 The balance round, and more turrets in the plain](issues/18-the-balance-round-and-turrets-in-the-plain.md) |
| General (last) | [17 A build version in the artifact and the menu](issues/17-a-build-version.md) |

Blocking: 05 by 04 · 08 by 06 · 12 by 03 · 16 by 15 · 10 by 18 · 19 by 16, 18. Everything else is on the frontier.

Added 2026-11-12 (Mateusz's second list): 18 and 19, and the keep bug folded into 10. 18 goes first of them -
his hand-tuned numbers sit uncommitted on `main`, and every later sweep runs on them; he commits after it.

## Decisions so far

<!-- one line per resolved ticket: [title](issues/NN-....md): gist -->

- [Aliens shoot at control points and nests](issues/01-aliens-shoot-at-points-and-nests.md): a rouse latched
  the same place a hit does, and barrages shoot a hurt-from place; `Perception.roused` now keeps them apart.
- [Aliens stuck at a shut house](issues/02-aliens-stuck-at-a-shut-house.md): routes were terrain-only; a hunter now
  routes over the ground as it is (shut doors solid) and takes the through-the-door route, the siege, only when it
  can reach nobody otherwise (G0075, amends ADR 0022).
- [Ground worth going round, and venom on death](issues/03-ground-avoidance-and-venom-on-death.md): men clipped the
  flank of ground they ran past (avoidance looked only from a point ahead) - now measured from both; and toxic blood,
  an adaptation leaving a venom spatter where any alien dies, heeded by a man on a keyed chance (G0076).
- [A grenade count](issues/04-a-grenade-count.md): the commander carries 3 (Bandolier +1 a rank), spent a throw, full
  on return; a grenade cache card in crates refills him; pips in the panel; one shadow for what is in the air (G0077).
  Counter to nests withdrawn.
- [A crate where a point was taken](issues/05-a-crate-where-a-point-was-taken.md): no new kind - a taken point lays a
  grenade cache per seated slot beside it, once; a take by touch on the player's side pops; the proving ground's
  others gain crates and grenade caches (G0078).
- [The leech and the slinger that throws it](issues/12-the-leech-and-its-slinger.md): a leech clings - a grip that pins
  nobody, taken by touch or by a leap at a man passing close by, spent on finishing him - and goes off in alien
  venom; alone, on a fuse (a mine's clock). A
  leech slinger slings them onto its target; in badlands and the cut (G0083).
- [The balance round, and more turrets in the plain](issues/18-the-balance-round-and-turrets-in-the-plain.md): his
  numbers in, guarantees kept (strike 300, shotgun 48, pulse 3 bolts, giants 1800, slinger 540); squad sight 27, aliens
  notice x1.3 and amble wider, leaps from farther; a nest is a threat and kept (G0085); a trooper never fires down a
  fouled line (G0084); the plain lays 4-6 turrets.

## Not yet specified

- **A second balance pass**, after 10 and 16 land - a two-sided finale and a lava biome move the fight again; 18
  tunes only what has been played.
- **What else a lava biome brings** besides geysers and its spitters - ground, roster, its finale place in the
  pool - sharpens once 15 has shown how geysers play (16 asks the first of it).

## Out of scope

<!-- closed tickets ruled beyond the destination: gist + why + link -->

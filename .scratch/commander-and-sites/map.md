# Commander and sites - map

Label: wayfinder:map
Charted: 2026-11-22

## Destination

Every item of Mateusz's list of 2026-11-22 built and green on `commander-and-sites-integration`, each
gameplay change written down where this repository records it: a G-record, an invariant row, a glossary row,
an input-map row. The items are the dash and its perks, the commander colours, `--scenario` with Full House,
redeploy, sites and crates, the mainframe, the jaguar, the rift, spires and the relic.

## Notes

- **Agreed design is in [spec.md](spec.md)** and is not provisional. Each ticket restates what it needs, so it
  can be read on its own.
- **Tickets are build tickets**, `Type: task`, `Label: ready-for-agent`. Grilling is done. A ticket runs
  `/mattpocock-skills:implement` with the project skill it names (`new-system`, `new-kind`, `new-biome`),
  after `feature`'s steps 1-3: read the invariants, check them, write the design down.
- **Serial, in number order.** Tickets 05-10 share `biomes.properties`, `Placement`, `units.properties`, the
  glossary and the input map, and 02 and 04 share `CommanderInput` and the wire. Parallel branches would
  spend the run on merges.
- **Numbers allocated to this run**, so no branch guesses:
  - **G-records:** 02 G0107 · 03 G0108 · 04 G0109 · 05 G0110 · 06 G0111 · 07 G0112 · 08 G0113 · 09 G0114 ·
    10 G0115. G0116-G0120 are spare, one at a time, lowest first.
  - **ADRs:** 0024 for ticket 08 (if a new definition table needs one), 0025 for ticket 10 (ice as solid
    ground beside the door). 0026 is spare.
- **Wire:** 02 takes `InputCodec` button bit 64 and 04 takes bit 128. That fills the held byte; say so in
  04's G-record. Each bumps `Msg.PROTOCOL`.
- **Judging:** every ticket leaves something Mateusz can judge the feel by. That is a hidden scenario reached
  with `--scenario=<name>` (01 builds the flag), or for ground work a printed mission code.
- **Flag, never override:** each ticket names the G-record or rule it amends (G0068, G0098, G0056/G0078). The
  amendment is agreed, so write it as a decision record and mark the old record *amended by*.
- Ticket file: `Type:`, `Status:` (`open` / `claimed` / `resolved`), `Blocked by:`. Claim by setting
  `Status: claimed` before any work. Resolve by appending `## Answer` (what was built, every provisional
  decision with its alternative, the keys), setting `Status: resolved`, and adding a line under *Decisions so
  far* below.

## Tickets

| # | Ticket | Spec | Blocked by |
|---|---|---|---|
| 01 | [`--scenario`, Full House and the commander colours](issues/01-scenario-flag-full-house-colours.md) | 2, 3 | - |
| 02 | [The dash](issues/02-the-dash.md) | 1 | - |
| 03 | [Rocketman and Pantera](issues/03-rocketman-and-pantera.md) | 1 | 02 |
| 04 | [Redeploy and the down mark](issues/04-redeploy-and-the-down-mark.md) | 4 | 02 |
| 05 | [Sites, and the crates a taken site leaves](issues/05-sites-and-crates.md) | 5 | - |
| 06 | [The mainframe](issues/06-the-mainframe.md) | 6 | 05 |
| 07 | [The jaguar](issues/07-the-jaguar.md) | 7 | - |
| 08 | [Emplacements, and the fen's spires](issues/08-emplacements-and-spires.md) | 9 | - |
| 09 | [The desert relic and its wardens](issues/09-the-relic.md) | 10 | 05, 06, 08 |
| 10 | [The rift](issues/10-the-rift.md) | 8 | 05, 07 |

## Decisions so far

- 2026-11-22: the whole of [spec.md](spec.md), grilled with Mateusz.
- 2026-11-22, ticket 01 (resolved, merged): `--scenario` resolved by `Scenarios.looselyNamed` in sim (registry only, exact `named` kept for online); mission-versus-scenario conflict caught in `LaunchOptions`; commander gold, `.1` white, trooper blue unchanged. Provisional.
- 2026-11-22, ticket 02 (resolved, merged): dash launched in `CommanderControlSystem`; a press mid-burst ignored; a hauled load pulled at his actual pace so he keeps hold; any `Slowed` halves a dash; puff colour `graphic.color.puff`; button bit 64, `Msg.PROTOCOL` 10. Provisional.
- 2026-11-22, ticket 03 (resolved, merged): Rocketman's surge in `CommanderControlSystem.launch` via `Surges`, `Damage.landStunning`; reach 5/10; Pantera -13 % recharge per rank (whole percents); ring stays where it went off. Provisional.
- 2026-11-22, ticket 04 (resolved, merged): redeploy read in `CasualtySystem`, `PromptKind.REDEPLOY` (no `ActionKind`), mark over the trees, radius 1.6 lift 0.8, panel bar unmarked; bit 128 fills the held byte, `Msg.PROTOCOL` 11. Provisional.

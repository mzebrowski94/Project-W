# Finale variety - map

Label: wayfinder:map
Charted: 2026-11-24

## Destination

G0120, G0121 and G0122 built and green on `finale-variety-integration`, with `main`'s red mission sweep fixed first,
each change written down where this repository records it: the records' *Enforced by*, invariant rows, glossary rows.

## Notes

- **Agreed design is in [spec.md](spec.md)** and the three records it names, and is not provisional. Each ticket
  restates what it needs, so it can be read on its own.
- **Precondition.** The records, glossary rows and README dash row were written in the grilling session and must be on
  `main` before the run is cut from it. If `docs/design/decisions/G0120-*.md` is missing on `main`, stop: nothing to build.
- **Tickets are build tickets**, `Type: task`, `Label: ready-for-agent`, except 01, a bug. Each runs `feature` steps 1-3,
  then `/mattpocock-skills:implement` with the project skill it names.
- **Serial, in number order.** 03 and 04 share `FinaleDef`, `finales.properties`, `Mission` and the sweep; 02 and 05
  share the squad's systems and `ReserveTest`'s neighbours. Parallel branches would spend the run on merges.
- **Numbers allocated to this run**, so no branch guesses:
  - **G-records:** G0120 (03, 04), G0121 (02), G0122 (05) already exist and are written as agreed - fill in their
    *Enforced by* with the tests that now hold them, add `## Amended by` sections only where a ticket genuinely changes
    them. **G0123-G0126 are spare**, one at a time, lowest first, for a ticket that genuinely needs a new record (01 may,
    if the fix changes a rule).
  - **ADR:** 0026 spare.
- **Judging:** every ticket leaves something Mateusz can judge by: a hidden scenario (`--scenario=<name>`) or a printed
  mission code - one ending on each finale kind for 03-05.
- Ticket file: `Type:`, `Status:` (`open` / `claimed` / `resolved`), `Blocked by:`. Claim by setting `Status: claimed`
  before any work. Resolve by appending `## Answer` (what was built, every provisional decision with its alternative,
  the keys), setting `Status: resolved`, and adding a line under *Decisions so far* below.

## Tickets

| # | Ticket | Record | Blocked by |
|---|---|---|---|
| 01 | [The rift's missing jaguar nests](issues/01-rift-jaguar-nests.md) | - | - |
| 02 | [The recall](issues/02-the-recall.md) | G0121 | 01 |
| 03 | [A finale placed by rules](issues/03-finale-placed-by-rules.md) | G0120 | 01 |
| 04 | [Wave roles and the keep's pool](issues/04-wave-roles-and-the-keeps-pool.md) | G0120 | 03 |
| 05 | [The squad assaults the lair](issues/05-the-squad-assaults-the-lair.md) | G0122 | 01 |

## Decisions so far

- 2026-11-24: the whole of [spec.md](spec.md), grilled with Mateusz.
- 2026-11-24, 01 (provisional): the rift's nooks are 7 of radius 3, so a bare one holds a nest; G0115 *Amended by*.

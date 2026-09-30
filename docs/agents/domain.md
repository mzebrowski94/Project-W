# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.
Single-context repo: one domain, several Gradle modules (`map`, `sim`, `core`, `ui`, `net`, `lwjgl3`).

## Where the skills' names map here

| Skills expect | This repo has |
|---|---|
| `CONTEXT.md` (glossary) | `docs/glossary.md` - grouped by subsystem; read the whole section, not just one word |
| `docs/adr/` (structural decisions) | `docs/adr/` - index and **Do not build** column in `docs/adr/README.md` |
| (gameplay decisions) | `docs/design/decisions/` (`G0001`...) - index in its `README.md` |
| (invariants) | `docs/design/invariants/`, indexed from `docs/design/README.md` |

Never create a root `CONTEXT.md` or `CONTEXT-MAP.md`: a second glossary beside `docs/glossary.md` is a
defect here (CLAUDE.md, *Reuse before you create*). When `/domain-modeling` resolves a term, it goes into
`docs/glossary.md` under its subsystem section; a structural decision becomes the next `docs/adr/NNNN-*.md`,
a gameplay one the next `docs/design/decisions/GNNNN-*.md`, each added to its README index.

## Before exploring, read these

- **`docs/glossary.md`**: the section(s) for the subsystem you are touching.
- **`docs/adr/README.md`** and the ADRs that touch the area you're about to work in.
- **`docs/design/decisions/README.md`** before proposing a gameplay alternative.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name),
use the term as defined in `docs/glossary.md`. Don't drift to synonyms.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the
project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR and decision conflicts

If your output contradicts an existing ADR or G-decision, surface it explicitly rather than silently overriding:

> _Contradicts ADR 0007 (circles-only collision), but worth reopening because..._

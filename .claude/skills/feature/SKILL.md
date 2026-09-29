---
name: feature
description: Add or change gameplay in Project-W without contradicting existing design. Use when adding a mechanic, weapon, enemy, level rule, pickup, input binding or progression change - before writing any code.
---

# Adding gameplay

Code conventions are in the `new-system` skill. This one is about not contradicting the rest of the
game. Work through it before writing anything.

## 1. Read the constraints

The pillars are in `CLAUDE.md` - already in context. Read `docs/design/README.md` for the invariants,
the input map and the open questions. That one file is the whole design surface; you do not need to
read the systems.

## 2. Check the feature against them

State explicitly, in your reply, which invariants and pillars the feature touches. Three outcomes:

- **Consistent** - say so in one line and continue.
- **Would break an invariant or a pillar** - stop. Say which one and what the conflict is, and let
  Mateusz decide. Do not weaken an invariant to fit a feature; that is how a game loses its shape,
  and the invariant was written when the trade-off was clearer than it is now.
- **The design does not say** - check the open questions. If it is listed there, ask. If it is not
  listed and you are about to invent an answer that later features will have to live with, ask
  anyway, and add it to the open questions.

A feature that quietly reinterprets an existing rule is worse than one that openly contradicts it -
the second gets caught.

## 3. Write the design down before the code

Whatever the feature settles becomes part of the design surface, in the same change as the code:

- A new rule that must always hold -> a row in the invariants table. Mark it `enforced` with the
  test name if you can test it, `judgement` if you cannot. Prefer testable.
- An alternative you rejected that someone will propose again -> a record in
  `docs/design/decisions/`, following the format in its README. Only if it is genuinely re-proposable.
- A new input -> a row in the input map. Check nothing else claims it.
- A new domain word -> `docs/glossary.md`, and then use only that word.
- A question you deliberately left open -> the open questions list.
- Every tunable number -> `assets/sim/*.properties`, never a literal in a system.

If the feature produced none of these, that is a signal worth noticing: either it was smaller than
it looked, or something went undocumented.

## 4. Build it

Hand off to the `new-system` skill for the component, the system and the tests. The TDD order in
`CLAUDE.md` applies: general tests, implementation, then the specific cases.

Promote any `deferred` invariant your feature just made testable - that is the moment it stops being
prose and starts being enforced, and it will not happen later on its own.

## 5. Verify

`.claude/scripts/verify.sh`, then re-read your invariant list from step 2 against what you actually
built. Implementations drift from intentions in small ways, and the drift is easiest to see while
the change is still fresh.

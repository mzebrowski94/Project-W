# Finale variety, the recall and the squad's assault - spec

Grilled with Mateusz on 2026-11-24 (`grill-with-docs` with `feature`). **Not provisional**: the records below are the
agreed design, written before any code. This file only points at them and names what the grilling settled beyond them.

| Record | What it settles |
|---|---|
| [G0120](../../docs/design/decisions/G0120-a-finale-is-placed-by-rules-round-a-fixed-skeleton.md) | A finale is placed by rules round a fixed skeleton; enemies dealt by role; two turrets in the keep. Amends G0064, G0087. |
| [G0121](../../docs/design/decisions/G0121-with-the-reserve-empty-a-trooper-is-recalled-to-bring-a-commander-back.md) | With the reserve empty, a trooper is recalled to bring a commander back. Amends G0055. |
| [G0122](../../docs/design/decisions/G0122-the-squad-assaults-the-lair-nests-first-then-the-boss.md) | Once a lair has risen, the squad assaults it: nests first, then the boss. Amends G0061. |

Glossary words already written: **skeleton** *(finale)*, **lee**, **role** *(wave)*, **recall**; *layout* moved to the
words we do not use. Open question already written: *Should the lair rise a second time?* - **not** to be built.

## Answers from the grilling, in Mateusz's choices

1. Placement technique: **rules through seeded `Scatter`** (over sockets in hand-made layouts, and over more layouts).
2. Fixed: post, watch, mouths, the flip, the boss's spot **and the six lair nest spots** (only their strains are dealt).
3. **Mirrored north-south pairs.**
4. Cover: **bands with an open, reached lee**; shapes from a short list (short bars, L shapes).
5. Counts **dealt from min-max ranges**, never moved by tier or player count; turrets fixed - **4** in the redoubt,
   **2** in the keep, a mirrored pair between the post and the boss, inside his barrage's reach.
6. Waves: **roles** (fodder, fast, ranged, heavy), each defined once in the block as strains with a count range each; one
   member dealt per wave; time and mouths as written.
7. Keep nests: each of six spots from the **keep's own pool of about nine walking strains, no strain more than twice**.
   The lair's guns dealt from the keep's list, none twice.
8. Recall: commander back **at the rally point**; the trooper recalled is the one **on his feet nearest the rally
   point**; every level; co-op one per waiting commander, lowest slot first; `RECALL` on the HUD.
9. Squad: **assault from reach** in the keep once its lair has risen; the redoubt unchanged.
10. Boss second rising: **no**, recorded as an open question.

Accepted consequence: every mission code dealt before ticket 03 ends on different finale ground (G0065).

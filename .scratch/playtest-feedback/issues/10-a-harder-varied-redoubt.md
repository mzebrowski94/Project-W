# A harder, varied redoubt

Type: grilling
Status: open
Blocked by: 18
Section: Turrets and the finale

## Question

1. The **redoubt**'s **waves** are too easy: send them from two **mouths** at once.
2. Vary where its turrets, mines and **pieces** stand from mission to mission, keeping a symmetry or pattern.
3. **Bug (Mateusz, 2026-11-12):** in the **keep**, troopers do not fight the **boss** or the **nests** round it -
   they should.

How does variety fit a **finale** that is a set piece? Blocked by 18: "too easy" is judged on 18's numbers.

The keep is the redoubt's ground with a **lair** (glossary *keep*), so a varied layout reaches it too - decide
whether the keep varies with it.

## From Mateusz (translated)

> Finale: the wave finale needs to be made harder somehow, e.g. waves should appear from two sides at once.
> Finale: vary the layout of turrets, mines and walls in finales (it would be good to keep some symmetry/pattern
> of turrets and mines).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-biome` (finale family). G0064 (a finale is a set piece; Do not build *a finale laid out by
rules*), G0027, G0061, `finales.properties`, `Strip.anchor`, `docs/glossary.md` *Levels and objectives*.

## Conflict to settle

G0064 rules out a finale laid out by rules. A few symmetric hand-made layouts per finale, one dealt by the seed,
keeps it a set piece - propose that and amend G0064 if Mateusz agrees.

## The bug (3)

Do it first, as a bug: `/mattpocock-skills:diagnosing-bugs`, smallest reproducing test first. Symptom as the
player saw it: *with the lair woken, troopers in reach of the boss or a nest by it shoot neither.* Leads, unconfirmed:
everything of a lair is put down on one tick (G0048) - whether it reaches what troopers notice; the boss is
**rooted** at zero speed; 06's nest reach (`Enemy.standsOut`, G0080) for the lair's nests; the boss shooting from
past where a trooper notices. Those are guesses to test, not the cause. From 18 (2026-11-12): a trooper's sight is
now 27 (hold 34) and the boss notices at 58 with a spew reaching 32, so the last lead is likelier than before; the boss
is no nest, so 18's nest threat and keeping (G0085) do not reach him.

## Done when

The bug fixed with its reproducing test. Built as data, the sweeps green over every layout, the decision record,
`verify.sh` green. Ask for a scenario.

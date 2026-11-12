# A signature strain in every biome

Type: grilling
Status: open
Blocked by: 16, 18
Section: Biomes

## Question

Biomes should differ by more than ground: give each its own enemies - the way the slinger marks the anthill and
badlands - so a strip reads as a run of different fights. Which biome lacks one, what is it, and which of those are
a new strain as data on what exists (G0008, `new-kind`) and which are new behaviour (`new-system`, its own ticket)?

## From Mateusz (translated)

> Add more unique enemies like slingers etc. to biomes - the goal is to make biomes more interesting and varied.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind`, `new-biome` (*Roster and furniture*). Every `sim.biome.*.roster` in
`biomes.properties`, `docs/glossary.md` *Units and sides*, G0068, G0070, G0074, G0083, 16's answer (the lava biome's
upgraded spitter is the first signature strain made for a biome), 18's answer (the numbers the rosters are tuned
against).

## Where it stands (rosters, 2026-11-12)

- **Plain** - grunts, runners: nothing of its own.
- **Quarry** - brutes, spitters, grunts: nothing of its own.
- **Brake** - runners, ants, sludgers, brutes: shares all of them.
- **Anthill** - ants, sludgers, grunts, slinger.
- **Badlands** - spitters, grunts, slinger, leech slinger.
- **Cut** - brutes, spitters, ants, giants, slinger, leech slinger: the most crowded.
- **Fen** - frogs (its own), ants, sludgers, giants.

## Open

- What "its own" means: a strain found only there, or one weighted heavily there and rare elsewhere?
- One per biome in this ticket, or the shape settled here and each strain built as a ticket of its own (the map's
  rule: a ticket that turns out bigger stops after a stage and leaves the rest as new tickets)?
- Variants that are numbers on an existing strain (a quicker runner, an armoured grunt) versus a strain with a new
  move - the second is `new-system` and a G-record each.
- Pillar 5: each new strain legible apart from the others at a glance (colour, size, look).

## Done when

The plan for every biome settled and written; the strains that are data built, each in its biome's roster with the
mission sweep green; the ones needing new behaviour left as tickets here. Glossary rows, `new-biome` amend step,
`verify.sh` green. Ask for a scenario.

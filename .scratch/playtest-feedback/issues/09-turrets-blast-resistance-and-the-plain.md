# Turrets that shrug off blasts, and turrets in the plain

Type: grilling
Status: open
Blocked by: -
Section: Turrets and the finale

## Question

1. A **turret** takes about 90 % less from a **blast**.
2. The plain **biome** lays turrets.

Where does "less from a blast" live so it is one number with one reader - a field on the definition every kind
already has, zero meaning off (CLAUDE.md, *Small patch, right shape*) - rather than a turret special?

## From Mateusz (translated)

> Turrets should be more resistant to explosions (about 90 % damage reduction from blasts).
> Turrets should be added to the "plain" biome.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind` / `new-biome`. G0018 (the blast), G0024 (the turret), G0045 Do not build (*armour
against blasts* - the ant's head; this is a different thing), G0054 (`Whole` rounds it), G0068 (placement),
`docs/backlog.md` (*Turret, mine and nest are one of a kind each*), the plain's block in `biomes.properties`.

## Open

Whether the crew aboard still dies to the blast (G0024: a blast reaches him); how many turrets in the plain, and
where placement puts them; the new-biome amend step.

## Done when

Both built, keys with values, `StripSweepTest` / `MissionSweepTest` green, `verify.sh` green.

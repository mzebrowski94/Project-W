# Turrets that shrug off blasts, and turrets in the plain

Type: grilling
Status: resolved
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

## Answer

G0082 (provisional, for review).

- **Blast resistance** lives on `Health` - the one thing every damageable kind has - as `blastResistance`, per cent
  shrugged off, zero (off) by default. One reader, `Health.fromBlast`, in `BlastSystem` before `Damage.land`, rounded
  by `Whole`. Not G0045's armour: no side, no head. Key `sim.turret.blastResistance=90` (`level.properties`): a blast
  takes 100 of the turret's 360, so the fourth one takes it.
- **The crew** still takes the blast (G0024 unchanged): he has no health to resist with.
- **The plain** lays `furniture.turretsMin=1`, `turretsMax=2` (zero on every other block); placement puts each within
  `pointReach` of a point, after the crates by points, off nests. Drawn last, so other biomes draw as before.
  `new-biome` amended (*Fit*, *Roster and furniture*, *Porting*) with a CHANGELOG line.

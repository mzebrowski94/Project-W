# Ground worth going round, and venom on death

Type: grilling
Status: resolved
Blocked by: -
Section: Alien behaviour - bugs

## Question

Two items on one system - **avoidance** of ruined ground:

1. **Bug**: units walk into **fire** and **venom** too often instead of going round. A defect in how ground
   is given room (G0046), or its tuning?
2. **Adaptation**: aliens leave a small **venom** **slick** where they die; a trooper steps round it on a
   **chance** (high).

Fix first, then the adaptation on top. "Leave a spatter on dying" is the mechanism 12's leech reuses, so it is
a declaration any strain can name, not a grunt special.

## From Mateusz (translated)

> BUG: units too often walk mindlessly into fire or venom without avoiding it.
> Adaptation: enemies leave venom when they die (a small puddle - troopers should roll whether to avoid it,
> high chance).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-system` / `new-kind`. G0034, G0035, G0046 (ground is given room by whoever it can
catch), G0039 (chance), G0018 (things explode by dying - the one death reaction), `AvoidanceSystem`,
`AiChance`, the *breed* effect in `docs/glossary.md` *Upgrades*.

## Conflicts to settle

- G0035: venom is *walked through*. A trooper stepping round it on a roll amends that for men.
- G0046 Do not build: *avoidance asking whether something is a slick* - the roll rides on the existing
  declaration, never a type check.

## Done when

Both built, keys in `assets/sim/*.properties`, a decision record for the venom amendment, `verify.sh` green.

## Settled in grilling (2026-11-11)

Adaptation - all agreed by Mateusz:

1. **Where it lives.** Every strain's definition carries a slick it leaves on dying, off at zero circles
   (like `RangedDef.off()`). A dying alien carries `SlicksOnDeath` as a shot does; `SlickSystem` lays it
   through the one death reaction (G0018). The adaptation is `count death_slicks aliens N`, read in one
   place through `Modifiers`. Ticket 12's leech declares its own circles above zero.
2. **The puddle.** Frog venom's harm (wounds and shoves, G0053), on the alien side so aliens wade it;
   smaller and shorter - first guess 2 circles at ~0.7 of a frog circle's radius, ~4 s. Every death lays
   one, whatever the cause.
3. **Ranks.** Two, each adding 2 circles - a bigger puddle, not a longer one.
4. **The roll.** Per man per puddle: a seeded draw keyed on (man, spatter), stateless, every circle of
   one spatter sharing the key - a man goes round all of it or walks through all of it. First chance
   taken per attempt rather than per second: answers the open question in `docs/design/README.md` as
   *keyed, not dealt* (G0039 does not apply).
5. **Which ground.** A field on the slick definition, `heeded` (1 = always given room). The puddle
   declares 0.85; frog venom and fire keep 1. Avoidance reads the field and never asks what the thing
   is (G0046 Do-not-build kept).
6. **Who rolls.** Troopers only - the commander is not steered, aliens wade their own side's ground.
7. **Words.** The puddle is a **spatter** of **venom**; new glossary words **toxic blood** (the
   adaptation, `toxic_blood`) and **heed** - written.
8. **Record.** New G-record "A man heeds ground on a chance the ground declares", amending G0046, plus a
   row in the aliens invariants file.

Bug - diagnosed by probe (shipped config, 46 lanes: 9 entered, nearly all 1-tick grazes of 0.02-0.16
depth, each a full wound and shove; desire then walks the man back and the second graze downs him):

9. **Cause: a defect, not tuning.** `AvoidanceSystem` measures danger direction and gap only from the
   unit's projected point (4.7 ahead at 13.5 x 0.35). A circle beside his body is behind that point, so
   every forward slot scores zero danger and he clips its flank. One circle is never entered; a spatter is
   (rounding one blob, the line back to the goal meets the next one's flank). Lookahead 0: 0/46; 0.8: worse.
   **Fix:** measure each hazard from the current position as well as the projected one, keep the greater
   threat per slot. Keys unchanged; retune only if the sweep still shows grazes.
10. **Tests.** Failing first: `AvoidanceSystemTest.neverClipsTheFlankOfGroundItIsRunningAlongside` -
    shipped venom shot from (20,50) east, trooper at (30,55), control point at (95,55), enters at t99
    today. Plus a sweep: no lane of a shipped venom or fire spatter is entered.
11. **Point inside a spatter** (man loops in, out, down): re-check after the fix; if it persists, a new
    ticket ("a man whose goal lies in ground he heeds"), not this one. No flow-field routing (G0046).
12. **A graze costs a full wound** - unchanged (G0034, pillar 1).
13. **Dodge, leap, hop carrying a unit in** - by design, out of scope.

Build order: the fix first (tests 10), then toxic blood (1-8).

Settled at the start of stage 2 (Mateusz, 2026-11-11), refining 1, 4 and 5:

14. **The shape is one shared block**, `sim.blood.*` in `hazards.properties` (fire's precedent); each strain
    declares only `deathSlicks` (circles, 0 = off), and `death_slicks` adds to it, read as the alien dies.
15. **`SlicksOnDeath.bodily`** - a declared field, true where a strain spawns, false for shots and blasts - so
    toxic blood never adds circles to a frog's shot (the `aliens` target reaches alien shots too).
16. **The keyed draw**: each circle's `Collider` carries `heeded` and `spatter` (the dying alien's handle);
    avoidance draws `Rng.keyed(seed, spatter, man)`, no allocation, no stream advanced.
17. **Test seams**, at `Simulation` level in `ToxicBloodTest`: puddle laid on alien side / none without; second
    rank bigger; frog shot unchanged; unheeding man walks straight in, heeding man goes round whole; share
    over many men about the chance and the same on replay.

## Answer

Built (2026-11-11), `verify.sh` green, nothing committed.

- **Bug - a defect, fixed.** Avoidance measured ground only from where a unit would be a moment ahead, so a
  circle level with a man running past was behind that point and he clipped its flank - a 1-tick graze, a full
  wound. Ground is now measured from where he is as well (`AvoidanceSystem`, ground only; bodies unchanged). No
  key moved. Enforced: `AvoidanceSystemTest.neverClipsTheFlankOfGroundItIsRunningAlongside`,
  `.walksPastEveryShippedSpatterWithoutSettingFootInIt` (13 lanes x venom, grenade fire, barrel fire). Point 11
  re-checked: a point inside a spatter no longer loops a man in and out; no new ticket.
- **Toxic blood - built** as settled in points 1-8 and 14-17: [G0076](../../../docs/design/decisions/G0076-a-man-heeds-ground-on-a-chance-the-ground-declares.md),
  amending G0046 and G0039; `ToxicBloodTest`; keys `sim.blood.*`, `sim.unit.<strain>.deathSlicks`,
  `sim.upgrade.toxic_blood.*`, `sim.upgrade.stat.death_slicks.*`, `slicks.heeded` on venom and glob.
- **Leech (ticket 12)** reuses it by declaring `deathSlicks` above zero on its strain.

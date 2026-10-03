# Game design

The **pillars** live in `CLAUDE.md` because they apply to every change, including ones nobody
planned as gameplay work. They are not repeated here.

## What belongs here, and what does not

This file carries **constraints and intent**. It does not describe how systems behave - that is what
the code is for, and a description that disagrees with `sim/system/**/*.java` is worse than no
description at all.

| Question | Where the answer lives |
|---|---|
| What must always be true? | Invariants, indexed below |
| What is still scaffolding, and how is a scenario added? | [`alpha-phase.md`](alpha-phase.md) |
| What does this system do? | `sim/src/main/java/.../system/<package>/` |
| What is this number? | `assets/sim/*.properties` |
| Why did we reject the obvious alternative? | `docs/design/decisions/` |
| What does this word mean? | `docs/glossary.md` |

## Invariants

Statements that must hold across every feature, one file per domain. **Before adding a mechanic, read
every file below whose domain it touches, and check it against their rows**; if it would break a row,
stop and raise it rather than deciding alone.

`enforced` rows have a test and cannot drift. `judgement` rows need a human to notice. `deferred`
rows become enforceable once the named feature exists - promote them then.

| File | What it holds |
|---|---|
| [`docs/design/invariants/players.md`](invariants/players.md) | slots and input, aim, the leash, a commander coming back, the action key |
| [`docs/design/invariants/simulation.md`](invariants/simulation.md) | determinism and randomness, entities and components, the one spawner and reaper, config |
| [`docs/design/invariants/movement.md`](invariants/movement.md) | desired velocity and drivers, avoidance, routes, weave, spread, gait, slows, thickets, facing |
| [`docs/design/invariants/aliens.md`](invariants/aliens.md) | perception, aggro and whom they hunt, dodges, leaps, evolution, shooters, nests, ants, the boss, lairs |
| [`docs/design/invariants/combat.md`](invariants/combat.md) | projectiles, melee, the clinch and stun, blasts, slicks, fire, mines, grenades, guns, heat, stations, the beam, whole hit points |
| [`docs/design/invariants/map-and-view.md`](invariants/map-and-view.md) | generation, terrain and collision against it, the arena, scatter, sight lines, the camera |
| [`docs/design/invariants/squad-and-objectives.md`](invariants/squad-and-objectives.md) | troopers, orders, the reserve and reinforcements, control points, posts, waves, objectives |
| [`docs/design/invariants/wounds.md`](invariants/wounds.md) | harms, wounds and grace, going down, bleed-out, stabilising, hauling, shields, stuns |
| [`docs/design/invariants/pickups.md`](invariants/pickups.md) | who takes what, glow, weapons found and lost, barrels, crates, medkits |
| [`docs/design/invariants/progression.md`](invariants/progression.md) | the tally, upgrades, drafts, adaptations, the swarm's health, nothing between levels |

A new row goes in the file of the domain it constrains; one that spans two goes where its test lives,
and one that leans on "the row above" stays next to it. A new file is a row here, or `SourceCitationsTest`
fails the build - a file the index does not name is never read.

## Input map

One key, one action. **Adding a binding means editing this table in the same change** - this is the
cheapest guard against two features silently claiming the same input.

*Built* rows work today. *Settled* rows are fixed by a decision but not yet wired up; *proposed*
rows are still a guess.

**Both columns are live.** The keyboard is player one's and no pad takes it in the alpha (1.a); every
other player is a pad. Every binding below is in [`assets/controls.properties`](../../assets/controls.properties)
and can be changed there without touching code.

Pad buttons are named by **where they are**, never by what is printed on them: `SOUTH` is A on an Xbox
pad and Cross on a PlayStation one, `WEST` is X and Square, `EAST` is B and Circle. The Xbox and
PlayStation names below are the two label sets for one binding, not two bindings (ADR 0011).

The alpha-phase harness has its own keys - pause, single-step, restart - listed in
[`alpha-phase.md`](alpha-phase.md). Those are dev controls and deliberately kept out of this table;
do not treat one as taken when adding a game binding. None of them is on the pad.

| Keyboard | Pad (Xbox / PlayStation) | Action | Status |
|---|---|---|---|
| `WASD`, arrow keys | Left stick | Move the commander | built |
| Mouse | Right stick | Aim | built - G0002. The stick sends a direction; the mouse sends a place |
| Left mouse | `X` / `Square`, or `RT` / `R2` | Fire | built |
| Right mouse | `LT` / `L2` | Melee strike | built |
| `Q` | `B` / `Circle` | Throw a grenade | built - G0019 |
| `E` | `A` / `Cross` | Act on what is in reach - stabilise a casualty, get on or off a turret, open a crate | built - G0021, G0024, G0056 |
| `F` | `RB` / `R1` | Take hold of a load, or let go of it | built - G0021 |
| `1` | D-pad up | Squad order: follow me | built - scaffolding, see below |
| `2` | D-pad right | Squad order: forward | built - scaffolding, see below |
| `3` | D-pad down | Squad order: defence | built - scaffolding, see below |
| `4` | D-pad left | Squad order: cancel | built - scaffolding, see below |
| `Tab` | `LB` / `L1` | Pull the view back for a look, and put it back. **One camera, so anybody's press moves everybody's view** | built |
| `` ` `` | - | Swap the bottom bar between the players' panels and the roll call. This machine's bar, so it swaps for everybody on it | built - the roll call is a placeholder |
| `Esc` | `Start` / `Options` | Open the pause menu, which asks for the pause. **The pause is everybody's; the menu is this machine's** - another machine sees the paused banner. Offline, losing the window or a seated pad opens it too | built |
| `C` | `Y` / `Triangle` | Open the oldest upgrade draft of this seat's slot in the top bar; with it open, take the highlighted card (G0049). This machine's top bar, one draft at a time. Solo, it pauses while open (D2) | built |
| `1`-`3`, while a draft is open | D-pad left / right | Choose a card. The chooser's order inputs give no order while his draft is open; his move, aim, fire, strike, grenade and action stay live | built |
| `4`, while a draft is open | D-pad down | Close the draft and keep it | built |
| `1`-`4`, while a crate is open | D-pad left / right | Highlight a card of the crate (G0056). The opener's order inputs give no order while it is open; his move, aim, fire, strike and grenade stay live | built |
| `E`, while a crate is open | `A` / `Cross` | Take the highlighted card, which shuts the crate | built |
| `X` | D-pad down, while a crate is open | Shut the crate, taking nothing. `X` is claimed by nothing else | built |
| - | - | Unbound on the pad: `Back`/`Share`, both stick clicks. Left free on purpose | - |

**In pages and modals** (the pause menu, and the menus to come): arrows or `W`/`S` and the d-pad or
left stick move the selection, repeating while held; `Enter`/`Space` and `A`/`Cross` confirm;
`Esc`/`Backspace`, `B`/`Circle` and the right mouse button go back; the mouse hovers and clicks; a pad's
`Start` resumes. These are the same buttons that act and throw in a level, so **a press that changes
context is spent**: whatever is held when a menu opens or closes, or a level starts, does nothing in the
game until it has been let go once. Bindings: `controls.ui.*` in `controls.properties`.

Plugging a pad in **is** joining, and unplugging is not leaving (G0028). There is no binding for
either and there is not going to be one.

The four order keys, and the d-pad that mirrors them, are **alpha-phase scaffolding, not a
decision**. The command *model* is settled -
an order reaches the troopers near the commander and names a verb, never a target (G0017) - but the
input is not: the intended binding is one button plus a radial menu, which needs interface work that
does not exist yet. Four keys make the verbs playable in the meantime. They are listed here rather
than with the harness keys because they drive the game rather than the dev loop, so nothing else may
claim them.

## Open questions

Deliberately undecided. **Ask rather than inventing an answer** - a guess here becomes a
contradiction three features later.

- **What does the view do with a player it cannot frame?** The leash was tried at the width of the
  view, which is what pillar 4 asks for, and it played badly - being stopped by an invisible wall
  because somebody else walked the other way is not a fight, it is a tether. It is now far wider than
  the screen and is a backstop rather than a frame, so **a player can be off screen**, which pillar 4
  says should not happen. That is a knowing gap, and the question it leaves is which of the answers
  fills it: the camera giving up at far zoom and a marker at the edge pointing at whoever is missing;
  splitting the view; or a soft pull that only starts once somebody is actually out of shot. None is
  chosen, and a marker is the cheapest thing to try first. Since G0056 the leash holds only the players at one machine: online, two
  machines' players are each framed by their own camera and may be a level apart - so the question is now
  only about players sharing a screen. Whether online players should be drawn back together at all is
  answered for the boss by the lair's regroup count; a level with other places everybody must reach will
  ask it again.
- **Do upgrades carry between levels?** They last the level (G0049). Carrying them on is blocked on
  what happens when a level's objectives are met, and asks: does a slot's personal ledger follow the
  slot or the man, does an unspent draft carry over, and do the aliens' adaptations reset?
- **Should the aliens adapt to what the players take?** Adaptations are dealt on a schedule, at
  random; a hook for weighting them against the players' upgrades exists and is off.
- **Rerolls.** None in v1. If one is ever wanted it is an input event limited by a count, never by a
  currency (pillar 3).
- **Can an adaptation raise an alien's health?** For men it can (G0053: wounds are damage taken, the
  maximum folded at the read). For the aliens it is a question about adaptations; the swarm's health
  factor (`sim.swarm.healthPercent`) is config, not a stat, and does not raise it.
- **Should anything hurt a man more than one step at once?** A heavy shot worth two wounds is the
  first thing three harms cannot say (G0053's revisit). If it comes, it is a count on the harm, never
  an amount.
- **Is a shield ever found rather than earned?** The respawn shield is the only one (G0053). A pickup, an
  upgrade that restores one, or one that holds more than a hit are all open, and each changes how
  often pillar 2's "no invulnerability" is tested.
- **How is a chance per attempt dealt, when there first is one?** Every chance today is per second of
  opportunity and is dealt from a due (G0039). A critical hit, a drop or anything else rolled once per
  attempt would want the same even deal counted in attempts - Dota's P = C x N or a hard pity - and
  whether it is kept per unit or per slot (pillar 7) is a real choice. Nothing needs it yet.

- **What is the input for a squad order?** The model is settled (G0017): an order reaches the troopers
  near the commander and names one of four verbs, never a target. Keys `1`-`4` are scaffolding; the
  intended binding is a button plus a radial menu.
- A body is scenery for its throes - solid, immovable, and it stops bullets. That is realistic and it
  is what makes the clinch settle at touching distance, but it also means shooting into a crowd now
  leaves obstacles for a second at a time. Whether that reads as good feedback or as clutter is a
  question for play, not for a test.
- **Should a trooper still go for a downed commander when another player is closer?** His troopers
  pick him up (G0023), and a second player can reach him with the same action key. A squad and a friend
  racing for the same body is either the best moment in the game or a waste of a trooper, and only play
  will say which.
- **Should noise pull aggro?** Detection is a radius around each unit and nothing the player does
  changes it, so firing a gun in a corridor wakes nobody who could not already have noticed you. A
  shot that wakes things beyond the detection radius is a big arcade lever and it is cheap to add now
  and awkward to retrofit later, but it is a decision about how loud the game wants to be.
- **Can a rabbit be killed with the hand strike?** Only a projectile reaches the damage path through
  a pickup, so a sweep passes through one today. Denial at range is the interesting half and it works;
  whether denial in your face should also work is undecided, and it is a rule about what a melee
  weapon is for rather than a gap in the rabbit.
- **Where do rabbits come from?** A scenario spawns a handful and that is all there ever are. Whether
  a level breeds them, whether running out matters, and whether the aliens are meant to clear them
  before you can get to them are all still open - and they decide whether the rabbit is a resource or
  a set piece.
- **What happens when a level's objectives are met?** The alpha only says MISSION COMPLETED on the goal
  line and the world runs on. Extraction, an immediate cut, a lull before the next wave and a
  score screen are all still open, and this is now the question standing between the alpha and a
  playable level.
- **Should a trooper ever break off a fight?** It stands still and shoots until whatever it noticed
  is dead or out of range (1.d), which is exactly right for an arcade squad and exactly wrong if the
  thing shooting back is a brute. Retreating, repositioning and taking cover are all unaddressed, and
  the first of them that gets added needs this answered rather than guessed.
- **Can the other side take a control point back?** Capture is one-way today. Whether an alien can
  stand on a point and undo it is a question about what the aliens are *trying to do*, which nothing
  in the game has yet said.
- The source game had four commanders because it was four-player co-op, which this now is (G0028).
  **Are the four the same man in four colours, or four commanders with distinct kits?** Today they are
  identical but for colour. Distinct kits would give a reason to pick one and a reason to want a
  second player, and it is the question standing between "co-op works" and "co-op is worth doing".
  In single-player it is the same question asked once. **A first answer (G0049):** personal upgrades
  give each slot a kit of its own over a level; whether commanders *start* distinct is still open.
- Does the squad persist between levels, or does each level grant a fresh one?
- On failure, do you retry the level, or is the run over?
- Given "no between-level management", what does progression across the nine levels actually consist
  of - only what you carry out of a level?
- **A one-tile gap takes grunts and refuses brutes**, because a tile is 5 world units and a brute is
  5.2 across. That fell out of the tile size rather than being designed, and it is a real tactical
  rule the player would learn: a crack you can back into is a crack the heavy thing cannot follow you
  through. Keep it as a feature, or make a tile at least as wide as the widest body so a gap admits
  everything and corridors are measured only in whole tiles? Deciding to keep it means unit sizes and
  tile size are now coupled, and a future unit larger than a tile needs this answered again.
- What does alien food do for the commander? Mushrooms and flowers currently offer him nothing, so he
  walks over them. Denial by eating them, a use of his own, or nothing at all are all still open - and
  "nothing at all" is a real answer, not a gap.
- Can a pickup be destroyed? Shooting a mushroom would make denial something the player can do at
  range rather than only by standing on it, and would change how a feeding ground reads. Nothing
  supports it today.

The alpha phase answers none of these. Where a system needs one of them to have an answer it uses
scaffolding instead, listed in [`alpha-phase.md`](alpha-phase.md) so the scaffolding is not later
mistaken for a decision. An answered question leaves this list for the record that answers it.

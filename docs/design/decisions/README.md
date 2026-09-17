# Gameplay decisions

Closed decisions about what the game *is*, kept separate from `docs/adr/`, which covers technical
and structural choices. Same format, same discipline, different subject.

Open a record only when you are about to work against it or propose changing it - each one opens with
a **Trigger** line saying when that is. The **Do not build** column is the negative index: if the
mechanic you are about to write appears there, it has already been rejected and the record says why.

| # | Decision | Do not build | Status |
|---|---|---|---|
| [G0001](G0001-inspired-by-not-remake.md) | Inspired by *Triumph! War 2099*, not a remake of it. | a faithful remake; the original levels or roster | accepted |
| [G0002](G0002-aim-is-independent-of-movement.md) | Aim is independent of movement. | facing that follows movement | accepted |
| [G0003](G0003-the-renderer-reads-a-component.md) | The renderer reads a component; it never asks what an entity is. | branching on kind in the renderer; placeholder sprites; colour on a gameplay component | accepted |
| [G0004](G0004-no-event-bus.md) | No event bus - reactions are components, feedback is a spawned entity. | an event bus, message queue, listener registry or signal | accepted |
| [G0005](G0005-the-strike-is-an-entity.md) | A melee strike is an entity, not an overlap query. | a grid query for the arc that writes `Damage` directly | accepted |
| [G0006](G0006-a-pickup-is-claimed-then-applied.md) | A pickup is claimed on contact and applied afterwards; exactly one taker, and a side offered nothing leaves it standing. | applying a grant in `onContact`; a `canTake` flag or side mask; removing a claimed pickup | accepted |
| [G0007](G0007-level-state-is-an-entity.md) | State that belongs to the level is an entity. Narrows G0004. | a field on `Simulation`; a `LevelState` object; an event queue; a framework singleton | accepted |
| [G0008](G0008-a-level-may-be-larger-than-the-view.md) | A level may be larger than the view. Amends pillar 4. | capping levels to the view; a per-level-type exception; **a minimap** | accepted |
| [G0009](G0009-the-grid-builds-the-map-and-nothing-else.md) | The tile grid lays a level out; nothing in the game is quantised to it. | obstacles as tiles; spawns snapped to tile centres; tile-to-tile pathing | accepted |
| [G0010](G0010-a-mind-is-a-field-not-a-tree.md) | A unit's mind is a field on a component, not a behaviour tree. | a behaviour tree; an FSM object per unit; one component per state | accepted |
| [G0011](G0011-detection-is-range-only.md) | Detection is range only; rock does not hide you. | line of sight; separate hearing and sight radii | accepted; narrowed by G0015 |
| [G0012](G0012-the-dodge-is-deterministic.md) | A dodge is a function of the shot, not a roll. | a seeded wobble; re-deciding every tick; alternating sides | accepted |
| [G0013](G0013-the-clinch-is-what-a-parry-earns.md) | A melee kill clinches only something already committed to attacking. | a kill that always clinches; giving the sweep the commander's reach | accepted; one rejection superseded by G0014 |
| [G0014](G0014-the-clinch-carries.md) | The clinch carries the body in under its own momentum; it never places it. | placing the body; pulling the commander onto it; mass in the solver | accepted |
| [G0015](G0015-detection-is-a-list-of-tests.md) | Detection is a list of tests a strain declares. Narrows G0011. | a special case in `PerceptionSystem`; line of sight for everything; a `Visibility` component | accepted |
| [G0016](G0016-aggro-latches-a-place.md) | Aggro latches a place, not a unit. | latching an attacker by id; holding an `Entity` across ticks; a threat table | accepted |
| [G0017](G0017-an-order-reaches-who-is-near.md) | An order reaches the troopers near the commander, not the squad. | select-then-order; orders reaching the whole squad; a squad roster | accepted |
| [G0018](G0018-the-blast-is-one-circle.md) | The blast is one circle, and things explode by dying. | damage falloff; a lethal core in a wounding ring; a blast that spares its own side; a blast in the collision path; spawning one directly; a blast that stays lethal while it is drawn | accepted |
| [G0019](G0019-the-grenade-is-a-fixed-throw.md) | A grenade is thrown along the aim direction for a fixed distance. | throwing to the aim point; cooking or charging a throw; contact detonation; a solid grenade; a grenade `WeaponKind` | accepted |
| [G0020](G0020-lethality-belongs-to-the-source.md) | Whether a blow kills or leaves a casualty is declared by the source, not derived from the amount. | a health threshold; a chance to go down; adding or removing a `Casualty` component; a casualty commander; a blast that wounds some and kills others | accepted |
| [G0021](G0021-one-key-and-the-game-chooses.md) | One action key, and what is in reach decides what it does. | an action menu or radial; a key per action; selecting a target first; an action the player can queue | accepted |
| [G0022](G0022-a-barrel-is-a-hazard-with-two-faces.md) | A barrel is steered around, bitten only on a roll, and worth a smaller bang or a charged gun depending on what reached it. | a barrel as an obstacle; an "avoided" flag beside the grants; barrel-eaters decided at spawn; a bite rolled per contact; a blast that spares whoever set it off; a compound grant for the power-up | accepted; amended 2026-09-17 - shunned is about the pair, not the thing |
| [G0023](G0023-the-commander-can-be-picked-up.md) | The commander is a casualty like his men, and a trooper sometimes comes for him. | a trooper that always comes; re-rolling the approach; aid outranking an order or a fight; a second set of revive numbers; a trooper that hauls; a second respawn clock | accepted; supersedes G0020's rejection of a casualty commander |

## When to write one

Only when an alternative was actually rejected **and is likely to be proposed again**. A decision
that nobody would argue with is an invariant - put it in `docs/design/README.md` instead. A
description of how something works is neither; that is the code's job.

Keep the same six fields `docs/adr/README.md` lists: **Trigger**, **Enforced by**, the
**constraint**, its **scope** (especially where it does *not* apply), the **DO NOT BUILD** list with
one reason each, and a **revisit trigger**. Add the rejected names to the table above, so the
negative index stays complete without anyone opening a file.

Prefix each rejected alternative `**No:**`. An item that is deferred rather than rejected stays
unmarked - see G0017's formations item.

Like ADRs, these are not edited as the game evolves - a changed decision gets a new record
superseding the old one.

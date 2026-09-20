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
| [G0024](G0024-a-turret-is-a-place-not-a-kit.md) | A turret is a place you get on, not a gun you pick up. | a turret as a weapon pickup; a vehicle or mount with seats; a crew that stays solid beside the gun; a latched `crewed` flag; a second binding for getting on | accepted; answers G0021's revisit trigger |
| [G0025](G0025-heat-belongs-to-the-gun.md) | Heat belongs to the gun, and coming back costs more than stopping. | heat on the man; resetting it when a crew leaves; drawing it on the `charge` channel; locking until empty; a rising cooldown instead of a stop | accepted |
| [G0026](G0026-an-alien-comes-for-whoever-is-nearest.md) | An alien comes for whoever on the player's side is nearest; the commander is on that list rather than above it. | a threat table; aggro earned by damage; latching a target by id or reference; a flow field per target; finishing a man who is down | accepted; answers the open question about aliens and troopers |
| [G0027](G0027-the-post-is-a-goal-for-the-other-side.md) | The post is a goal the level gives the aliens, not a point they capture. | a post with health; a capture bar the aliens fill; marching handled by wandering or by a scenario flag; a wave schedule on a scenario's own timer | accepted |
| [G0028](G0028-a-player-is-a-slot.md) | A player is a slot; joining is plugging a pad in, and unplugging is not leaving. | a lobby or press-start screen; a `Player` component; removing a commander when his pad disconnects; a per-player squad | accepted |
| [G0029](G0029-a-point-taken-is-a-point-held.md) | A point taken is a point held; "forward" sends the squad on, one point at a time. | a new mode for holding a taken point; spending the forward distance as the permission; holding at an untaken point; a release that lasts until cancelled | accepted |
| [G0030](G0030-a-reinforcement-is-won-where-the-fighting-is.md) | A reinforcement lies beside a control point, becomes a man beside the commander at once, and is wasted past the squad's ceiling. | a reserve that pays out later; leaving the crate standing when the squad is full; a timed or defended arrival; spawning the man where the crate was; a reinforcement granted by the capture itself | accepted; amended 2026-09-18 - a handful of crates per point, three men per crate, one a tick |
| [G0031](G0031-a-nest-is-a-place-that-produces.md) | A nest is a place that produces, and it sleeps until somebody arrives. | a spawner as a timer on the level; a nest that works from the first tick; a nest that goes back to sleep; a cap on its brood; a `NestKind` enum; a second rouse path; reading `Damage` to know it was hit | accepted |
| [G0032](G0032-a-gun-may-have-a-reach.md) | A gun may have a reach, a cone and a pellet count; an aim may not. | a range or clamp on the aim; a range on the unit for the commander; damage falloff; a hitscan short gun; a cone-shaped overlap query; `pellets` on the shooter; expiring a shot by seconds | accepted |
| [G0033](G0033-a-pack-spreads-by-moving-the-goal.md) | A pack spreads by each creature walking at a point beside the man, on a side seeded from where it was spawned. | a separation force or boids; a flow field per creature; rolling the side; slot assignment round the target; an offset that holds all the way in; the numbers on `AvoidanceDef` | accepted |
| [G0034](G0034-a-slick-is-ground-not-an-explosion.md) | A slick is ground with a side and a life, not a blast that lingers. | a blast that lingers; damage that ticks or falls off; a slick on nobody's side; a system that asks whether something is a slick; one big circle instead of a spatter; a slick that finishes whatever is already lying in it | accepted |
| [G0035](G0035-fire-is-a-neutral-slick.md) | Fire is a slick with a different declaration, left by the blast dying: no side, steered around, and it swells and collapses. Narrows G0034. | a `Fire` component and system; a blast that lingers; fire that spares whoever caused it; fire on the flamethrower; a growth shape per explosive; avoidance asking whether something is a slick | accepted; narrows G0034's side rule to a declaration |

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

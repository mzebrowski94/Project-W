# Glossary

Only terms whose meaning is not obvious from the name. Add one in the same commit that introduces
it. Terms marked *(source)* come from *Triumph! War 2099*.

| Term | Meaning |
|---|---|
| **commander** *(source)* | The unit the player directly controls. |
| **squad** *(source)* | The group of troopers following a commander. |
| **trooper** | One AI-controlled member of a squad. |
| **level** *(source)* | One self-contained combat scenario, start to objective. |
| **projectile** | A fired entity that travels and can hit something. Always simulated; there is no hitscan. |
| **faction** | Which side an entity is on. Damage only ever crosses sides. Scenery is on nobody's. |
| **recovery** | The window after a staggering hit during which a unit takes no further contact damage. |
| **leap** | A unit throwing itself at the commander to close the last of the distance. Committed, telegraphed, and aimed once at launch - never steered. |
| **hesitation** | The pause a unit takes before the first leap of a sequence. The tell: the only part of a leap the player gets to answer. Drawn from the seeded `Rng`, so it varies between leaps and not between runs. |
| **strike** | One swing of a melee weapon, as an entity in the world with a body that sweeps an arc (G0005). Never an overlap query. |
| **stun** | A window during which an entity cannot act - no control, no chase, no leap, no blow. It can still be moved and still be shoved; that is what lets knockback exist. Distinct from *recovery*, which is about damage rather than agency. |
| **lock** | The stun a *fatal* blow puts on both parties. What turns a kill into an exchange rather than a deletion. |
| **stagger** | The stun a *survived* blow puts on the one that took it, and only on that one. A shove, not a clinch: whoever landed it keeps fighting. |
| **knockback** | The velocity a landed blow gives a target **that survives it**, bleeding off over the stun. A blow that kills throws nothing - the body stops where it was struck. |
| **clinch** | How a lethal exchange ends: the body stops dead, becomes immovable, and the solver settles the survivor against it, so the two finish touching. Not a mechanic of its own - it falls out of the reach, the no-throw-on-kill rule and the corpse being scenery. |
| **death throes** | The window between a killing blow and the body being removed. Every unit has them. The body is scenery for their duration - solid, immovable, and it stops bullets. Where the dying animation will play; a commander's respawn countdown starts after them, not during. |
| **brute** | A heavy enemy: slower, tougher, and able to survive a hand strike. Carries everything an ordinary enemy carries, so only spawning and drawing tell them apart. |
| **obstacle** | A static entity that blocks movement and projectiles. An entity like any other, not terrain data. |
| **pickup** | Anything lying on the field that the first thing to touch it takes. Not the player's alone: what it does depends on which side took it, and one item can be a weapon to the commander and a meal to what is chasing him. |
| **grant** | What a pickup gives one side for taking it. Every pickup carries two - one per side - and either may be nothing, which is what makes that side walk over it and leave it. |
| **strain** | The form a unit currently has. Grunt, brute and whatever they grow into are strains of one thing, not different classes of entity: a strain is an enum on a component and a table of numbers, and changing it is a field write. |
| **grunt** | The basic enemy strain. *Enemy* stays the umbrella word for everything on the alien side; *grunt* is the specific creature. |
| **evolve** | A unit changing strain. One way, and it arrives at the new strain's full health - a unit that has just become something else arrives whole. |
| **feed** | What a unit has eaten towards its next evolution, as a count. Crossing its strain's threshold evolves it; an item that names a strain outright skips the count entirely. |
| **charge** | How full something is, as a fraction the simulation publishes for the renderer - a unit part-way to evolving, and later anything else that fills up. One tell, drawn the same way wherever it appears. |
| **frozen trooper** | Alpha-phase scaffolding: an inert body on the field, a reinforcement to the commander and a mouthful to an alien. **Not a trooper** - the squad does not exist yet, and nothing about this answers whether it will work this way when it does. |
| **the level entity** | One entity, spawned with the world and never removed, carrying state that belongs to the level rather than to anything standing in it (G0007). |
| **reinforcement** *(source)* | A pickup that adds troopers to the squad. Until the squad exists, one lands on the level entity and is counted there. |
| **tick** | One fixed simulation step, `SimConfig.tickSeconds()` long. Never a frame. |
| **frame** | One rendered image. Zero or more ticks happen per frame. Never a tick. |
| **alpha** | Fraction of a tick elapsed, used to interpolate rendering between ticks. |
| **world unit** | The simulation's unit of length. Never pixels. |
| **arena** | The rectangle a level is played inside. Not a body - it is handled by clamping, not by a collision test. It is exactly the map's own extent; there is no separate arena size. |
| **tile** | One cell of the grid a level is laid out on. A unit of *construction*, not of play: nothing moves tile by tile and nothing is centred in one (G0009). |
| **terrain** | The kind of ground on a tile. Names a role, like a `Look` does - what it looks like is the client's decision and what it costs a unit is a number in `game.properties`. Whether it can be stood on is the one exception, because generation needs it. |
| **region** | A contiguous area of a map, decided before its tiles are filled in. What makes a level read as places rather than one flat sheet: regions are shaded subtly apart, and later decide what terrain and what contents belong where. |
| **focus** | A marker saying the view must keep this entity in shot. The simulation says who matters and stops there; where the camera goes is entirely the client's. |
| **fragment** | A piece of map that generation produces and assembles with others into a level. Not built yet; the grid exists to make it cheap. |
| **scenario** | One arena the alpha-phase menu can launch, built to make a system visible. Content, never design. |
| **contact** | An overlapping pair, reported once per tick by the constraint solver. What it *means* is the collision system's business. |
| **aggro** | Whether a unit is hunting the commander. One latched boolean, and the whole of a unit's mind: it notices him at its detection range and gives up at its lose range, which is a longer one. |
| **detection range** | How close the commander must come before an unaware unit notices him. |
| **lose range** | How far he must get before a hunting unit gives up. Never shorter than the detection range - one number for both would flicker a unit standing exactly on the boundary between hunting and idling, every tick. |
| **patch** | The circle of ground an idle unit ambles around, centred where it was spawned. Losing the commander therefore means walking home, which is what makes a disengagement read as one. Set once, at the spawn: a grunt that becomes a brute keeps the grunt's patch. |
| **desired velocity** | Where a unit wants to be going this tick, before anything decides whether it may. Written by whatever is driving it, committed to a velocity by one system, and spent on the tick it was written - a unit nothing spoke for asks for nothing and stops. |
| **flow field** | A direction per tile, pointing the way to one goal, rebuilt when the goal leaves its tile. One grid pass serves every unit that reads it, however many there are - the alternative is a path search each and a queue walking in single file. |
| **clearance** | Whether a body of a given size fits on a tile. Baked into a flow field rather than checked afterwards, which is what makes a one-tile crack a route for a grunt and a wall for a brute without anybody writing a rule about it. |
| **stream** | One seeded generator per concern, all derived from the level seed. Sharing one sequence means re-tuning how often anything draws a number reshuffles every number every other system gets - reproducible, and miserable to iterate on. |
| **intent** | What a commander is trying to do this tick, as a component. Written from input, read by systems. |

## Words we do not use

- *unit* alone - ambiguous between trooper, entity and world unit. Say which.
- *type* or *class* for what a creature is - say strain. *Kind* is fine for the enum in code
  (`UnitKind`, `PickupKind`), but prose says strain.
- *item*, *collectable*, *powerup* - say pickup.
- *mutation*, *transformation* - say evolve.
- *object*, *actor*, *thing* - say entity.
- *update* for a simulation step - say tick.
- *create* or *make* for bringing an entity into the world - say spawn.

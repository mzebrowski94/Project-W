# Glossary

Only terms whose meaning is not obvious from the name. Add one in the same commit that introduces
it. Terms marked *(source)* come from *Triumph! War 2099*.

Grouped by subsystem, alphabetical within each group. The grouping is for **spotting a word we already
have**: near-duplicates always come from the same subsystem, and one concept gets one word everywhere.
Before adding a term, read its section end to end - not just search for the name you had in mind.

A term that could sit in two sections goes where a reader would look for it, not where the code puts it:
*dodge* is under Fighting although `DodgeSystem` is part of the steering pipeline.

### Units and sides

| Term | Meaning |
|---|---|
| **brute** | A heavy enemy: slower, tougher, and able to survive a hand strike. Carries everything an ordinary enemy carries, so only spawning and drawing tell them apart. |
| **commander** *(source)* | The unit the player directly controls. |
| **faction** | Which side an entity is on. Damage only ever crosses sides. Scenery is on nobody's. |
| **grunt** | The basic enemy strain. *Enemy* stays the umbrella word for everything on the alien side; *grunt* is the specific creature. |
| **obstacle** | A static entity that blocks movement and projectiles. An entity like any other, not terrain data. |
| **squad** *(source)* | The group of troopers following a commander. |
| **strain** | The form a unit currently has. Grunt, brute and whatever they grow into are strains of one thing, not different classes of entity: a strain is an enum on a component and a table of numbers, and changing it is a field write. |
| **trooper** | One AI-controlled member of a squad. |

### Growth and pickups

| Term | Meaning |
|---|---|
| **barrel** | An explosive barrel: a pickup with nothing on offer to anybody. The commander is offered nothing and leaves it standing; what an alien gets for biting into one is the blast. A pickup rather than an obstacle on purpose - avoidance steers a unit round anything solid *except* a pickup, so as an obstacle it would be the thing the aliens carefully walked around. |
| **charge** | How full something is, as a fraction the simulation publishes for the renderer - a unit part-way to evolving, and later anything else that fills up. One tell, drawn the same way wherever it appears. |
| **evolve** | A unit changing strain. One way, and it arrives at the new strain's full health - a unit that has just become something else arrives whole. |
| **feed** | What a unit has eaten towards its next evolution, as a count. Crossing its strain's threshold evolves it; an item that names a strain outright skips the count entirely. |
| **grant** | What a pickup gives one side for taking it. Every pickup carries two - one per side - and either may be nothing, which is what makes that side walk over it and leave it. |
| **pickup** | Anything lying on the field that the first thing to touch it takes. Not the player's alone: what it does depends on which side took it, and one item can be a weapon to the commander and a meal to what is chasing him. |
| **rabbit** | Alien food that runs away. A pickup in every way that matters - claimed on contact, granted from the same table - but it walks, it dodges, and it can be shot. The commander is offered nothing by one, so his only interest is denying it. |
| **reinforcement** *(source)* | A pickup that adds troopers to the squad. Until the squad exists, one lands on the level entity and is counted there. |

### Fighting

| Term | Meaning |
|---|---|
| **blast** | The circle of ground an explosion makes lethal, for a moment. One circle, uniformly fatal inside it and harmless outside it, on nobody's side, and stopped by rock (G0018). It catches what is standing in it on the tick it appears and nothing that walks in afterwards, so the rest of it is only the flash. Everything that explodes does so by **dying**, and a blast is what the death leaves. |
| **clinch** | How a lethal exchange ends: both parties stopped dead and held still, the body immovable, and the last stride closed so the two finish touching. It carries no knockback decay - a killing blow throws nothing, so there is nothing to bleed off, and carrying one let the survivor coast out from under the body. A melee kill earns it only against something already committed to attacking; a swat on something walking is not a clinch and costs the commander nothing (G0013). |
| **contact** | An overlapping pair, reported once per tick by the constraint solver. What it *means* is the collision system's business. |
| **death throes** | The window between a killing blow and the body being removed. Every unit has them. The body is scenery for their duration - solid, immovable, and it stops bullets. Where the dying animation will play; a commander's respawn countdown starts after them, not during. |
| **dodge** | A sidestep away from an incoming shot. Perpendicular to the shot, to the side the creature is already on, the same every time, so it can be learned and led (G0012). Bounded by a commit window, which is what makes the answer to a dodge a second shot. |
| **grenade** | What the commander throws. It travels along his aim **direction** for a fixed distance, comes off rock and scenery without losing speed, passes through anything alive, and leaves a blast where the distance runs out (G0019). The reach is the same every throw, so a wall changes where one ends up rather than how far it went. |
| **hesitation** | The pause a unit takes before the first leap of a sequence. The tell: the only part of a leap the player gets to answer. Drawn from the seeded `Rng`, so it varies between leaps and not between runs. |
| **knockback** | The velocity a landed blow gives a target **that survives it**, bleeding off over the stun. A blow that kills throws nothing - the body stops where it was struck. |
| **leap** | A unit throwing itself at the commander to close the last of the distance. Committed, telegraphed, and aimed once at launch - never steered. |
| **lock** | The stun a *fatal* blow puts on both parties. What turns a kill into an exchange rather than a deletion. |
| **projectile** | A fired entity that travels and can hit something. Always simulated; there is no hitscan. |
| **recovery** | The window after a staggering hit during which a unit takes no further contact damage. |
| **stagger** | The stun a *survived* blow puts on the one that took it, and only on that one. A shove, not a clinch: whoever landed it keeps fighting. |
| **strike** | One swing of a melee weapon, as an entity in the world with a body that sweeps an arc (G0005). Never an overlap query. |
| **stun** | A window during which an entity cannot act - no control, no chase, no leap, no blow. It can still be moved and still be shoved; that is what lets knockback exist. Distinct from *recovery*, which is about damage rather than agency. |

### Driving a unit

| Term | Meaning |
|---|---|
| **aggro** | Whether a unit is hunting the commander. One latched boolean, and the whole of a unit's mind. It notices him at its detection range and, if its strain loses interest at all, gives up past its lose range. Him going down releases it regardless: nothing stands to attention over a corpse. |
| **avoidance** | Steering around what a unit is about to hit, decided before it hits it. Context steering: a ring of candidate directions scored by how much the unit wanted to go that way against how dangerous that way is. It never avoids the other side, which is what it is trying to reach, and never a pickup, which is a thing you walk over. |
| **clearance** | Whether a body of a given size fits on a tile. Baked into a flow field rather than checked afterwards, which is what makes a one-tile crack a route for a grunt and a wall for a brute without anybody writing a rule about it. |
| **desired velocity** | Where a unit wants to be going this tick, before anything decides whether it may. Written by whatever is driving it, committed to a velocity by one system, and spent on the tick it was written - a unit nothing spoke for asks for nothing and stops. |
| **detection range** | How close the commander must come before an unaware unit notices him. |
| **facing** | Which way a thing is looking, published as a unit vector for the renderer. The commander's is his aim and never his heading (G0002); a trooper's is a decision of its own, so it can stand still and go on watching. Anything that does not steer itself has none. |
| **flow field** | A direction per tile, pointing the way to one goal, rebuilt when the goal leaves its tile. One grid pass serves every unit that reads it, however many there are - the alternative is a path search each and a queue walking in single file. |
| **forage** | An idle unit going to eat something it has noticed. Only ever reached by a unit that has not noticed the commander - he outranks food absolutely - which is why the secondary range is the shortest range in the game. |
| **intent** | What a commander is trying to do this tick, as a component. Written from input, read by systems. |
| **lose range** | How far he must get before a hunting unit gives up. Never shorter than the detection range - one number for both would flicker a unit standing exactly on the boundary between hunting and idling, every tick. |
| **patch** | The circle of ground an idle unit ambles around. Centred wherever it last had nothing to do: the anchor is dragged along behind anything hunting or feeding, so the moment a unit gives up it is already home. Anchoring to the spawn instead sent a unit that broke off a chase back across the level, which read as the level rewinding. |
| **scan** | A trooper sweeping its attention from side to side as it advances. The trooper's answer to *forage*: what it does with its attention while nothing is happening, and the reason its facing is worth drawing. |
| **secondary range** | How close something edible has to be before an idle unit goes for it. Shorter than the detection range on purpose: this is what a unit does with its attention when it has nothing else to do with it. |

### The commander's orders

| Term | Meaning |
|---|---|
| **earshot** | How far the commander's voice carries. The whole of who hears an order: there is no selection and no roster, so where he is standing is what decides. |
| **order** | One thing the commander tells the troopers near him to do, as a standing instruction on each of them. Reaches only what is within earshot when it is given (G0017), outranks the level's objectives, and never interrupts a fight - which needs no queue, because nothing consumes it. **His death drops every standing order**, and none of them come back with him. |
| **urgency** | How much a trooper's advance beats its scanning, as a fraction. Deliberately **not** a speed - "move out" is what changes speed, and one dial doing both would make that order invisible. It is the number that sets the game's pace. |

### The map and the view

| Term | Meaning |
|---|---|
| **arena** | The rectangle a level is played inside. Not a body - it is handled by clamping, not by a collision test. It is exactly the map's own extent; there is no separate arena size. |
| **focus** | A marker saying the view must keep this entity in shot. The simulation says who matters and stops there; where the camera goes is entirely the client's. |
| **fragment** | A piece of map that generation produces and assembles with others into a level. Not built yet; the grid exists to make it cheap. |
| **region** | A contiguous area of a map, decided before its tiles are filled in. What makes a level read as places rather than one flat sheet: regions are shaded subtly apart, and later decide what terrain and what contents belong where. |
| **shake** | Something violent that happened somewhere, published for the view: how hard at the middle of it, how far it is felt, and how far through it is. The simulation says what happened and stops there - what the view does about it is entirely the client's, exactly as *focus* says who matters and not where the camera goes. |
| **spawn area** | The patch of tiles a side is put on at the start of a level. A rectangle of tiles with everyone jittered inside them, never at a tile centre (G0009) - the same tile-anchored scatter that places everything else. |
| **terrain** | The kind of ground on a tile. Names a role, like a `Look` does - what it looks like is the client's decision and what it costs a unit is a number in `game.properties`. Whether it can be stood on is the one exception, because generation needs it. |
| **tile** | One cell of the grid a level is laid out on. A unit of *construction*, not of play: nothing moves tile by tile and nothing is centred in one (G0009). |

### Levels and objectives

| Term | Meaning |
|---|---|
| **control point** | Ground a squad takes by standing near it for a while. Captured by dwelling; the dwell goes back to zero the moment the last capturer leaves, rather than pausing, and starting an attempt wakes what is nearby. |
| **level** *(source)* | One self-contained combat scenario, start to objective. |
| **objective** | A condition a level requires before it counts as done. A level names a set and requires all of it. Two exist: clear the map of enemies, and capture every control point. What a completed level *does* is still open - in the alpha it does nothing. |
| **scenario** | One arena the alpha-phase menu can launch, built to make a system visible. Content, never design. |
| **the level entity** | One entity, spawned with the world and never removed, carrying state that belongs to the level rather than to anything standing in it (G0007). |

### Time, space and randomness

| Term | Meaning |
|---|---|
| **alpha** | Fraction of a tick elapsed, used to interpolate rendering between ticks. |
| **frame** | One rendered image. Zero or more ticks happen per frame. Never a tick. |
| **stream** | One seeded generator per concern, all derived from the level seed. Sharing one sequence means re-tuning how often anything draws a number reshuffles every number every other system gets - reproducible, and miserable to iterate on. |
| **tick** | One fixed simulation step, `SimConfig.tickSeconds()` long. Never a frame. |
| **world unit** | The simulation's unit of length. Never pixels. |

### Alpha-phase scaffolding

| Term | Meaning |
|---|---|
| **frozen trooper** | Alpha-phase scaffolding: an inert body on the field, a reinforcement to the commander and a mouthful to an alien. **Not a trooper** - the squad does not exist yet, and nothing about this answers whether it will work this way when it does. |

## Words we do not use

- *unit* alone - ambiguous between trooper, entity and world unit. Say which.
- *type* or *class* for what a creature is - say strain. *Kind* is fine for the enum in code
  (`UnitKind`, `PickupKind`), but prose says strain.
- *item*, *collectable*, *powerup* - say pickup.
- *mutation*, *transformation* - say evolve.
- *object*, *actor*, *thing* - say entity.
- *update* for a simulation step - say tick.
- *create* or *make* for bringing an entity into the world - say spawn.

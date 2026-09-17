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
| **spitter** | The shooting strain: purple, fragile, and it holds its ground at a distance instead of closing. A different problem from the two above - one you have to go and solve rather than one that comes to you. |
| **squad** *(source)* | The group of troopers following a commander. |
| **strain** | The form a unit currently has. Grunt, brute and whatever they grow into are strains of one thing, not different classes of entity: a strain is an enum on a component and a table of numbers, and changing it is a field write. |
| **trooper** | One AI-controlled member of a squad. |

### Growth and pickups

| Term | Meaning |
|---|---|
| **barrel** | An explosive barrel: a pickup the commander is offered nothing by and leaves standing. The one **shunned** pickup, so the aliens steer round it and only **bite** into one on a roll. What one is worth to them depends on what reached it: something that bites sets it off in its own face, and something that can **swallow** it comes away with a **charged** gun instead (G0022). |
| **bite** | A creature setting off something it should have left alone. Rolled while it is actually touching the thing, so avoidance keeping it away is most of what makes a bite rare - and the blast is far smaller than a shot's, because the biter had its mouth on it. |
| **charge** | How full something is, as a fraction the simulation publishes for the renderer - a unit part-way to evolving, a control point being taken, a man being put back on his feet. One tell, drawn the same way wherever it appears; the one that is good news names a colour of its own so it cannot be mistaken for a clock running out. |
| **charged** | Carrying a barrel rather than having been blown up by one. A strain that names a charged gun **swallows** an explosive it reaches and fires shots that go off where they land (G0022). |
| **evolve** | A unit changing strain. One way, and it arrives at the new strain's full health - a unit that has just become something else arrives whole. |
| **feed** | What a unit has eaten towards its next evolution, as a count. Crossing its strain's threshold evolves it; an item that names a strain outright skips the count entirely. |
| **grant** | What a pickup gives one side for taking it. Every pickup carries two - one per side - and either may be nothing, which is what makes that side walk over it and leave it. |
| **pickup** | Anything lying on the field that the first thing to touch it takes. Not the player's alone: what it does depends on which side took it, and one item can be a weapon to the commander and a meal to what is chasing him. |
| **shunned** | A pickup that is steered around rather than walked over, because what it hands whoever takes it is an explosion. Derived from the grant rather than declared beside it, so the two can never disagree. The barrel is the only one - and it is shunned **by whatever cannot use one**: anything that can **swallow** an explosive crosses the ground for it like any other food (G0022). |
| **swallow** | Taking an explosive whole instead of setting it off. No blast at all: the creature is carrying it now. |
| **rabbit** | Alien food that runs away. A pickup in every way that matters - claimed on contact, granted from the same table - but it walks, it dodges, and it can be shot. The commander is offered nothing by one, so his only interest is denying it. |
| **reinforcement** *(source)* | A pickup that adds troopers to the squad. Until the squad exists, one lands on the level entity and is counted there. |

### Fighting

| Term | Meaning |
|---|---|
| **blast** | The circle of ground an explosion makes lethal, for a moment. One circle, uniformly fatal inside it and harmless outside it, on nobody's side, and stopped by rock (G0018). It catches what is standing in it on the tick it appears and nothing that walks in afterwards, so the rest of it is only the flash. Everything that explodes does so by **dying**, and a blast is what the death leaves. |
| **bleed-out** | The window a casualty has before he is gone. It stops while he is being hauled, so the ground you cross carrying him is free; what it costs is the work at the far end. Published as a fraction on the `charge` channel, because a rescue the player cannot time is a rescue he cannot decide to attempt. |
| **casualty** | A man shot or blasted off his feet but not dead: he lies where he fell with a **bleed-out** running, and somebody who reaches him in time can **stabilise** him. He is at zero health, so the next hit of any kind finishes him - being shot while down and being eaten while down are the same rule rather than two. **The commander can be one**, now that the squad comes for him (G0023). Nothing on the alien side can, because nothing on that side would come and get it (G0020). Distinct from *going down*, which everywhere else in this codebase means dying. |
| **clinch** | How a lethal exchange ends: both parties stopped dead and held still, the body immovable, and the last stride closed so the two finish touching. It carries no knockback decay - a killing blow throws nothing, so there is nothing to bleed off, and carrying one let the survivor coast out from under the body. A melee kill earns it only against something already committed to attacking; a swat on something walking is not a clinch and costs the commander nothing (G0013). |
| **contact** | An overlapping pair, reported once per tick by the constraint solver. What it *means* is the collision system's business. |
| **decisive** | What a blow is when it empties a health bar. Whether that kills outright or leaves a **casualty** is declared by the source rather than derived from the amount: gunfire and blasts leave casualties, claws and sweeps and leaps kill (G0020). Fatal wins when two hits combine. |
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
| **spit** | What a spitter fires. Slow in the air on purpose, and thrown inside a narrow cone rather than along a line - so a shot meant for the commander reaches the men standing beside him, which is most of what makes a firing line hurt a squad. |
| **attack arc** | How wide an arc in front of a creature its claws reach into, centred on the way it is going. Below all round on purpose: walking into the back of something that had not turned round used to kill outright, which read as the game cheating rather than as a mistake. Generous, though - a lunging creature is not precise, and something standing still reaches all round because it can turn on the spot. |
| **spread** | The width of the cone a shot may leave in, centred on the aim. Applied by turning the shot, never by nudging where it was aimed: turning keeps every shot the same length and the cone the same width at every distance. It **settles**: the first shot is the wild one. |
| **settle** | A shooter walking its aim in. Every shot it lands on the same target narrows its **spread** towards the floor its strain declares, and being close narrows it further and shortens the wait. Losing the target throws all of it away, which is why breaking a shooter's line beats trading with it. |
| **stabilise** | Putting a casualty back on his feet. It needs somebody within arm's length and it takes time standing still; the commander holds his action key for it, and a trooper does it off its own judgement (G0023). Letting go loses the work rather than banking it. He gets up hurt and briefly protected, and a man who has been **hauled** takes longer. |
| **strike** | One swing of a melee weapon, as an entity in the world with a body that sweeps an arc (G0005). Never an overlap query. |
| **stun** | A window during which an entity cannot act - no control, no chase, no leap, no blow. It can still be moved and still be shoved; that is what lets knockback exist. Distinct from *recovery*, which is about damage rather than agency. |

### Driving a unit

| Term | Meaning |
|---|---|
| **aggro** | Whether a unit is hunting the commander. One latched boolean, and the whole of a unit's mind. It notices him at its detection range and, if its strain loses interest at all, gives up past its lose range. Him going down releases it regardless: nothing stands to attention over a corpse. |
| **aid** | A trooper leaving what it was doing to go and **stabilise** somebody. Ranked below a standing order and above the level's objectives, and taken on a **chance** rather than always (G0023). |
| **avoidance** | Steering around what a unit is about to hit, decided before it hits it. Context steering: a ring of candidate directions scored by how much the unit wanted to go that way against how dangerous that way is. It never avoids the other side, which is what it is trying to reach, and never a pickup it wants - only a **shunned** one gets room. |
| **chance** | How often a unit does something it only sometimes does, as a probability per second of opportunity. Every one in the game is a constant in `AiChance` and a line in `game.properties`, so the conditional behaviour can be read off one list instead of found by searching the systems. Zero switches one off and draws no number at all. |
| **clearance** | Whether a body of a given size fits on a tile. Baked into a flow field rather than checked afterwards, which is what makes a one-tile crack a route for a grunt and a wall for a brute without anybody writing a rule about it. |
| **desired velocity** | Where a unit wants to be going this tick, before anything decides whether it may. Written by whatever is driving it, committed to a velocity by one system, and spent on the tick it was written - a unit nothing spoke for asks for nothing and stops. |
| **detection range** | How close the commander must come before an unaware unit notices him. |
| **field of view** | The arc a shooter will fire into, centred on where it is looking. Wider than a trooper's, so a creature built around a gun is harder to get behind than a man - and its aim **swings** onto a target at a rate rather than snapping, so stepping round one buys a real moment. |
| **swing** | A shooter bringing its aim round onto what it is hunting. A rate, not a snap: it is what turns a **field of view** from a number into something the player can use. |
| **hold ground** | A shooter that moves for somewhere to be and never for somebody to follow. It comes when summoned - a provocation is a place rather than a target, so taking a point off one brings it over - and it crosses the ground between noticing you and being able to fight you, once. From the moment it is **in position** it holds that ground however far you go, so you are driven off rather than chased. |
| **in position** | A shooter that has reached the distance it wanted. It outlives being in range on purpose: stepping out of its reach costs it the aim it had **settled**, and only losing it altogether lets it move up again. |
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
| **defence** | The order to hold here: stationary, still watching. Renamed from *stop* on 2026-09-13 - what a squad does when it stops is hold a place, and the verb should say so. |
| **forward** | The order to push on: a burst of pace towards the objective, for a short distance rather than a countdown. Renamed from *move out* on 2026-09-13. |
| **order** | One thing the commander tells the troopers near him to do, as a standing instruction on each of them. Reaches only what is within earshot when it is given (G0017), outranks the level's objectives, and never interrupts a fight - which needs no queue, because nothing consumes it. **His death drops every standing order**, and none of them come back with him. |
| **urgency** | How much a trooper's advance beats its scanning, as a fraction. Deliberately **not** a speed - "forward" is what changes speed, and one dial doing both would make that order invisible. It is the number that sets the game's pace. |

### What the commander does with his hands

| Term | Meaning |
|---|---|
| **action** | Something the commander does to whatever is within arm's length, on one key. He never picks from a list: what is in reach is what is on offer, nearest first, and the simulation publishes which kind it is for the interface to show (G0021). |
| **haul** | To drag a load - a casualty or a barrel - along behind you. It costs almost everything: he backs away and cannot press forwards, keeps only a fraction of his pace and a little sideways lean, and has no hand free for a weapon. **Hauled** latches on the load, because what it buys is paid for after he puts it down. |
| **load** | Whatever is being hauled. Named by an issued id on both ends rather than by an entity reference, so a drag that outlives what it was holding can never come back attached to something else (G0010). |
| **reach** | How close something must be, surface to surface, for the commander to do anything with it. One number for every action there is, so there is one distance to learn. |

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

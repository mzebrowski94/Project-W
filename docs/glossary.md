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
| **pickup** | A collectable that changes the player's state - weapon, health, reinforcement. |
| **reinforcement** *(source)* | A pickup that adds troopers to the squad. |
| **tick** | One fixed simulation step, `SimConfig.tickSeconds()` long. Never a frame. |
| **frame** | One rendered image. Zero or more ticks happen per frame. Never a tick. |
| **alpha** | Fraction of a tick elapsed, used to interpolate rendering between ticks. |
| **world unit** | The simulation's unit of length. Never pixels. |
| **arena** | The rectangle a level is played inside. Not a body - it is handled by clamping, not by a collision test. |
| **scenario** | One arena the alpha-phase menu can launch, built to make a system visible. Content, never design. |
| **contact** | An overlapping pair, reported once per tick by the constraint solver. What it *means* is the collision system's business. |
| **intent** | What a commander is trying to do this tick, as a component. Written from input, read by systems. |

## Words we do not use

- *unit* alone - ambiguous between trooper, entity and world unit. Say which.
- *object*, *actor*, *thing* - say entity.
- *update* for a simulation step - say tick.
- *create* or *make* for bringing an entity into the world - say spawn.

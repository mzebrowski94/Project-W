# Glossary

Only terms whose meaning is not obvious from the name. Add one in the same commit that introduces
it. Terms marked *(source)* come from *Triumph! War 2099*.

| Term | Meaning |
|---|---|
| **commander** *(source)* | The unit the player directly controls. |
| **squad** *(source)* | The group of troopers following a commander. |
| **trooper** | One AI-controlled member of a squad. |
| **level** *(source)* | One self-contained combat scenario, start to objective. |
| **pickup** | A collectable that changes the player's state - weapon, health, reinforcement. |
| **reinforcement** *(source)* | A pickup that adds troopers to the squad. |
| **tick** | One fixed simulation step, `SimConfig.tickSeconds()` long. Never a frame. |
| **frame** | One rendered image. Zero or more ticks happen per frame. Never a tick. |
| **alpha** | Fraction of a tick elapsed, used to interpolate rendering between ticks. |
| **world unit** | The simulation's unit of length. Never pixels. |

## Words we do not use

- *unit* alone - ambiguous between trooper, entity and world unit. Say which.
- *object*, *actor*, *thing* - say entity.
- *update* for a simulation step - say tick.
- *create* or *make* for bringing an entity into the world - say spawn.

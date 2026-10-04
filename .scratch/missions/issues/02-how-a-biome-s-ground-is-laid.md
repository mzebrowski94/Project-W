# How a biome's ground is laid

Type: prototype
Status: open

## Question

Which technique lays one biome's ground from a seed - base terrain, patches of a neighbouring ground,
rock features of a given density - so it reads like the hand-painted Adventure biomes (plain, quarry,
brake) and can never seal its own gaps? Run `game-patterns` first. Prototype: generate the three from a
handful of seeds, render each to an image, and react to them against `AdventureScenario`'s versions.
Settles the fields the ground part of a biome definition needs.

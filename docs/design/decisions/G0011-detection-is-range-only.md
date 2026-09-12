# G0011: Detection is range only; rock does not hide you

**Trigger.** Touching perception; proposing line of sight. Narrowed by G0015 - read that too.

**Enforced by.** Nothing mechanical - caught by reading.

**Constraint.** A unit notices the commander by distance alone. Terrain, obstacles and other units do
not block detection, and there is no line-of-sight test anywhere in perception.

**Scope.** Noticing. It says nothing about *reaching* him: routing is a flow field over the terrain,
so a unit that has heard you through a wall still has to walk round it, and in a level built out of
corridors that is most of the drama. It also says nothing about what a future unit type might do - a
strain whose whole idea is that it only reacts to what it can see is a strain with its own rule, not
a change to this one.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Line of sight.* The obvious realism, and it will be proposed the first time a player watches
something wake up on the far side of a wall. Rejected for three reasons, in order of weight. It turns
every wall into a place where standing still is safe, which is a stealth mechanic in a game whose
first pillar is that fights are decided by reflexes. It makes what a unit will do unreadable from
where it is standing, because the player would have to model the raycast to predict it. And it is a
ray per unit per tick against the tile grid, for an answer the player cannot see being computed.

**No:** *A hearing radius separate from a sight radius.* Two ranges that differ only in whether rock stops
them. It is the same decision with more numbers, and it needs line of sight to exist before it means
anything.

**Revisit when** cover becomes a mechanic the player is asked to use deliberately rather than a shape
in the level - a stance, an ability, a strain built around being unseen. Detection lives in one system
reading two numbers off a component, so adding a rule to it is contained.

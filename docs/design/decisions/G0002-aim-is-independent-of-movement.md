# G0002: Aim is independent of movement

**Status.** accepted

**Trigger.** Touching facing, aim or input; proposing a gamepad-only or touch build.

**Enforced by.** Nothing mechanical - caught by reading.

**Constraint.** Where a unit is pointed is set by the aim input, not by the movement input. Moving
one way while firing another is always available, and no mechanic may assume that the direction a
unit moves is the direction it faces.

**Scope.** Stated for the commander, but it binds every system that touches facing: a trooper
walking to a position while shooting something behind it is the same rule, not an exception to it.
It says nothing about *how* aim is expressed - a world point or a direction is an implementation
choice inside the input value object, and either satisfies this.

**DO NOT BUILD** - rejected, and likely to be re-proposed. Facing follows movement. One input instead of two, no
mouse dependency, and closer to how a 2001 game would have done it - it will come back the first
time a gamepad-only or touch build is discussed, and it will look like a small simplification. It
is not. It removes the retreat-while-firing decision, and that decision is most of what makes a
top-down fight tactical rather than a race to whoever has more health. Pillar 1 asks for fights
decided by reflexes; with facing locked to movement there is much less for reflexes to decide.

**Revisit when** the game is genuinely targeted at a device with one stick and no pointer. Adding a
gamepad alongside mouse and keyboard is not that trigger - a right stick aims.

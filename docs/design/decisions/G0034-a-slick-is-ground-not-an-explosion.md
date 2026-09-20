# G0034: A slick is ground, not an explosion

**Trigger.** Adding or changing anything that makes a piece of the field dangerous to stand on - a
lingering hazard, a gas, a fire, a mine - or proposing that a blast linger, spare a side, or fall
off with distance. G0018 is the other half of this and answers the same question for explosions.

**Enforced by.** `SlickTest.leavesTheSideThatMadeItAlone`,
`.staysOnTheGroundLongAfterTheShotThatMadeIt`, `.cleansTheGroundOnceItHasHadItsTime`,
`.putsAManStandingInItOnTheGroundRatherThanKillingHim`, `.ruinsTheGroundWhereItsShotBurst`,
`.leavesNothingInsideRock`.

**Constraint.** A slick is one circle of ruined ground: an ordinary entity with a `Position`, a
`Body`, a `Collider` and a `ContactDamage`, non-solid and immovable, carrying the **faction of
whatever fired the shot**. It catches whatever walks into it for as long as it lasts, its blow is the
wounding kind, and the circle that hurts is the entity's own body, which is the circle the renderer
draws. A burst leaves several overlapping ones - a spatter - and the shape of that is a number on the
gun.

It arrives the way everything that leaves something behind arrives: the shot carries
`SlicksOnDeath`, and one system lays the spatter where the shot died (G0018's death reaction, asked
of a different reaction). A shot that burst on a wall, one that burst on a man and one that simply ran
out of flight all get there the same way.

**Scope.** Anything that ruins ground. It says nothing about how big or how long-lived any particular
spatter is - the venom gun names its own - and nothing about what one looks like beyond the body
being the drawing. It does not touch G0018: a blast is still one circle, still on nobody's side, and
still dangerous for exactly one tick.

**Why it is a second kind of thing rather than a longer blast.** G0018's own revisit trigger says so:
the first thing that shape genuinely cannot express is an explosion that is not fatal, and the answer
then is a second kind rather than a curve added to the first. This is that second kind, and keeping
it separate is what leaves every blast rule intact.

**Three of G0018's rejections look like they apply here, and each is answered by the thing being
ground rather than an explosion.**

*It is in the collision path, which G0018 refused.* That refusal was arithmetic, not principle: the
collision grid's cell size comes from the widest body in the game, and a twelve-unit blast would have
tripled it to serve a quarter of a second. A slick is about two units across - smaller than a brute -
so it costs the grid nothing, and being an ordinary body is what lets it borrow the contact rule, the
faction rule and the casualty rule without a line of new logic. `SimConfig.largestRadius` measures it
through the gun table, so a slick widened past a brute would be caught by the cell size rather than
by somebody noticing.

*It keeps hurting for as long as it is drawn, which G0018 refused.* That refusal was about a tell that
lies: a blast's flash outlasts the tick it is lethal on, so making the whole flash lethal would leave
a trap whose edge **in time** the player cannot see. A slick has no such gap - it is dangerous for
exactly as long as it is on the screen, and then it is gone. Drawn and dangerous are the same
interval, which is what the blast rule was protecting.

*It spares the side that made it, which G0018 refused.* That refusal was about the **grenade**: a
throw that could not catch the thrower is a free button, and where he is standing is the whole cost of
using one. Nothing on the player's side fires venom, so there is no button to make free. What it buys
is that a firing line of frogs is a place rather than a mess - a spatter that killed frogs would have
them clear their own ground and read as a bug the first time one walked into another's.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A blast that lingers, instead of this.* The tidiest-looking answer, and it breaks the one
rule that makes running through the tail of an explosion safe. Two kinds of circle is cheaper than one
circle with a mode.

**No:** *Damage that ticks while you stand in it, or that falls off towards the edge.* Pillar 1. A
slick is decisive on contact for the same reason a blast is inside its circle: "am I in it" is a
question the screen answers, and "how long have I been in it" is arithmetic.

**No:** *A slick on nobody's side, to match the blast.* Considered and decided the other way, by
Mateusz, 2026-09-19. It is the one place the two mechanics differ and the difference is deliberate;
if it plays badly the fix is one field on the spawn, not a new rule.

**No:** *A `Slick` that a system asks about by kind - who it catches, what it does.* It is a faction
and a `ContactDamage` and nothing else, which is why no system in the game had to learn that slicks
exist. Anything that would need `if (slick)` belongs on the definition instead.

**No:** *One big circle instead of a spatter.* It reads as a second blast, and a single circle is
walked round. Several overlapping ones read as ground that has been ruined, which is the thing the
player is meant to be looking at.

**No:** *A slick that puts down anything already lying in it for ever.* The window a circle grants its
victim is what keeps the blow a casualty rather than an outright kill; without it the next circle of
the same spatter finishes him on the tick he falls. A man left lying in venom is still finished
eventually, and that is the mechanic rather than a gap in it.

**Revisit when** something on the player's side wants to ruin ground - a burning barrel, an incendiary
gun. That is the first thing this shape cannot express without the side rule being argued again, and
the answer then is a neutral slick declared per gun rather than a second mechanic.

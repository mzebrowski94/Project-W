# G0034: A slick is ground, not an explosion

**Status.** amended by G0035: fire is a slick on nobody's side; amended by G0053: a slick wounds a man rather than putting him down

**Trigger.** Adding or changing anything that makes a piece of the field dangerous to stand on - a
lingering hazard, a gas, a fire, a mine - or proposing that a blast linger, spare a side, or fall
off with distance. G0018 answers the same question for explosions.

**Enforced by.** `SlickTest.leavesTheSideThatMadeItAlone`,
`.staysOnTheGroundLongAfterTheShotThatMadeIt`, `.cleansTheGroundOnceItHasHadItsTime`,
`.woundsAManStandingInItRatherThanPuttingHimDown`, `.ruinsTheGroundWhereItsShotBurst`,
`.leavesNothingInsideRock`.

**Constraint.** A slick is one circle of ruined ground: an ordinary entity with a `Position`, a `Body`,
a `Collider` and a `ContactDamage`, non-solid and immovable, carrying the **faction of whatever fired
the shot**. It catches whatever walks into it for as long as it lasts, and the circle that hurts is the
entity's own body, which is the circle the renderer draws. A burst leaves several overlapping ones - a
spatter - whose shape is a number on the gun.

It arrives as everything that leaves something behind arrives: the shot carries `SlicksOnDeath`, and one
system lays the spatter where the shot died (G0018's death reaction). A shot that burst on a wall, on a
man or at the end of its flight all get there the same way.

**Scope.** Anything that ruins ground. Not how big or long-lived a spatter is - the gun names its own -
nor what one looks like beyond the body being the drawing. A blast is untouched: still one circle, on
nobody's side, dangerous for exactly one tick (G0018).

**Why a second kind of thing rather than a longer blast.** G0018's revisit trigger says the first thing
its shape cannot express - an explosion that is not fatal - gets a second kind rather than a curve on
the first. Three of G0018's rejections look as if they apply, and each is answered by the thing being
ground:

- *It is in the collision path.* G0018's refusal was arithmetic: a twelve-unit blast would have tripled
  the grid's cell size. A slick is about two units across, so it costs the grid nothing and borrows the
  contact, faction and casualty rules without new logic. `SimConfig.largestRadius` measures it through
  the gun table, so a slick widened past a brute is caught by the cell size.
- *It hurts for as long as it is drawn.* The blast rule protects against a flash that outlasts its
  lethal tick; a slick is dangerous exactly as long as it is on the screen.
- *It spares the side that made it.* G0018's refusal is about the grenade being a free button. Nothing
  on the player's side fires venom, and a firing line of frogs stays a place rather than a mess.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A blast that lingers, instead of this.* It breaks the rule that makes running through the tail
of an explosion safe. Two kinds of circle is cheaper than one circle with a mode.

**No:** *Damage that ticks while you stand in it, or falls off towards the edge.* Pillar 1: "am I in it"
is a question the screen answers; "how long have I been in it" is arithmetic.

**Reversed by G0035:** *A slick on nobody's side, to match the blast.* A slick's side is now declared
per kind - venom's is whoever fired it, fire's is nobody's.

**No:** *A `Slick` that a system asks about by kind.* It is a faction and a `ContactDamage` and nothing
else; anything that would need `if (slick)` belongs on the definition.

**No:** *One big circle instead of a spatter.* It reads as a second blast and is walked round; several
overlapping ones read as ruined ground.

**No:** *A slick that puts down anything already lying in it for ever.* The window a circle grants its
victim is what keeps the next circle of the same spatter from finishing him on the tick he falls.

**Revisit when** G0035's trigger fires: its own - something on the player's side wanting to ruin
ground - was answered there.

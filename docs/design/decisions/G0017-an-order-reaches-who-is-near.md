# G0017: An order reaches the troopers near the commander

**Constraint.** A squad order is issued from where the commander is standing and reaches the troopers
within a radius of him at that moment. There is no selection step, no roster, and no way to order the
whole squad at once. A trooper that takes an order keeps it until it is cancelled, replaced by
another, or spent - and orders outrank the level's objectives but never an ongoing fight, which is
carried out first.

Four verbs, and each is a mode a trooper is in rather than a place it is going: follow me, move out,
stop, cancel. None of them names a target, which is what keeps the commander's own position the thing
that decides who hears him.

**Scope.** The squad command model - who an order reaches and what an order is. It does not decide
the **input**: keys `1`-`4` are alpha-phase scaffolding, named as such, and the intended binding is a
button plus a radial menu. It does not decide what happens to a trooper that is out of earshot, which
is simply nothing.

**Rejected, and likely to be re-proposed.**

*Select, then order - drag a box over troopers, click where they should go.* The RTS answer, and the
one everybody reaches for. Rejected against two pillars at once. Pillar 2 says the commander is a unit
and not a cursor: selection needs a cursor with authority over the field, and he does not have one -
his mouse is his aim, and G0002 fixed that aim as a direction rather than a point on the ground.
Pillar 1 says a mechanic that makes the player stop and compute belongs in a different game, and
selection is exactly that pause.

*Orders reaching the whole squad wherever it is.* Simpler, and it never leaves a trooper behind.
Rejected because it makes the commander's position irrelevant to command, which is the one thing that
makes commanding interesting: when the squad is together - which pillar 4 says is most of the time -
the radius costs the player nothing, and when it is strung out, "get close enough to be heard" is a
real decision made with the same input as everything else, movement.

*A squad roster along the edge of the screen, ordering troopers individually.* Rejected as off-screen
management, which pillar 4 rules out, and as a menu the player carries around, which is what pillar 3
is against even though nothing is being bought.

*Formations as a fifth order.* Not rejected - deferred. "Follow me" already implies a loose formation
and that is enough to judge whether tighter ones are wanted. A formation order is worth having only
once there is a reason to prefer one shape over another, which needs level geometry the game does not
have yet.

**Revisit when** the radial menu lands and the input can carry a **target** as well as a verb.
Pointing at a place - "take that point", "hold that corner" - is a different model from a verb every
nearby trooper interprets for itself, and it is the one thing this record would be wrong about. It is
also the point at which the radius may stop being the right filter, because a targeted order implies
somebody chosen to carry it.

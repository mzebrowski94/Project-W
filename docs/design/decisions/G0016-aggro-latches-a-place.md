# G0016: Aggro latches a place, not a unit

**Constraint.** A unit that is provoked - shot, struck, or standing near a control point being taken -
latches the **position** the provocation came from and goes there. It does not latch what provoked it.
No entity reference and no entity id survives a tick. What it fights when it arrives is whatever
ordinary perception finds for it once it is there.

**Scope.** Provocation: being hurt, and the aggro a capture generates. It does not touch noticing the
commander, which is a range test on a def (G0011, G0015) and is latched as a boolean exactly as
before. The two live side by side: a unit walking to a remembered place still notices him on the way
and he still outranks everything.

**The trade, recorded because it is deliberate.** "An enemy focuses its first attacker and only moves
to the next once that one is dead" becomes **emergent rather than guaranteed**. A squad firing from
one position reads identically - the latched place is where they all are. A squad spread wide will
sometimes see an enemy pull off the trooper that shot it toward one standing nearer the latched point.
That was accepted knowingly, on the grounds that the guaranteed version costs a registry and the
difference is mostly invisible from inside the game.

**Rejected, and likely to be re-proposed.**

*Latching the attacker by a stable id, with an id-to-entity registry rebuilt each tick.* The literal
reading of the requirement, and the first thing anybody will propose when they see an enemy switch
targets. Rejected because it buys a behaviour the player can rarely distinguish at the cost of a
second index over the world, kept correct every tick, whose only purpose is to re-find something the
simulation deliberately does not hold on to. If the switching turns out to read as indecision in play,
this is the fix - but it should be bought after seeing the problem, not before.

*Holding the `Entity` itself on the component.* Entities and components are pooled: a reference kept
across ticks can outlive what it pointed at and come back aimed at whatever was built from the
recycled instance. This is the concrete hazard G0010 was written about, and it fails silently and
rarely, which is the worst way for it to fail.

*Re-resolving "who hurt me most recently" every tick and calling that the target.* No stored state at
all, so it looks like the cleanest answer. Rejected because it is not a latch: the enemy would swing
toward whoever fired last, so two troopers alternating shots would hold it in place between them
rather than pulling it onto one of them.

*A decaying threat value per attacker.* The MMO answer. It is a table per unit, it needs identity to
key on, and it makes what a unit will do next a number the player cannot see - which pillar 5 refuses
and which G0011 already rejected line of sight for.

**Revisit when** the difference becomes visible in play - a squad spread wide enough that enemies
switching targets mid-approach reads as indecision rather than as chaos - or when a unit's identity is
that it holds a grudge, which is a strain with its own rule rather than a change to this one.

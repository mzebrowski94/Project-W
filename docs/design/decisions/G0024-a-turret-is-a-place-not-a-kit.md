# G0024: A turret is a place you get on, not a gun you pick up

**Status.** accepted

**Trigger.** Adding anything a unit gets inside, sits on or is carried by, or proposing that manning
a turret work like picking a weapon up.

**Enforced by.** `TurretTest.putsHimOnItWhenHePresses`, `.takesHimOffWhenHePressesAgain`,
`.drivesNobodyWhoIsOnATurret`, `.catchesTheCrewInTheBlastWhenItGoesUp`,
`.willNotTakeHoldOfATurretSomebodyIsOnIt`.

**Constraint.** A turret stays on the field and a man joins it. Getting on and off is the action key
(G0021), so it costs no binding and the player learns nothing new. While he is on it: the gun owns
where he is, nothing else drives him (`Drivers.held`), he is out of the pairwise path entirely, and
the gun is the only thing anything can hit. The gun fires where *it* is pointing rather than where he
is pointing, and comes round slower while the trigger is down.

**Scope.** Anything a unit is aboard. `Crew` is carried by the commander and by every trooper from
spawn, because a trooper will decide to use one off its own judgement and must arrive at exactly the
same state he does - the argument `Aid` makes for one revive path. It says nothing about vehicles,
which do not exist: there is one seat, and a thing that moved with somebody in it would need the
solver to know about passengers.

**A blast still reaches him.** Being out of the pairwise path means contact, gunfire and claws cannot
touch him; a blast is a radius test over everything that can be hurt, so it can. That is deliberate
rather than an oversight: the gun going up under him is what destroying a manned turret is *for*, and
it falls out of the existing death reaction with no rule of its own (G0018).

**A station is named by an issued id** (G0010, G0016), as a drag names its load. Ids come from a
counter that never goes back, so a man whose gun was destroyed under him holds a number nothing will
carry again, and `TurretSystem` puts him back on his feet rather than finding him a new one. Which
station is crewed is *derived* from the riders every tick rather than latched on both halves, because
two latched halves of one fact is how they come to disagree.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A turret as a weapon pickup - walk over it and carry it.* The obvious cheap version, and it
already has a table and a system. Rejected because it deletes the whole mechanic: what a turret costs
is standing still in one place, and a gun you carry away costs nothing. It would also be a gun that
is strictly better than a sidearm with no downside, which pillar 3 has no room for.

**No:** *A `Vehicle` or `Mount` component with seats, and passengers moved by the thing carrying them.*
Rejected as a second user that does not exist. One seat and a station that never moves is the whole of
what is needed, and the solver has no notion of a body carrying another.

**No:** *Keeping the crew solid and hittable beside the gun.* Two overlapping circles on one tile, with
the player unable to tell which one a shot is about to hit. Considered and turned down in favour of
"the gun is the only thing anything can hit", which is one bar, one target and one decision.

**No:** *A `crewed` flag latched on the station.* It is derived from the riders instead. A latched flag
survives its crew being killed, blown off or reaped, and leaves a gun nobody can take because the
simulation still believes somebody is on it.

**No:** *A second binding for getting on.* The input map has nothing free, and G0021 exists precisely so
that a new thing to do near you is a new `ActionKind` rather than a new key.

**Revisit when** something exists that both moves and carries a unit. That needs the solver to know
what a passenger is, and none of the above answers it.
